package com.freeturn.app.viewmodel.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.backup.BackupCrypto
import com.freeturn.app.domain.backup.BackupManager
import com.freeturn.app.domain.proxy.ProxyServiceLauncher
import com.freeturn.app.domain.proxy.ProxyStore
import com.freeturn.app.domain.ssh.SshRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class RestoreFailReason { BAD_PASSWORD, BAD_FILE, IO }

sealed interface BackupEvent {
    data object ExportSuccess : BackupEvent
    data object ExportFailed : BackupEvent
    data class RestoreSuccess(val count: Int) : BackupEvent
    data class RestoreFailed(val reason: RestoreFailReason) : BackupEvent
}

/** Профиль целиком: экспорт/восстановление зашифрованного бэкапа и полный сброс. */
class BackupViewModel(
    private val prefs: AppPreferences,
    private val proxyLauncher: ProxyServiceLauncher,
    private val sshRepository: SshRepository,
    private val backupManager: BackupManager,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            val event = try {
                val bytes = backupManager.export(password)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IOException("no output stream")
                }
                BackupEvent.ExportSuccess
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackupEvent.ExportFailed
            }
            _events.send(event)
        }
    }

    fun restoreBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            val event = try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("no input stream")
                }

                val data = backupManager.decode(bytes, password)
                proxyLauncher.stop()
                val count = backupManager.restore(data)
                sshRepository.resetAll()
                ProxyStore.clearLogs()
                BackupEvent.RestoreSuccess(count)
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupCrypto.BadPasswordException) {
                BackupEvent.RestoreFailed(RestoreFailReason.BAD_PASSWORD)
            } catch (_: BackupCrypto.FormatException) {
                BackupEvent.RestoreFailed(RestoreFailReason.BAD_FILE)
            } catch (_: Exception) {
                BackupEvent.RestoreFailed(RestoreFailReason.IO)
            }
            _events.send(event)
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            proxyLauncher.stop()
            prefs.resetAll()
            sshRepository.resetAll()
            ProxyStore.clearLogs()

            val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                appContext.startActivity(intent)
            }
        }
    }
}
