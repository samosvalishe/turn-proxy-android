package com.freeturn.app.viewmodel

sealed interface ServerHubStatus {
    /**
     * Профиль не активен: живой SSH/ядро принадлежат активному. Предлагаем активировать.
     */
    data object Offline : ServerHubStatus
    /** Активен, но SSH не настроен — нечего подключать. */
    data object NotPaired : ServerHubStatus
    /** Идёт подключение/проверка (единая busy-фаза, skeleton). */
    data object Connecting : ServerHubStatus
    /** Идёт серверное действие (старт/стоп/установка). */
    data class Working(val action: String) : ServerHubStatus
    /** SSH установлен и состояние ядра известно. */
    data class Online(
        val running: Boolean,
        val installed: Boolean,
        val tcpMode: Boolean?,
        val obfProfile: String?,
        val version: String?,
        val sshIp: String
    ) : ServerHubStatus
    /** Ошибка SSH или серверной команды. Причина — внутренняя java/SSH-ошибка, юзеру
     *  бесполезна и в hero не показывается; полный текст остаётся в sshLog (NerdScreen). */
    data object Failed : ServerHubStatus
    /** Sync с сервером выключен — live-статус ядра нерелевантен, нейтральная заглушка. */
    data object SyncOff : ServerHubStatus
}

/**
 * Доступность «Настроек сервера»: при sync ON правки пушатся на сервер → нужен живой SSH;
 * при sync OFF настройки клиент-локальны → доступны и оффлайн. Единственное место с этим
 * правилом — его используют и вход в экран (ServerDetailScreen), и сам экран
 * (ServerManagementScreen), иначе условия разъезжаются.
 */
fun serverSettingsAvailable(connected: Boolean, syncOn: Boolean): Boolean = connected || !syncOn
