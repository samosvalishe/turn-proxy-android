@file:Suppress("DEPRECATION")

package com.freeturn.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import androidx.core.content.edit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

data class SshConfig(
    val ip: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val authType: String = "PASSWORD",
    val sshKey: String = "",
    val hostFingerprint: String = ""
)

data class ClientConfig(
    val serverAddress: String = "",
    val vkLinks: List<String> = emptyList(),
    val threads: Int = 4,
    val allocsPerStream: Int = 1,
    val useUdp: Boolean = true,
    val manualCaptcha: Boolean = false,
    val localPort: String = "127.0.0.1:9000",
    val isRawMode: Boolean = false,
    val rawCommand: String = "",
    val vlessMode: Boolean = false,
    // "auto" | "udp" | "doh" — соответствует флагу -dns ядра.
    val dnsMode: String = DnsMode.AUTO,
    // Если true — в argv ядра добавляется -port 443, переопределяя порт TURN-сервера VK.
    val forcePort443: Boolean = false,
    // Если true — добавляется флаг -debug для расширенного вывода в логах.
    val debugMode: Boolean = false
)

/**
 * Парсит сохранённые звонковые ссылки. Сначала пробует новый ключ
 * (newline-joined), потом legacy (одиночная ссылка). Тримит, дропает пустые.
 */
internal fun decodeVkLinks(joined: String?, legacy: String?): List<String> {
    val fromNew = joined
        ?.split('\n', '\r')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
    if (fromNew.isNotEmpty()) return fromNew
    val one = legacy?.trim().orEmpty()
    return if (one.isNotEmpty()) listOf(one) else emptyList()
}

object DnsMode {
    const val AUTO = "auto"
    const val UDP = "udp"
    const val DOH = "doh"
    val ALL = listOf(AUTO, UDP, DOH)
}

class AppPreferences(context: Context) {
    private val context = context.applicationContext

    companion object {
        val SSH_IP = stringPreferencesKey("ssh_ip")
        val SSH_PORT = intPreferencesKey("ssh_port")
        val SSH_USER = stringPreferencesKey("ssh_user")
        val SSH_AUTH_TYPE = stringPreferencesKey("ssh_auth_type")
        val SSH_HOST_FP = stringPreferencesKey("ssh_host_fp")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CLIENT_SERVER_ADDR = stringPreferencesKey("client_server_addr")
        // Legacy single-link key. Читается только для миграции, перезаписывается
        // первой ссылкой из списка для обратной совместимости со старыми билдами.
        val CLIENT_VK_LINK = stringPreferencesKey("client_vk_link")
        // Список звонковых ссылок, разделитель — \n. URL не содержит перевод строки,
        // поэтому простой join безопасен и не требует JSON-эскейпа.
        val CLIENT_VK_LINKS = stringPreferencesKey("client_vk_links")
        val CLIENT_THREADS = intPreferencesKey("client_threads")
        val CLIENT_ALLOCS_PER_STREAM = intPreferencesKey("client_allocs_per_stream")
        val CLIENT_UDP = booleanPreferencesKey("client_udp")
        val CLIENT_MANUAL_CAPTCHA = booleanPreferencesKey("client_manual_captcha")
        val CLIENT_LOCAL_PORT = stringPreferencesKey("client_local_port")
        val CLIENT_IS_RAW = booleanPreferencesKey("client_is_raw")
        val CLIENT_RAW_CMD = stringPreferencesKey("client_raw_cmd")
        val CLIENT_VLESS = booleanPreferencesKey("client_vless")
        val CLIENT_DNS_MODE = stringPreferencesKey("client_dns_mode")
        val CLIENT_FORCE_PORT_443 = booleanPreferencesKey("client_turn_port_443")
        val CLIENT_DEBUG = booleanPreferencesKey("client_debug")

        // Устаревший ключ — читается только для миграции (true → "doh").
        // Ключ "client_force_port_443" не переиспользуется под новую семантику, чтобы
        // у старых пользователей не включился неожиданно -port 443.
        private val CLIENT_FORCE_PORT_443_LEGACY = booleanPreferencesKey("client_force_port_443")
        val PROXY_LISTEN = stringPreferencesKey("proxy_listen")
        val PROXY_CONNECT = stringPreferencesKey("proxy_connect")
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
        val TG_SUBSCRIBE_SHOWN = booleanPreferencesKey("tg_subscribe_shown")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

        // Устаревшие ключи — используются только для миграции
        private val SSH_PASS_LEGACY = stringPreferencesKey("ssh_pass")
        private val SSH_KEY_LEGACY = stringPreferencesKey("ssh_key")
    }

    // Шифрованное хранилище для SSH-пароля и ключа (Android Keystore + AES-256)
    // Подавляем предупреждения: стабильной замены EncryptedSharedPreferences пока нет
    @Suppress("DEPRECATION")
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_ssh_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val sshConfigFlow: Flow<SshConfig> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            SshConfig(
                ip = prefs[SSH_IP] ?: "",
                port = prefs[SSH_PORT] ?: 22,
                username = prefs[SSH_USER] ?: "root",
                // Читаем из зашифрованного хранилища; если пусто — берём из DataStore (миграция)
                password = encryptedPrefs.getString("ssh_pass", null)
                    ?: prefs[SSH_PASS_LEGACY] ?: "",
                authType = prefs[SSH_AUTH_TYPE] ?: "PASSWORD",
                sshKey = encryptedPrefs.getString("ssh_key", null)
                    ?: prefs[SSH_KEY_LEGACY] ?: "",
                hostFingerprint = prefs[SSH_HOST_FP] ?: ""
            )
        }

    val clientConfigFlow: Flow<ClientConfig> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            ClientConfig(
                serverAddress = prefs[CLIENT_SERVER_ADDR] ?: "",
                vkLinks = decodeVkLinks(prefs[CLIENT_VK_LINKS], prefs[CLIENT_VK_LINK]),
                threads = prefs[CLIENT_THREADS] ?: 4,
                allocsPerStream = prefs[CLIENT_ALLOCS_PER_STREAM] ?: 1,
                useUdp = prefs[CLIENT_UDP] ?: true,
                manualCaptcha = prefs[CLIENT_MANUAL_CAPTCHA] ?: false,
                localPort = prefs[CLIENT_LOCAL_PORT] ?: "127.0.0.1:9000",
                isRawMode = prefs[CLIENT_IS_RAW] ?: false,
                rawCommand = prefs[CLIENT_RAW_CMD] ?: "",
                vlessMode = prefs[CLIENT_VLESS] ?: false,
                dnsMode = prefs[CLIENT_DNS_MODE]
                    ?: if (prefs[CLIENT_FORCE_PORT_443_LEGACY] == true) DnsMode.DOH else DnsMode.AUTO,
                forcePort443 = prefs[CLIENT_FORCE_PORT_443] ?: false,
                debugMode = prefs[CLIENT_DEBUG] ?: false
            )
        }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[ONBOARDING_DONE] ?: false }

    val proxyListenFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[PROXY_LISTEN] ?: "0.0.0.0:56000" }

    val proxyConnectFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[PROXY_CONNECT] ?: "127.0.0.1:40537" }

    val dynamicThemeFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[DYNAMIC_THEME] ?: true }

    val tgSubscribeShownFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[TG_SUBSCRIBE_SHOWN] ?: false }

    val profilesFlow: Flow<List<Profile>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> ProfileJson.decodeList(prefs[PROFILES_JSON]) }

    val activeProfileIdFlow: Flow<String?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[ACTIVE_PROFILE_ID]?.takeIf { it.isNotBlank() } }

    /** Заменяет весь список профилей. */
    suspend fun saveProfiles(list: List<Profile>) {
        context.dataStore.edit { prefs ->
            prefs[PROFILES_JSON] = ProfileJson.encodeList(list)
        }
    }

    suspend fun setActiveProfileId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_PROFILE_ID)
            else prefs[ACTIVE_PROFILE_ID] = id
        }
    }

    suspend fun saveSshConfig(config: SshConfig) {
        // Чувствительные данные — в зашифрованное хранилище
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit {
                putString("ssh_pass", config.password)
                    .putString("ssh_key", config.sshKey)
            }
        }
        // Остальное — в DataStore; удаляем устаревшие незашифрованные значения
        context.dataStore.edit { prefs ->
            prefs[SSH_IP] = config.ip
            prefs[SSH_PORT] = config.port
            prefs[SSH_USER] = config.username
            prefs[SSH_AUTH_TYPE] = config.authType
            prefs[SSH_HOST_FP] = config.hostFingerprint
            prefs.remove(SSH_PASS_LEGACY)
            prefs.remove(SSH_KEY_LEGACY)
        }
    }

    suspend fun saveSshFingerprint(fingerprint: String) {
        context.dataStore.edit { prefs -> prefs[SSH_HOST_FP] = fingerprint }
    }

    suspend fun saveClientConfig(config: ClientConfig) {
        context.dataStore.edit { prefs ->
            prefs[CLIENT_SERVER_ADDR] = config.serverAddress
            val cleaned = config.vkLinks.map { it.trim() }.filter { it.isNotEmpty() }
            prefs[CLIENT_VK_LINKS] = cleaned.joinToString("\n")
            // Дублируем первую ссылку в legacy-ключ — чтобы откат на старый бинарь
            // не сломал базовый сценарий с одной ссылкой.
            prefs[CLIENT_VK_LINK] = cleaned.firstOrNull() ?: ""
            prefs[CLIENT_THREADS] = config.threads
            prefs[CLIENT_ALLOCS_PER_STREAM] = config.allocsPerStream
            prefs[CLIENT_UDP] = config.useUdp
            prefs[CLIENT_MANUAL_CAPTCHA] = config.manualCaptcha
            // Мигрируем старый ключ: noDtls удалён из приложения.
            prefs.remove(booleanPreferencesKey("client_no_dtls"))
            prefs[CLIENT_LOCAL_PORT] = config.localPort
            prefs[CLIENT_IS_RAW] = config.isRawMode
            prefs[CLIENT_RAW_CMD] = config.rawCommand
            prefs[CLIENT_VLESS] = config.vlessMode
            prefs[CLIENT_DNS_MODE] = if (config.dnsMode in DnsMode.ALL) config.dnsMode else DnsMode.AUTO
            prefs[CLIENT_FORCE_PORT_443] = config.forcePort443
            prefs[CLIENT_DEBUG] = config.debugMode
            // Зачищаем устаревший ключ после успешной миграции, чтобы он не всплывал
            // при следующей загрузке, если пользователь переключит режим обратно.
            prefs.remove(CLIENT_FORCE_PORT_443_LEGACY)
        }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_DONE] = done }
    }

    suspend fun setDynamicTheme(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DYNAMIC_THEME] = enabled }
    }

    suspend fun setTgSubscribeShown() {
        context.dataStore.edit { prefs -> prefs[TG_SUBSCRIBE_SHOWN] = true }
    }

    suspend fun saveProxyConfig(listen: String, connect: String) {
        context.dataStore.edit { prefs ->
            prefs[PROXY_LISTEN] = listen
            prefs[PROXY_CONNECT] = connect
        }
    }

    /**
     * Снимок: список + id активного. Объединено в один Flow, чтобы UI получал
     * консистентную пару (нет окна, в котором активный id указывает на удалённый).
     */
    val profilesSnapshot: Flow<ProfilesSnapshot> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            ProfilesSnapshot(
                list = ProfileJson.decodeList(prefs[PROFILES_JSON]),
                activeId = prefs[ACTIVE_PROFILE_ID]?.takeIf { it.isNotBlank() }
            )
        }

    /** Полный сброс: DataStore + EncryptedSharedPreferences + кастомный бинарник */
    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit { clear() }
            File(context.filesDir, "custom_vkturn").delete()
        }
    }
}
