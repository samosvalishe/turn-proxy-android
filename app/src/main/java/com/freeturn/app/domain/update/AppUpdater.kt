package com.freeturn.app.domain.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import com.freeturn.app.domain.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdater(private val context: Context) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Volatile private var latestApkUrl: String? = null

    private val apkFile: File
        get() = File(context.cacheDir, "update.apk")

    private fun getCurrentVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (_: PackageManager.NameNotFoundException) {
        "0.0.0"
    }

    /**
     * @param silent true - игнорировать ошибки сети (автопроверка). false - показывать ошибки (ручная).
     */
    suspend fun checkForUpdate(silent: Boolean = false) {
        _state.value = UpdateState.Checking
        try {
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
            if (release == null) {
                _state.value = if (silent) UpdateState.Idle
                else UpdateState.Error("Не удалось получить информацию о релизе")
                return
            }

            val remoteVersion = release.getString("tag_name").removePrefix("v")

            if (isNewer(remoteVersion, getCurrentVersion())) {
                latestApkUrl = findApkUrl(release)
                if (latestApkUrl != null) {
                    _state.value = UpdateState.Available(remoteVersion)
                } else {
                    _state.value = if (silent) UpdateState.Idle
                    else UpdateState.Error("APK не найден в релизе")
                }
            } else {
                _state.value = UpdateState.NoUpdate
            }
        } catch (e: CancellationException) {
            // Штатная отмена корутины.
            throw e
        } catch (_: Exception) {
            _state.value = if (silent) UpdateState.Idle
            else UpdateState.Error("Нет соединения с сервером")
        }
    }

    suspend fun downloadUpdate() {
        val url = latestApkUrl ?: run {
            _state.value = UpdateState.Error("URL обновления не найден")
            return
        }

        _state.value = UpdateState.Downloading(0)
        try {
            withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                try {
                    connection.connect()
                    // Защита от записи 404/редирект-страниц в APK.
                    if (connection.responseCode !in 200..299) {
                        throw java.io.IOException("HTTP ${connection.responseCode}")
                    }

                    val totalSize = connection.contentLength.toLong()
                    var downloaded = 0L

                    connection.inputStream.use { input ->
                        apkFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                if (totalSize > 0) {
                                    _state.value = UpdateState.Downloading(
                                        (downloaded * 100 / totalSize).toInt()
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            // Чужая подпись = подмена (MITM на CDN-редиректе/компрометация релиза).
            val trusted = withContext(Dispatchers.IO) { isSignedBySameCert(apkFile) }
            if (!trusted) {
                apkFile.delete()
                _state.value = UpdateState.Error("Подпись обновления не совпадает - установка отменена")
                return
            }
            _state.value = UpdateState.ReadyToInstall
        } catch (e: CancellationException) {
            apkFile.delete()
            throw e
        } catch (e: Exception) {
            apkFile.delete()
            _state.value = UpdateState.Error("Ошибка загрузки: ${e.message}")
        }
    }

    fun installUpdate() {
        if (!apkFile.exists()) {
            _state.value = UpdateState.Error("Файл обновления не найден")
            return
        }

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun resetState() {
        _state.value = UpdateState.Idle
    }

    // Private

    private fun fetchLatestRelease(): JSONObject? {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            if (connection.responseCode == 200) {
                JSONObject(connection.inputStream.bufferedReader().readText())
            } else null
        } finally {
            connection.disconnect()
        }
    }

    /** [apk] подписан тем же сертификатом и тем же packageName, что установленное приложение. */
    @Suppress("DEPRECATION")
    private fun isSignedBySameCert(apk: File): Boolean = try {
        val pm = context.packageManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flag)
        val installed = pm.getPackageInfo(context.packageName, flag)
        val downloadedSigs = signatureHashes(archive)
        val installedSigs = signatureHashes(installed)
        archive?.packageName == context.packageName &&
            downloadedSigs.isNotEmpty() &&
            downloadedSigs == installedSigs
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun signatureHashes(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            info.signatures
        }
        return sigs.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun findApkUrl(release: JSONObject): String? {
        val assets = release.getJSONArray("assets")
        val apkUrlsByName = buildMap {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) put(name, asset.getString("browser_download_url"))
            }
        }
        for (abi in Build.SUPPORTED_ABIS) {
            apkUrlsByName.entries.firstOrNull { it.key.contains(abi) }?.let { return it.value }
        }
        return null
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/samosvalishe/turn-proxy-android/releases/latest"

        fun isNewer(remote: String, current: String): Boolean {
            val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val c = current.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, c.size)) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv != cv) return rv > cv
            }
            return false
        }
    }
}
