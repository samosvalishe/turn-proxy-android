package com.freeturn.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.freeturn.app.data.backup.BackupData
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.ClientId
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.server.ServerJson
import com.freeturn.app.data.server.ServerOpts
import com.freeturn.app.data.server.ServersSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

// Битый файл переживаем с дефолтами: чтение/запись на пути старта туннеля
// (ownClientId, оркестратор) не должны ронять процесс.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class AppPreferences(context: Context) {
    private val context = context.applicationContext

    companion object {
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
        val NERD_MODE = booleanPreferencesKey("nerd_mode")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val TG_SUBSCRIBE_SHOWN = booleanPreferencesKey("tg_subscribe_shown")
        val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")
        val RESTART_SERVER_ON_SWITCH = booleanPreferencesKey("restart_server_on_switch")
        val SERVERS_JSON = stringPreferencesKey("servers_json")
        val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
        val OWN_CLIENT_ID = stringPreferencesKey("own_client_id")
    }

    /** DataStore-флоу: IOException (битый файл) -> дефолты, остальное пробрасываем. */
    private fun <T> prefFlow(transform: (Preferences) -> T): Flow<T> =
        context.dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map(transform)

    /** Снимок серверов (источник истины конфигурации). Один Flow для консистентности. */
    val serversSnapshot: Flow<ServersSnapshot> = prefFlow { prefs ->
        ServersSnapshot(
            list = ServerJson.decodeList(prefs[SERVERS_JSON]),
            activeId = prefs[ACTIVE_SERVER_ID]?.takeIf { it.isNotBlank() },
            loaded = true
        )
    }

    // --- Конфиг активного сервера ---
    // Производные от serversSnapshot: их читает рантайм (ProxyService, оркестратор,
    // SSH). Без активного сервера отдают дефолты - запускать в этом случае нечего.

    private val activeServerFlow: Flow<Server?> =
        serversSnapshot.map { it.active }.distinctUntilChanged()

    val sshConfigFlow: Flow<SshConfig> =
        activeServerFlow.map { it?.ssh ?: SshConfig() }.distinctUntilChanged()

    val clientConfigFlow: Flow<ClientConfig> =
        activeServerFlow.map { it?.client ?: ClientConfig() }.distinctUntilChanged()

    val proxyListenFlow: Flow<String> =
        activeServerFlow.map { it?.proxyListen ?: "0.0.0.0:56000" }.distinctUntilChanged()

    val proxyConnectFlow: Flow<String> =
        activeServerFlow.map { it?.proxyConnect ?: "127.0.0.1:40537" }.distinctUntilChanged()

    val serverOptsFlow: Flow<ServerOpts> =
        activeServerFlow.map { it?.opts ?: ServerOpts() }.distinctUntilChanged()

    val dynamicThemeFlow: Flow<Boolean> = prefFlow { prefs -> prefs[DYNAMIC_THEME] ?: true }

    /** "Режим отладки" - открывает отладочные секции (журнал, verbose, экран логов). */
    val nerdModeFlow: Flow<Boolean> = prefFlow { prefs -> prefs[NERD_MODE] ?: true }

    /** Приватный режим - маскирует чувствительные поля в UI. */
    val privacyModeFlow: Flow<Boolean> = prefFlow { prefs -> prefs[PRIVACY_MODE] ?: false }

    /** При смене активного профиля перезапускать его удалённый сервер под параметры профиля. */
    val restartServerOnSwitchFlow: Flow<Boolean> =
        prefFlow { prefs -> prefs[RESTART_SERVER_ON_SWITCH] ?: false }

    val tgSubscribeShownFlow: Flow<Boolean> = prefFlow { prefs -> prefs[TG_SUBSCRIBE_SHOWN] ?: false }

    // Один раз за установку: на MIUI/HyperOS isIgnoringBatteryOptimizations остаётся false
    // даже после "не ограничивать" - без флага диалог всплывал бы каждый запуск.
    val batteryPromptShownFlow: Flow<Boolean> = prefFlow { prefs -> prefs[BATTERY_PROMPT_SHOWN] ?: false }

    // --- CRUD серверов ---
    // Каждая операция - одна транзакция dataStore.edit: атомарный read-modify-write,
    // параллельные записи не теряются и не оставляют активный id без сервера.

    /** Атомарно правит сервер по id. true - сервер найден и изменился. */
    suspend fun updateServer(id: String, transform: (Server) -> Server): Boolean {
        var changed = false
        context.dataStore.edit { prefs ->
            val list = ServerJson.decodeList(prefs[SERVERS_JSON])
            val updated = list.map { if (it.id == id) transform(it) else it }
            changed = updated != list
            if (changed) prefs[SERVERS_JSON] = ServerJson.encodeList(updated)
        }
        return changed
    }

    /** Атомарно правит активный сервер. true - активный есть и изменился. */
    suspend fun updateActiveServer(transform: (Server) -> Server): Boolean {
        var changed = false
        context.dataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_SERVER_ID]?.takeIf { it.isNotBlank() } ?: return@edit
            val list = ServerJson.decodeList(prefs[SERVERS_JSON])
            val updated = list.map { if (it.id == activeId) transform(it) else it }
            changed = updated != list
            if (changed) prefs[SERVERS_JSON] = ServerJson.encodeList(updated)
        }
        return changed
    }

    /** Добавляет сервер, уникализируя имя. [activate] делает его активным. */
    suspend fun addServer(server: Server, activate: Boolean = false): String {
        context.dataStore.edit { prefs ->
            val list = ServerJson.decodeList(prefs[SERVERS_JSON])
            val base = server.name.trim().ifBlank { Server.FALLBACK_NAME }
            val named = server.copy(name = uniqueServerName(base, list))
            prefs[SERVERS_JSON] = ServerJson.encodeList(list + named)
            if (activate || list.isEmpty()) prefs[ACTIVE_SERVER_ID] = named.id
        }
        return server.id
    }

    /** Клонирует сервер по id: копия с новым id и уникальным именем. Возвращает id копии. */
    suspend fun cloneServer(id: String): String? {
        var newId: String? = null
        context.dataStore.edit { prefs ->
            val list = ServerJson.decodeList(prefs[SERVERS_JSON])
            val source = list.firstOrNull { it.id == id } ?: return@edit
            val copy = source.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueServerName(source.name, list)
            )
            prefs[SERVERS_JSON] = ServerJson.encodeList(list + copy)
            newId = copy.id
        }
        return newId
    }

    /** Переименовывает сервер. Пустое имя оставляет текущее; занятое получает " (2)". */
    suspend fun renameServer(id: String, name: String) {
        context.dataStore.edit { prefs ->
            val list = ServerJson.decodeList(prefs[SERVERS_JSON])
            val target = list.firstOrNull { it.id == id } ?: return@edit
            val unique = uniqueServerName(name.trim().ifBlank { target.name }, list, excludingId = id)
            if (unique == target.name) return@edit
            prefs[SERVERS_JSON] =
                ServerJson.encodeList(list.map { if (it.id == id) it.copy(name = unique) else it })
        }
    }

    /** Удаляет сервер; если он был активным - активным становится первый из оставшихся. */
    suspend fun deleteServer(id: String) {
        context.dataStore.edit { prefs ->
            val list = ServerJson.decodeList(prefs[SERVERS_JSON])
            val remaining = list.filterNot { it.id == id }
            if (remaining.size == list.size) return@edit
            prefs[SERVERS_JSON] = ServerJson.encodeList(remaining)
            if (prefs[ACTIVE_SERVER_ID] == id) {
                val next = remaining.firstOrNull()
                if (next == null) prefs.remove(ACTIVE_SERVER_ID)
                else prefs[ACTIVE_SERVER_ID] = next.id
            }
        }
    }

    suspend fun setActiveServerId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_SERVER_ID)
            else prefs[ACTIVE_SERVER_ID] = id
        }
    }

    private fun uniqueServerName(base: String, existing: List<Server>, excludingId: String? = null): String {
        val taken = existing
            .filter { it.id != excludingId }
            .map { it.name.trim().lowercase() }
            .toSet()
        if (base.lowercase() !in taken) return base
        var i = 2
        while ("$base ($i)".lowercase() in taken) i++
        return "$base ($i)"
    }

    /** SSH-конфиг активного сервера. */
    suspend fun saveSshConfig(config: SshConfig) {
        updateActiveServer { it.copy(ssh = config) }
    }

    suspend fun saveSshFingerprint(fingerprint: String) {
        updateActiveServer { it.copy(ssh = it.ssh.copy(hostFingerprint = fingerprint)) }
    }

    suspend fun saveSshRootMode(mode: String) {
        updateActiveServer { it.copy(ssh = it.ssh.copy(rootMode = mode)) }
    }

    suspend fun setDynamicTheme(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DYNAMIC_THEME] = enabled }
    }

    suspend fun setNerdMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[NERD_MODE] = enabled }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PRIVACY_MODE] = enabled }
    }

    suspend fun setRestartServerOnSwitch(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[RESTART_SERVER_ON_SWITCH] = enabled }
    }

    suspend fun setTgSubscribeShown() {
        context.dataStore.edit { prefs -> prefs[TG_SUBSCRIBE_SHOWN] = true }
    }

    suspend fun setBatteryPromptShown() {
        context.dataStore.edit { prefs -> prefs[BATTERY_PROMPT_SHOWN] = true }
    }

    /** Постоянный Client ID владельца. Генерируется один раз. */
    suspend fun ownClientId(): String {
        // Write-транзакция нужна один раз за жизнь установки.
        val cur = prefFlow { it[OWN_CLIENT_ID] }.first()
        if (cur != null && ClientId.isValid(cur)) return cur
        var id = ""
        context.dataStore.edit { prefs ->
            val existing = prefs[OWN_CLIENT_ID]
            id = if (existing != null && ClientId.isValid(existing)) existing
            else ClientId.generate().also { prefs[OWN_CLIENT_ID] = it }
        }
        return id
    }

    /** Полный сброс настроек. */
    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }

    /** Снимок для бэкапа: все серверы, активный и интерфейсные тоггл-настройки. */
    suspend fun exportData(): BackupData {
        val snap = serversSnapshot.first()
        return BackupData(
            servers = snap.list,
            activeId = snap.activeId,
            ownClientId = ownClientId(),
            dynamicTheme = dynamicThemeFlow.first(),
            nerdMode = nerdModeFlow.first(),
            privacyMode = privacyModeFlow.first(),
            restartServerOnSwitch = restartServerOnSwitchFlow.first()
        )
    }

    /**
     * Применяет бэкап: добавляет серверы к существующим (не заменяет - новые id и
     * уникализация имён). Тогглы, личность (own client-id) и активный сервер восстанавливает
     * только на чистый профиль (не было серверов): иначе импорт "подмешать серверы" в живой
     * профиль молча менял бы тему/приватность/cid. Всё одной транзакцией. Возвращает число
     * добавленных серверов.
     */
    suspend fun importBackup(data: BackupData): Int {
        var added = 0
        context.dataStore.edit { prefs ->
            val list = ServerJson.decodeList(prefs[SERVERS_JSON]).toMutableList()
            val freshProfile = list.isEmpty()
            val idMap = HashMap<String, String>()
            data.servers.forEach { incoming ->
                val newId = UUID.randomUUID().toString()
                idMap[incoming.id] = newId
                val base = incoming.name.trim().ifBlank { Server.FALLBACK_NAME }
                list += incoming.copy(id = newId, name = uniqueServerName(base, list))
                added++
            }
            prefs[SERVERS_JSON] = ServerJson.encodeList(list)
            val restoredActive = data.activeId?.let { idMap[it] }
            when {
                freshProfile && restoredActive != null -> prefs[ACTIVE_SERVER_ID] = restoredActive
                prefs[ACTIVE_SERVER_ID].isNullOrBlank() && list.isNotEmpty() ->
                    prefs[ACTIVE_SERVER_ID] = list.first().id
            }
            if (freshProfile) {
                data.ownClientId?.takeIf { ClientId.isValid(it) }?.let { prefs[OWN_CLIENT_ID] = it }
                prefs[DYNAMIC_THEME] = data.dynamicTheme
                prefs[NERD_MODE] = data.nerdMode
                prefs[PRIVACY_MODE] = data.privacyMode
                prefs[RESTART_SERVER_ON_SWITCH] = data.restartServerOnSwitch
            }
        }
        return added
    }
}
