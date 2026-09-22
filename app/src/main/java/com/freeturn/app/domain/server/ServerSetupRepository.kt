package com.freeturn.app.domain.server

import android.content.Context
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.control.ProbeData
import com.freeturn.app.domain.ssh.SSHManager

class ServerSetupRepository(context: Context, private val ssh: SSHManager) {

    private val control = ServerControl(context, ssh)

    val lastSeenFingerprint: String? get() = ssh.lastSeenFingerprint

    suspend fun detectRootMode(cfg: SshConfig): String? = control.detectRootMode(cfg)

    suspend fun probe(cfg: SshConfig): Result<ProbeData> =
        control.run(cfg, ServerCommand.Probe).requireData<ProbeData>()

    suspend fun apply(cfg: SshConfig, opts: ApplyOptions): Result<ApplyResult> =
        control.run(cfg, ServerCommand.Apply(opts)).applyResult()

    /** Снос FreeTurn, нашего ft-wg0 и каталога установки; чужой WG скрипт не трогает. */
    suspend fun uninstall(cfg: SshConfig): Result<Unit> =
        control.run(cfg, ServerCommand.Uninstall).asUnit()
}
