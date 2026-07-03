package com.freeturn.app.data.config

data class SshConfig(
    val ip: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val authType: String = AUTH_PASSWORD,
    val sshKey: String = "",
    val hostFingerprint: String = "",
    /** Способ эскалации до root (определяется preflight'ом). См. константы ниже. */
    val rootMode: String = ROOT,
    /** Пароль sudo для key-auth + SUDO_PASS. Пусто -> при password-auth берётся [password]. */
    val sudoPassword: String = ""
) {
    companion object {
        // Значения authType - контракт хранения (ServerJson).
        const val AUTH_PASSWORD = "PASSWORD"
        const val AUTH_SSH_KEY = "SSH_KEY"

        // rootMode - контракт хранения. Транспорт оборачивает команду:
        // ROOT -> bash -s; SUDO_NOPASS -> sudo -n bash -s; SUDO_PASS -> sudo -S -p '' bash -s.
        const val ROOT = "ROOT"
        const val SUDO_NOPASS = "SUDO_NOPASS"
        const val SUDO_PASS = "SUDO_PASS"
    }
}
