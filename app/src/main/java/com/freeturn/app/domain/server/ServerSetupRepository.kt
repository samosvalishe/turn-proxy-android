package com.freeturn.app.domain.server

import android.content.Context
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.control.ProbeData
import com.freeturn.app.data.control.UninstallData
import com.freeturn.app.data.control.WgSetupData
import com.freeturn.app.data.control.decodeBase64
import com.freeturn.app.domain.ssh.SSHManager

/**
 * SSH-операции мастера добавления self-hosted сервера. Собственный [SSHManager] и
 * [ServerControl]: мастер работает с черновиком конфига и не трогает живую сессию
 * активного сервера ([com.freeturn.app.domain.ssh.SshRepository]). Состояния не
 * держит - каждая операция самостоятельный вызов, ошибки приходят [Result].
 */
class ServerSetupRepository(context: Context, private val ssh: SSHManager) {

    private val control = ServerControl(context, ssh)

    /** Отпечаток хоста, увиденный последней командой (TOFU) - сохраняется в сервер. */
    val lastSeenFingerprint: String? get() = ssh.lastSeenFingerprint

    /** Preflight: определяет rootMode (ROOT/SUDO_NOPASS/SUDO_PASS). null - SSH-ошибка. */
    suspend fun detectRootMode(cfg: SshConfig): String? = control.detectRootMode(cfg)

    /** Снимок состояния хоста после probe. */
    data class ProbeResult(
        /** Порт активного/сконфигурированного НАШЕГО WireGuard; null - бэкенда нет. */
        val wgPort: Int?
    )

    /** Итог wg-setup: фактический порт бэкенда + клиентский .conf (если сервер вернул). */
    data class WgSetupResult(
        val port: Int,
        val clientConf: String?,
        /** true - наш ft-wg0 уже существовал, бутстрап не выполнялся. */
        val existed: Boolean
    )

    /** Проверка SSH-доступа + probe. Ошибка SSH/скрипта - failure с текстом. */
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
