package com.freeturn.app.domain

sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    data class Connected(val ip: String) : SshConnectionState()
    data class Error(val message: String, val hostKeyChanged: Boolean = false) : SshConnectionState()
}

enum class ServerOperation { APPLY, UPDATE, UPLOAD_BUILD, STOP }

sealed class ServerState {
    object Unknown : ServerState()
    object Checking : ServerState()
    data class Known(
        /** Стоит FreeTurn этим установщиком (есть install.conf); старая установка = false. */
        val installed: Boolean,
        val running: Boolean,
        /** Режим живого сервера ("udp" | "tcp"); null - сервер не запущен. */
        val mode: String? = null,
        val obfProfile: String? = null,
        val version: String? = null
    ) : ServerState()
    data class Working(val operation: ServerOperation) : ServerState()
    data class Error(val message: String) : ServerState()
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val version: String) : UpdateState()
    object NoUpdate : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    object ReadyToInstall : UpdateState()
    data class Error(val reason: UpdateError) : UpdateState()
}

enum class UpdateError { RELEASE_UNAVAILABLE, NO_APK, NETWORK, DOWNLOAD_FAILED, SIGNATURE_MISMATCH, FILE_MISSING }
