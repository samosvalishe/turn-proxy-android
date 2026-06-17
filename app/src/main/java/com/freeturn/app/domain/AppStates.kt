package com.freeturn.app.domain

/** Состояние SSH-подключения к активному серверу. */
sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    data class Connected(val ip: String) : SshConnectionState()
    data class Error(val message: String) : SshConnectionState()
}

/** Состояние удалённого сервера free-turn-proxy. */
sealed class ServerState {
    object Unknown : ServerState()
    object Checking : ServerState()
    data class Known(
        val installed: Boolean,
        val running: Boolean,
        /** true - сервер запущен с -mode tcp. null если не запущен/неизвестно. */
        val tcpMode: Boolean? = null,
        /** Профиль обфускации сервера (none|rtpopus|rtpopus2). null если не запущен/неизвестно. */
        val obfProfile: String? = null,
        val version: String? = null
    ) : ServerState()
    data class Working(val action: String) : ServerState()
    data class Error(val message: String) : ServerState()
}

/** Состояние локального прокси-клиента. */
sealed class ProxyState {
    object Idle : ProxyState()
    // Процесс поднимается, подтверждения соединения ещё нет.
    object Starting : ProxyState()
    // Процесс работает, но потоки/сессии ещё не подключены.
    data class Connecting(val active: Int, val total: Int) : ProxyState()
    // Хотя бы один поток/сессия подключены.
    data class Running(val active: Int, val total: Int) : ProxyState()
    data class Error(val message: String) : ProxyState()
    data class CaptchaRequired(val url: String, val sessionId: Long = 0L) : ProxyState()
}

/** Результат старта прокси-сервиса (мост ProxyService -> ProxyViewModel). */
sealed class StartupResult {
    data object Success : StartupResult()
    data class Failed(val message: String) : StartupResult()
}

/** Сессия ручной капчи. sessionId нужен для пересоздания WebView при одинаковом URL. */
data class CaptchaSession(val url: String, val sessionId: Long)

/**
 * Статистика подключений ядра.
 * [active] - живые каналы.
 * [total] - целевое число каналов (0 = неизвестно).
 */
data class ConnectionStats(val active: Int, val total: Int) {
    companion object {
        val IDLE = ConnectionStats(0, 0)
    }
}

/** Состояние проверки/загрузки обновления приложения. */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val version: String) : UpdateState()
    object NoUpdate : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    object ReadyToInstall : UpdateState()
    data class Error(val message: String) : UpdateState()
}
