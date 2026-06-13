package com.freeturn.app.data.config

data class SshConfig(
    val ip: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val authType: String = AUTH_PASSWORD,
    val sshKey: String = "",
    val hostFingerprint: String = ""
) {
    companion object {
        // Значения authType - контракт хранения (ServerJson).
        const val AUTH_PASSWORD = "PASSWORD"
        const val AUTH_SSH_KEY = "SSH_KEY"
    }
}
