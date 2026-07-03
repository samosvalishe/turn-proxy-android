package com.freeturn.app.domain.proxy

import kotlinx.coroutines.*
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Легковесный SOCKS5 прокси-сервер для раздачи Wi-Fi без root.
 * Принимает подключения на 0.0.0.0 и проксирует трафик через себя.
 * Поскольку он работает внутри приложения с включенным VpnService,
 * его исходящий трафик автоматически маршрутизируется в туннель.
 */
class Socks5Server(private val port: Int = 1080) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private val isRunning = AtomicBoolean(false)

    companion object {
        // SOCKS5 reply codes (RFC 1928)
        private const val REPLY_SUCCESS: Byte = 0x00
        private const val REPLY_GENERAL_FAILURE: Byte = 0x01
        private const val REPLY_CONNECTION_REFUSED: Byte = 0x05
        private const val REPLY_HOST_UNREACHABLE: Byte = 0x04
        private const val REPLY_NETWORK_UNREACHABLE: Byte = 0x03
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        newScope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                ProxyServiceState.addLog("SOCKS5 Hotspot Proxy запущен на 0.0.0.0:$port")
                while (isActive && isRunning.get()) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                if (e !is CancellationException && isRunning.get()) {
                    ProxyServiceState.addLog("SOCKS5 ошибка сервера: ${e.message}")
                }
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
        scope?.cancel()
        scope = null
        ProxyServiceState.addLog("SOCKS5 Hotspot Proxy остановлен")
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        var targetSocket: Socket? = null
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            // 1. Handshake
            val version = input.readByte()
            if (version != 5) return@withContext
            val numMethods = input.readByte()
            if (numMethods <= 0) return@withContext
            val methods = ByteArray(numMethods)
            input.readFully(methods)

            // Отвечаем: 0x05 (SOCKS5), 0x00 (No authentication)
            output.write(byteArrayOf(5, 0))
            output.flush()

            // 2. Request
            val reqVersion = input.readByte()
            val reqCmd = input.readByte()
            input.readByte() // RSV (reserved, игнорируем)
            val reqAtyp = input.readByte()

            if (reqVersion != 5 || reqCmd != 1) { // Only CONNECT is supported
                sendReply(output, REPLY_GENERAL_FAILURE)
                return@withContext
            }

            val host: String
            when (reqAtyp) {
                1 -> { // IPv4
                    val hostBytes = ByteArray(4)
                    input.readFully(hostBytes)
                    host = InetAddress.getByAddress(hostBytes).hostAddress ?: ""
                }
                3 -> { // Domain name
                    val len = input.readByte()
                    if (len <= 0) return@withContext
                    val hostBytes = ByteArray(len)
                    input.readFully(hostBytes)
                    host = String(hostBytes)
                }
                4 -> { // IPv6
                    val hostBytes = ByteArray(16)
                    input.readFully(hostBytes)
                    host = InetAddress.getByAddress(hostBytes).hostAddress ?: ""
                }
                else -> {
                    sendReply(output, REPLY_GENERAL_FAILURE)
                    return@withContext
                }
            }

            val portBytes = ByteArray(2)
            input.readFully(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            // 3. Устанавливаем соединение с целью.
            // Трафик этого сокета перехватит VpnService, так как мы внутри приложения.
            try {
                targetSocket = Socket(host, targetPort)
            } catch (e: java.net.ConnectException) {
                sendReply(output, REPLY_CONNECTION_REFUSED)
                return@withContext
            } catch (e: java.net.NoRouteToHostException) {
                sendReply(output, REPLY_HOST_UNREACHABLE)
                return@withContext
            } catch (e: java.net.UnknownHostException) {
                sendReply(output, REPLY_HOST_UNREACHABLE)
                return@withContext
            } catch (e: Exception) {
                sendReply(output, REPLY_GENERAL_FAILURE)
                return@withContext
            }

            sendReply(output, REPLY_SUCCESS)

            // 4. Двунаправленное копирование
            val clientToServer = launch { pipe(input, targetSocket.getOutputStream()) }
            val serverToClient = launch { pipe(targetSocket.getInputStream(), output) }

            clientToServer.join()
            serverToClient.join()
        } catch (_: EOFException) {
            // Клиент разорвал соединение посреди handshake — штатная ситуация
        } catch (_: Exception) {
            // Игнорируем сетевые ошибки отдельных клиентов
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
            try { targetSocket?.close() } catch (_: Exception) {}
        }
    }

    /** Отправляет SOCKS5 reply с указанным кодом результата. */
    private fun sendReply(output: OutputStream, replyCode: Byte) {
        // VER(1) REP(1) RSV(1) ATYP(1)=IPv4 BND.ADDR(4) BND.PORT(2)
        output.write(byteArrayOf(5, replyCode, 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    /**
     * Читает один байт, бросает [EOFException] при EOF.
     * Возвращает значение 0..255 как Int для удобства сравнений.
     */
    private fun InputStream.readByte(): Int {
        val b = this.read()
        if (b == -1) throw EOFException()
        return b
    }

    private fun InputStream.readFully(b: ByteArray) {
        var offset = 0
        while (offset < b.size) {
            val read = this.read(b, offset, b.size - offset)
            if (read == -1) throw EOFException()
            offset += read
        }
    }

    private suspend fun pipe(inputStream: InputStream, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(8192)
            while (isActive) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                outputStream.flush()
            }
        } catch (_: Exception) {
            // Соединение закрыто другой стороной — штатное завершение pipe
        }
    }
}
