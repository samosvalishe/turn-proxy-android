package com.freeturn.app.domain.proxy

import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.server.Server
import com.freeturn.app.domain.ServerState
import com.freeturn.app.domain.server.applyOptions
import com.freeturn.app.domain.server.withApplied
import com.freeturn.app.domain.ssh.SshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ProxyOrchestrator(
    private val prefs: AppPreferences,
    private val launcher: ProxyServiceLauncher,
    private val sshRepository: SshRepository,
) {
    // Процессный scope: рестарт пары не должен рваться вместе с экраном (VM гибнет с
    // Activity) - между stop и start сервер остался бы погашенным.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // Два рестарта пары подряд не должны переплетать stop/start.
    private val pairMutex = Mutex()
    private var syncJob: Job? = null

    /**
     * Отложенный рестарт пары сервер+клиент после правки опций: правки летят пачками,
     * SSH-команда - одна. Новая правка переносит рестарт, но начатый не обрывает.
     */
    fun scheduleSync() {
        synchronized(this) {
            syncJob?.cancel()
            syncJob = scope.launch {
                delay(SYNC_DEBOUNCE_MS)
                withContext(NonCancellable) {
                    pairMutex.withLock {
                        if (!prefs.clientConfigFlow.first().syncServerSwitches) return@withLock
                        restartServerIfRunning()
                        restartProxyIfRunning()
                    }
                }
            }
        }
    }

    /** Рестарт пары сразу ("Применить"). [syncServer] false - сервер не трогаем. */
    fun restartPair(syncServer: Boolean) {
        scope.launch {
            pairMutex.withLock {
                if (syncServer) restartServerIfRunning()
                else sshRepository.logNote("рестарт сервера пропущен: синхронизация выключена")
                restartProxyIfRunning()
            }
        }
    }

    /** Переключение активного сервера: рантайм подтягивается к новому, не рвясь с экраном. */
    fun onActiveServerChanged(target: Server) {
        scope.launch {
            pairMutex.withLock {
                if (prefs.restartServerOnSwitchFlow.first()) restartServerIfRunning()
                sshRepository.activeSshConfig?.let { prev ->
                    if (prev.ip != target.ssh.ip || prev.port != target.ssh.port) {
                        sshRepository.disconnect()
                    }
                }
                restartProxyIfRunning()
            }
        }
    }

    suspend fun restartServerIfRunning() {
        val active = sshRepository.activeSshConfig
        if (active == null) {
            sshRepository.logNote("рестарт сервера пропущен: нет активной SSH-сессии")
            return
        }
        val server = prefs.serversSnapshot.first().active ?: return
        // Сессия должна вести на хост активного профиля: после смены профиля она ещё
        // может указывать на прошлый сервер - иначе рестартнём чужой хост.
        if (active.ip != server.ssh.ip || active.port != server.ssh.port) {
            sshRepository.logNote("рестарт сервера пропущен: SSH-сессия указывает на другой хост")
            return
        }
        val state = sshRepository.serverState.value
        val known = state as? ServerState.Known
        if (known?.running != true) {
            val reason = if (known != null) "сервер остановлен"
                else "состояние сервера ${state::class.simpleName}"
            sshRepository.logNote("рестарт сервера пропущен: $reason")
            return
        }
        val applied = sshRepository.applyServer(server.applyOptions()) ?: return
        saveApplied(server.withApplied(applied))
    }

    /**
     * Профили одного VPS (relay и direct из мастера) делят серверный конфиг: без синхронизации
     * второй остался бы со старым ключом/режимом и получил бы отлуп сервера.
     */
    suspend fun saveApplied(server: Server) {
        prefs.serversSnapshot.first().list
            .filter { it.id == server.id || it.ssh.sameHost(server.ssh) }
            .forEach { s ->
                prefs.updateServer(s.id) {
                    it.copy(
                        proxyListen = server.proxyListen,
                        proxyConnect = server.proxyConnect,
                        // obf-timing - пейсинг клиента, серверу его не шлём.
                        opts = server.opts.copy(obfTimingMs = it.opts.obfTimingMs),
                        client = it.client.copy(clientId = server.client.clientId)
                    )
                }
            }
    }

    fun restartProxyIfRunning() {
        launcher.restartIfRunning()
    }

    private companion object {
        const val SYNC_DEBOUNCE_MS = 600L
    }
}
