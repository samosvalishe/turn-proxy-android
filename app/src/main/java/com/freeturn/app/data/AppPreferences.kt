

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

object DnsMode {
    const val AUTO = "auto"
    const val PLAIN = "plain"
    const val DOH = "doh"
    val ALL = listOf(AUTO, PLAIN, DOH)
}

/** Источник TURN-creds (флаг -provider ядра). Client-only. */
object Provider {
    const val VK = "vk"
    val ALL = listOf(VK)
}

/** Wire-профиль обфускации payload (флаг -obf-profile ядра). Должен совпадать с сервером. */
object ObfProfile {
    const val NONE = "none"
    const val RTPOPUS = "rtpopus"
    val ALL = listOf(NONE, RTPOPUS)

    private val KEY_REGEX = Regex("^[0-9a-fA-F]{64}$")

    /** Ключ -obf-key, который примет ядро (DecodeKey). Единая проверка для argv, UI и regen. */
    fun isValidKey(key: String): Boolean = key.matches(KEY_REGEX)
}

/**
 * Транспорт «туннельного приложения» поверх локального прокси. Пока единственный —
 * WireGuard (GoBackend): поднимает VPN-туннель, чей Endpoint указывает на localPort
 * прокси, так что трафик устройства идёт через TURN-релей.
 */
object TunnelTransport {
    const val NONE = "none"
    const val WIREGUARD = "wireguard"
    const val DEFAULT_TUNNEL_NAME = "freeturn-wg"
    val PERSISTED = listOf(NONE, WIREGUARD)
}

/** Режим split-tunneling для WireGuard-интерфейса. */
object SplitTunnelMode {
    /** Весь трафик в туннель (только сам прокси исключён). */
    const val ALL = "all"
    /** Только перечисленные приложения идут в туннель (IncludedApplications). */
    const val INCLUDE = "include"
    /** Перечисленные приложения исключены из туннеля (ExcludedApplications). */
    const val EXCLUDE = "exclude"
    val VALUES = listOf(ALL, INCLUDE, EXCLUDE)
}

data class ClientConfig(
    val serverAddress: String = "",
    val vkLink: String = "",
    /** Источник TURN-creds (-provider). Пока только "vk". */
    val provider: String = Provider.VK,
    val threads: Int = 4,
    /** Соответствует флагу `-streams-per-cred` ядра. Дефолт ядра = 10. */
    val streamsPerCred: Int = 10,
    /** TURN-транспорт UDP (-transport udp). false = TCP/TLS (дефолт ядра). */
    val useUdp: Boolean = true,
    val manualCaptcha: Boolean = false,
    val localPort: String = "127.0.0.1:9000",
    val isRawMode: Boolean = false,
    val rawCommand: String = "",
    /** Режим туннеля TCP-форвард (-mode tcp, Xray/sing-box). false = UDP-релей (WireGuard). */
    val tcpForward: Boolean = false,
    /** Bonding TCP по smux-сессиям (-bond). Только при tcpForward. Client-only в новом ядре. */
    val bond: Boolean = false,

    // Если true — добавляется флаг -debug для расширенного вывода в логах.
    val debugMode: Boolean = false,
    // Если true — в argv передаётся -dns-servers с DNS активной сети (оператор связи).
    val useCarrierDns: Boolean = false,
    // "auto" | "plain" | "doh" — соответствует флагу -dns-mode ядра.
    val dnsMode: String = DnsMode.AUTO,
    /**
     * Ручной список DNS-серверов (через запятую/пробел). Непустое значение имеет
     * приоритет над [useCarrierDns] и передаётся в флаг -dns-servers ядра.
     */
    val customDns: String = "",
    /**
     * Если true — изменения tcpForward/obfEnabled на клиенте дёргают рестарт
     * сервера (текущее поведение). Если false — флаги меняются только у клиента,
     * серверный процесс не трогается. (bond — client-only, сервер не трогает.)
     */
    val syncServerSwitches: Boolean = true,
    val magicSwitch: Boolean = false,
    /** Адрес для флага -turn ядра, если magicSwitch включён. Пусто = не передавать. */
    val magicTurn: String = "",
    /** Транспорт туннеля: NONE (proxy) либо WIREGUARD (VPN). По умолчанию — без туннеля. */
    val tunnelTransport: String = TunnelTransport.NONE,
    /** Конфиг WireGuard (.conf). Пусто = WG-туннель не поднимается. */
    val wireGuardConfig: String = "",
    /** Имя WG-туннеля для GoBackend. */
    val wireGuardTunnelName: String = TunnelTransport.DEFAULT_TUNNEL_NAME,
    /** Режим split-tunneling: all | include | exclude. */
    val splitTunnelMode: String = SplitTunnelMode.ALL,
    /** Список package-имён для include/exclude (разделители: запятая/пробел/перенос строки). */
    val splitTunnelApps: String = "",
    /** Сбор логов ядра в UI. false = ProxyServiceState.addLog глотает строки. */
    val logsEnabled: Boolean = true
) {
    /** WG реально активен только если выбран WG-транспорт и задан непустой конфиг. */
    val wireGuardActive: Boolean
        get() = tunnelTransport == TunnelTransport.WIREGUARD && wireGuardConfig.isNotBlank()
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
        val CLIENT_VK_LINK = stringPreferencesKey("client_vk_link")
        val CLIENT_PROVIDER = stringPreferencesKey("client_provider")
        val CLIENT_THREADS = intPreferencesKey("client_threads")
        val CLIENT_STREAMS_PER_CRED = intPreferencesKey("client_streams_per_cred")
        val CLIENT_UDP = booleanPreferencesKey("client_udp")
        val CLIENT_MANUAL_CAPTCHA = booleanPreferencesKey("client_manual_captcha")
        val CLIENT_LOCAL_PORT = stringPreferencesKey("client_local_port")
        val CLIENT_IS_RAW = booleanPreferencesKey("client_is_raw")
        val CLIENT_RAW_CMD = stringPreferencesKey("client_raw_cmd")
        val CLIENT_TCP_FORWARD = booleanPreferencesKey("client_tcp_forward")
        val CLIENT_BOND = booleanPreferencesKey("client_bond")
        val CLIENT_DEBUG = booleanPreferencesKey("client_debug")
        val CLIENT_USE_CARRIER_DNS = booleanPreferencesKey("client_use_carrier_dns")
        val CLIENT_DNS_MODE = stringPreferencesKey("client_dns_mode")
        val CLIENT_CUSTOM_DNS = stringPreferencesKey("client_custom_dns")
        val CLIENT_SYNC_SERVER = booleanPreferencesKey("client_sync_server")
        val CLIENT_MAGIC_SWITCH = booleanPreferencesKey("client_magic_switch")
        val CLIENT_MAGIC_TURN = stringPreferencesKey("client_magic_turn")
        val CLIENT_TUNNEL_TRANSPORT = stringPreferencesKey("client_tunnel_transport")
        val CLIENT_WG_CONFIG = stringPreferencesKey("client_wg_config")
        val CLIENT_WG_TUNNEL_NAME = stringPreferencesKey("client_wg_tunnel_name")
        val CLIENT_SPLIT_TUNNEL_MODE = stringPreferencesKey("client_split_tunnel_mode")
        val CLIENT_SPLIT_TUNNEL_APPS = stringPreferencesKey("client_split_tunnel_apps")
        val CLIENT_LOGS_ENABLED = booleanPreferencesKey("client_logs_enabled")
        val PROXY_LISTEN = stringPreferencesKey("proxy_listen")
        val PROXY_CONNECT = stringPreferencesKey("proxy_connect")
        val SERVER_OBF_PROFILE = stringPreferencesKey("server_obf_profile")
        val SERVER_OPTS_REV = intPreferencesKey("server_opts_rev")
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
        val NERD_MODE = booleanPreferencesKey("nerd_mode")
        val TG_SUBSCRIBE_SHOWN = booleanPreferencesKey("tg_subscribe_shown")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
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

    /** DataStore-флоу: IOException (битый файл) → дефолты, остальное пробрасываем. */
    private fun <T> prefFlow(transform: (Preferences) -> T): Flow<T> =
        context.dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map(transform)

    val sshConfigFlow: Flow<SshConfig> = prefFlow { prefs ->
            SshConfig(
                ip = prefs[SSH_IP] ?: "",
                port = prefs[SSH_PORT] ?: 22,
                username = prefs[SSH_USER] ?: "root",
                // Читаем из зашифрованного хранилища
                password = encryptedPrefs.getString("ssh_pass", null) ?: "",
                authType = prefs[SSH_AUTH_TYPE] ?: "PASSWORD",
                sshKey = encryptedPrefs.getString("ssh_key", null) ?: "",
                hostFingerprint = prefs[SSH_HOST_FP] ?: ""
            )
        }

    val clientConfigFlow: Flow<ClientConfig> = prefFlow { prefs ->
            ClientConfig(
                serverAddress = prefs[CLIENT_SERVER_ADDR] ?: "",
                vkLink = prefs[CLIENT_VK_LINK] ?: "",
                provider = (prefs[CLIENT_PROVIDER] ?: Provider.VK).let {
                    if (it in Provider.ALL) it else Provider.VK
                },
                threads = prefs[CLIENT_THREADS] ?: 4,
                streamsPerCred = prefs[CLIENT_STREAMS_PER_CRED] ?: 10,
                useUdp = prefs[CLIENT_UDP] ?: true,
                manualCaptcha = prefs[CLIENT_MANUAL_CAPTCHA] ?: false,
                localPort = prefs[CLIENT_LOCAL_PORT] ?: "127.0.0.1:9000",
                isRawMode = prefs[CLIENT_IS_RAW] ?: false,
                rawCommand = prefs[CLIENT_RAW_CMD] ?: "",
                tcpForward = prefs[CLIENT_TCP_FORWARD] ?: false,
                bond = prefs[CLIENT_BOND] ?: false,

                debugMode = prefs[CLIENT_DEBUG] ?: false,
                useCarrierDns = prefs[CLIENT_USE_CARRIER_DNS] ?: false,
                dnsMode = (prefs[CLIENT_DNS_MODE] ?: DnsMode.AUTO).let {
                    if (it in DnsMode.ALL) it else DnsMode.AUTO
                },
                customDns = prefs[CLIENT_CUSTOM_DNS] ?: "",
                syncServerSwitches = prefs[CLIENT_SYNC_SERVER] ?: true,
                magicSwitch = prefs[CLIENT_MAGIC_SWITCH] ?: false,
                magicTurn = prefs[CLIENT_MAGIC_TURN] ?: "",
                tunnelTransport = (prefs[CLIENT_TUNNEL_TRANSPORT] ?: TunnelTransport.NONE).let {
                    val t = if (it in TunnelTransport.PERSISTED) it else TunnelTransport.NONE
                    // Legacy: старый ClientSetup всегда писал WIREGUARD; пустой конфиг = туннель
                    // не выбран → читаем как NONE (proxy), чтобы режим не показывался как VPN.
                    if (t == TunnelTransport.WIREGUARD && prefs[CLIENT_WG_CONFIG].isNullOrBlank())
                        TunnelTransport.NONE else t
                },
                wireGuardConfig = prefs[CLIENT_WG_CONFIG] ?: "",
                wireGuardTunnelName = (prefs[CLIENT_WG_TUNNEL_NAME] ?: TunnelTransport.DEFAULT_TUNNEL_NAME)
                    .ifBlank { TunnelTransport.DEFAULT_TUNNEL_NAME },
                splitTunnelMode = (prefs[CLIENT_SPLIT_TUNNEL_MODE] ?: SplitTunnelMode.ALL).let {
                    if (it in SplitTunnelMode.VALUES) it else SplitTunnelMode.ALL
                },
                splitTunnelApps = prefs[CLIENT_SPLIT_TUNNEL_APPS] ?: "",
                logsEnabled = prefs[CLIENT_LOGS_ENABLED] ?: true
            )
        }

    val onboardingDoneFlow: Flow<Boolean> = prefFlow { prefs -> prefs[ONBOARDING_DONE] ?: false }

    val proxyListenFlow: Flow<String> = prefFlow { prefs -> prefs[PROXY_LISTEN] ?: "0.0.0.0:56000" }

    val proxyConnectFlow: Flow<String> = prefFlow { prefs -> prefs[PROXY_CONNECT] ?: "127.0.0.1:40537" }

    /** Снимок серверных obf-опций. obfKey читается из шифрованного хранилища. */
    data class ServerOpts(
        /** Wire-профиль обфускации: none | rtpopus (-obf-profile). */
        val obfProfile: String = ObfProfile.NONE,
        /** 64-hex obf-ключ (-obf-key). Должен совпадать на клиенте и сервере. */
        val obfKey: String = ""
    ) {
        /** Обфускация включена, когда выбран реальный профиль. */
        val obfEnabled: Boolean get() = obfProfile != ObfProfile.NONE
    }

    val serverOptsFlow: Flow<ServerOpts> = prefFlow { prefs ->
            ServerOpts(
                obfProfile = (prefs[SERVER_OBF_PROFILE] ?: ObfProfile.NONE).let {
                    if (it in ObfProfile.ALL) it else ObfProfile.NONE
                },
                obfKey = encryptedPrefs.getString("server_obf_key", null) ?: ""
            )
        }

    suspend fun saveServerOpts(opts: ServerOpts) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit { putString("server_obf_key", opts.obfKey) }
        }
        context.dataStore.edit { prefs ->
            prefs[SERVER_OBF_PROFILE] = opts.obfProfile
            prefs[SERVER_OPTS_REV] = (prefs[SERVER_OPTS_REV] ?: 0) + 1
        }
    }

    val dynamicThemeFlow: Flow<Boolean> = prefFlow { prefs -> prefs[DYNAMIC_THEME] ?: true }

    /** «Режим отладки» — открывает отладочные секции (журнал, verbose, экран логов). */
    val nerdModeFlow: Flow<Boolean> = prefFlow { prefs -> prefs[NERD_MODE] ?: false }

    val tgSubscribeShownFlow: Flow<Boolean> = prefFlow { prefs -> prefs[TG_SUBSCRIBE_SHOWN] ?: false }

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
        }
    }

    suspend fun saveSshFingerprint(fingerprint: String) {
        context.dataStore.edit { prefs -> prefs[SSH_HOST_FP] = fingerprint }
    }

    suspend fun saveClientConfig(config: ClientConfig) {
        context.dataStore.edit { prefs ->
            prefs[CLIENT_SERVER_ADDR] = config.serverAddress
            prefs[CLIENT_VK_LINK] = config.vkLink
            prefs[CLIENT_PROVIDER] = config.provider
            prefs[CLIENT_THREADS] = config.threads
            prefs[CLIENT_STREAMS_PER_CRED] = config.streamsPerCred
            prefs[CLIENT_UDP] = config.useUdp
            prefs[CLIENT_MANUAL_CAPTCHA] = config.manualCaptcha
            prefs[CLIENT_LOCAL_PORT] = config.localPort
            prefs[CLIENT_IS_RAW] = config.isRawMode
            prefs[CLIENT_RAW_CMD] = config.rawCommand
            prefs[CLIENT_TCP_FORWARD] = config.tcpForward
            prefs[CLIENT_BOND] = config.bond
            prefs[CLIENT_DEBUG] = config.debugMode
            prefs[CLIENT_USE_CARRIER_DNS] = config.useCarrierDns
            prefs[CLIENT_DNS_MODE] = if (config.dnsMode in DnsMode.ALL) config.dnsMode else DnsMode.AUTO
            prefs[CLIENT_CUSTOM_DNS] = config.customDns.trim()
            prefs[CLIENT_SYNC_SERVER] = config.syncServerSwitches
            prefs[CLIENT_MAGIC_SWITCH] = config.magicSwitch
            prefs[CLIENT_MAGIC_TURN] = config.magicTurn.trim()
            prefs[CLIENT_TUNNEL_TRANSPORT] =
                if (config.tunnelTransport in TunnelTransport.PERSISTED) config.tunnelTransport
                else TunnelTransport.NONE
            prefs[CLIENT_WG_CONFIG] = config.wireGuardConfig.trim()
            prefs[CLIENT_WG_TUNNEL_NAME] =
                config.wireGuardTunnelName.trim().ifBlank { TunnelTransport.DEFAULT_TUNNEL_NAME }
            prefs[CLIENT_SPLIT_TUNNEL_MODE] =
                if (config.splitTunnelMode in SplitTunnelMode.VALUES) config.splitTunnelMode
                else SplitTunnelMode.ALL
            prefs[CLIENT_SPLIT_TUNNEL_APPS] = config.splitTunnelApps.trim()
            prefs[CLIENT_LOGS_ENABLED] = config.logsEnabled
        }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_DONE] = done }
    }

    suspend fun setDynamicTheme(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DYNAMIC_THEME] = enabled }
    }

    suspend fun setNerdMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[NERD_MODE] = enabled }
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
    val profilesSnapshot: Flow<ProfilesSnapshot> = prefFlow { prefs ->
            ProfilesSnapshot(
                list = ProfileJson.decodeList(prefs[PROFILES_JSON]),
                activeId = prefs[ACTIVE_PROFILE_ID]?.takeIf { it.isNotBlank() },
                loaded = true
            )
        }

    /** Полный сброс: DataStore + EncryptedSharedPreferences. */
    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit { clear() }
        }
    }
}
