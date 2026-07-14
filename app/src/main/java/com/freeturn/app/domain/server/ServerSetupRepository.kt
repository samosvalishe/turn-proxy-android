package com.freeturn.app.domain.server

import android.content.Context
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.control.ProbeData
import com.freeturn.app.data.control.UninstallData
import com.freeturn.app.data.control.WgSetupData
import com.freeturn.app.data.control.decodeBase64
import com.freeturn.app.domain.ssh.SSHManager

class ServerSetupRepository(context: Context, private val ssh: SSHManager) {

    private val control = ServerControl(context, ssh)

    val lastSeenFingerprint: String? get() = ssh.lastSeenFingerprint

    suspend fun detectRootMode(cfg: SshConfig): String? = control.detectRootMode(cfg)

    data class ProbeResult(
        val wgPort: Int?
    )

    data class WgSetupResult(
        val port: Int,
        val clientConf: String?,
        val existed: Boolean
    )

    suspend fun probe(cfg: SshConfig): Result<ProbeResult> =
        control.run(cfg, ServerCommand.Probe)
            .requireData<ProbeData>()
            .map { ProbeResult(wgPort = it.wg.port) }

    suspend fun install(cfg: SshConfig): Result<Unit> =
        control.run(cfg, ServerCommand.Install).asUnit()

    suspend fun wgSetup(
        cfg: SshConfig,
        port: Int,
        endpoint: String
    ): Result<WgSetupResult> =
        control.run(cfg, ServerCommand.WgSetup(port, endpoint))
            .requireData<WgSetupData>()
            .map {
                WgSetupResult(
                    port = it.wg.port.takeIf { p -> p > 0 } ?: port,
                    clientConf = decodeBase64(it.clientConfB64),
                    existed = it.wg.existed
                )
            }

    suspend fun start(cfg: SshConfig, opts: ServerStartOptions): Result<Unit> =
        control.run(cfg, ServerCommand.Start(opts)).asUnit()

    /**
     * Снос free-turn-proxy с сервера: бинарь, unit, НАШ ft-wg0, clients, PREFIX.
     * Чужой WG/общий ip_forward не трогаем (скрипт сам решает по маркерам).
     * [withWgPkg] - снести и пакет wireguard-tools, но лишь если ставили его мы.
     */
    suspend fun uninstall(cfg: SshConfig, withWgPkg: Boolean = false): Result<UninstallData> =
        control.run(cfg, ServerCommand.Uninstall(withWgPkg = withWgPkg))
            .requireData<UninstallData>()
}
