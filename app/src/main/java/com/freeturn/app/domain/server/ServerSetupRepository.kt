package com.freeturn.app.domain.server

import android.content.Context
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.domain.ssh.SSHManager

/**
 * SSH-операции мастера добавления self-hosted сервера. Собственный [SSHManager] и
 * [ServerControl]: мастер работает с черновиком конфига и не трогает живую сессию
 * активного сервера ([SshRepository]). Состояния не держит - каждая операция
 * самостоятельный вызов, ошибки приходят типизированным [Result].
 */
class ServerSetupRepository(context: Context, private val ssh: SSHManager) {

    private val control = ServerControl(context, ssh)

    /** Отпечаток хоста, увиденный последней командой (TOFU) - сохраняется в сервер. */
    val lastSeenFingerprint: String? get() = ssh.lastSeenFingerprint

    /** Снимок состояния хоста после probe. */
    data class ProbeResult(
        /** Порт активного/сконфигурированного WireGuard; null - WG-бэкенда нет. */
        val wgPort: Int?
    )

    /** Итог wg-setup: фактический порт бэкенда + клиентский .conf (если сервер вернул). */
    data class WgSetupResult(
        val port: Int,
        val clientConf: String?,
        /** true - найден существующий wg0.conf, бутстрап не выполнялся. */
        val existed: Boolean
    )

    /** Проверка SSH-доступа + probe. Ошибка SSH/скрипта - failure с текстом. */
    suspend fun probe(cfg: SshConfig): Result<ProbeResult> =
        when (val r = control.run(cfg, ServerCommand.Probe)) {
            is CmdResult.Err -> r.toFailure()
            is CmdResult.Ok -> Result.success(
                ProbeResult(wgPort = r.kv["WG_PORT"]?.toIntOrNull())
            )
        }

    suspend fun install(cfg: SshConfig): Result<Unit> =
        control.run(cfg, ServerCommand.Install).asUnit()

    suspend fun wgSetup(
        cfg: SshConfig,
        port: Int,
        endpoint: String,
        adopt: Boolean = false
    ): Result<WgSetupResult> =
        when (val r = control.run(cfg, ServerCommand.WgSetup(port, endpoint, adopt))) {
            is CmdResult.Err -> r.toFailure()
            is CmdResult.Ok -> Result.success(
                WgSetupResult(
                    port = r.kv["WG_PORT"]?.toIntOrNull() ?: port,
                    clientConf = r.kv["WG_CLIENT_CONF_B64"]?.let(::decodeBase64),
                    existed = r.kv["WG_EXISTS"] == "yes"
                )
            )
        }

    suspend fun start(cfg: SshConfig, opts: ServerOptions): Result<Unit> =
        control.run(cfg, ServerCommand.Start(opts)).asUnit()
}
