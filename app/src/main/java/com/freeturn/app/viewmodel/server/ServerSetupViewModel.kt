package com.freeturn.app.viewmodel.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.R
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.HostPort
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.config.ProxyMode
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.config.TunnelTransport
import com.freeturn.app.data.control.RemoteConfig
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.server.ServerBackend
import com.freeturn.app.data.server.ServerMethod
import com.freeturn.app.data.server.ServerOpts
import com.freeturn.app.domain.proxy.ProxyOrchestrator
import com.freeturn.app.domain.server.ApplyOptions
import com.freeturn.app.domain.server.ApplyResult
import com.freeturn.app.domain.server.ServerSetupRepository
import com.freeturn.app.viewmodel.HapticEvent
import com.freeturn.app.viewmodel.Haptics
import com.freeturn.app.viewmodel.uiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class SetupStep { Ssh, Config, Install }

enum class SetupTaskKind { Apply, Persist }

data class SetupSshDraft(
    val ip: String = "",
    val port: String = "22",
    val username: String = "root",
    val password: String = "",
    val authType: String = SshConfig.AUTH_PASSWORD,
    val sshKey: String = "",
    val rootMode: String = SshConfig.ROOT,
    val sudoPassword: String = ""
) {
    val valid: Boolean
        get() = ip.isNotBlank() &&
            port.toIntOrNull()?.let { it in 1..65535 } == true &&
            when (authType) {
                SshConfig.AUTH_SSH_KEY -> sshKey.isNotBlank()
                else -> password.isNotBlank()
            }

    fun toSshConfig() = SshConfig(
        ip = ip.trim(),
        port = port.toIntOrNull() ?: 22,
        username = username.trim(),
        password = password,
        authType = authType,
        sshKey = sshKey,
        rootMode = rootMode,
        sudoPassword = sudoPassword
    )
}

data class SetupConfigDraft(
    val name: String = "",
    val backend: String = ServerBackend.NEW,
    val wgNet: String = ServerBackend.DEFAULT_WG_NET,
    val wgPort: String = ServerBackend.DEFAULT_WG_PORT.toString(),
    /** Адрес своего VPN (host:port) для backend=external. */
    val connect: String = "",
    /** Мой VPN слушает TCP (Xray/sing-box) - проброс в tcp-режиме. */
    val backendTcp: Boolean = false,
    val method: String = ServerMethod.DOCKER,
    val obfProfile: String = ObfProfile.RTPOPUS3,
    val listenPort: String = "",
    val callLink: String = ""
) {
    val ownWg: Boolean get() = backend == ServerBackend.NEW

    val proxyMode: String get() = if (!ownWg && backendTcp) ProxyMode.TCP else ProxyMode.UDP
}

data class SetupInstallState(
    val tasks: List<SetupTaskKind>,
    val current: Int = 0,
    val error: String? = null,
    val done: Boolean = false,
    val summary: SetupSummary? = null
)

data class SetupSummary(
    val serverName: String,
    val serverAddress: String,
    val ownWg: Boolean,
    val obfProfile: String,
    val wgConfImported: Boolean
)

data class SetupUiState(
    val step: SetupStep = SetupStep.Ssh,
    val ssh: SetupSshDraft = SetupSshDraft(),
    val checkingSsh: Boolean = false,
    val sshError: String? = null,
    /** На хосте уже есть установка: поля заполнены её конфигом. */
    val reinstall: Boolean = false,
    val duplicateHost: Boolean = false,
    val config: SetupConfigDraft = SetupConfigDraft(),
    val install: SetupInstallState? = null
) {
    /** Свой WG и FreeTurn на одном UDP-порту хоста не уживутся. */
    val portsClash: Boolean
        get() = config.ownWg && config.wgPort.isNotBlank() && config.wgPort == config.listenPort

    val configValid: Boolean
        get() {
            fun ok(p: String) = p.toIntOrNull()?.let { it in 1..65535 } == true
            if (!ok(config.listenPort) || portsClash) return false
            return if (config.ownWg) {
                ok(config.wgPort) && ServerBackend.isValidNet(config.wgNet)
            } else {
                HostPort.isValid(config.connect.trim())
            }
        }
}

// Сервер сохраняется только после успешного apply.
class ServerSetupViewModel(
    private val repo: ServerSetupRepository,
    private val prefs: AppPreferences,
    private val orchestrator: ProxyOrchestrator,
    private val haptics: Haptics,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(
        SetupUiState(config = SetupConfigDraft(listenPort = randomListenPort()))
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var fingerprint: String = ""
    private var serverName: String = ""
    private var installJob: Job? = null
    // Apply прошёл, а профиль не сохранился: повтор не должен гонять apply заново.
    private var applied: ApplyResult? = null

    fun setSsh(draft: SetupSshDraft) =
        _uiState.update {
            // Другой хост - его конфиг подтянет следующий probe.
            it.copy(ssh = draft, sshError = null, reinstall = it.reinstall && draft.ip == it.ssh.ip)
        }

    fun setConfig(draft: SetupConfigDraft) =
        _uiState.update { it.copy(config = draft) }

    fun rollListenPort() =
        _uiState.update { it.copy(config = it.config.copy(listenPort = randomListenPort())) }

    fun backToSsh() = _uiState.update { it.copy(step = SetupStep.Ssh) }

    fun backToConfig() {
        // Гонка с диалогом прерывания: установка успела завершиться - не сбрасываем.
        if (_uiState.value.install?.done == true) return
        installJob?.cancel()
        applied = null
        _uiState.update { it.copy(step = SetupStep.Config, install = null) }
    }

    fun submitSsh() {
        val s = _uiState.value
        if (s.checkingSsh || !s.ssh.valid) return
        _uiState.update { it.copy(checkingSsh = true, sshError = null) }
        viewModelScope.launch {
            // Preflight rootMode ДО probe: probe уже идёт под нужной эскалацией,
            // а определённый режим сохраняется в драфт (-> в сервер при persist).
            val baseCfg = s.ssh.toSshConfig()
            val mode = repo.detectRootMode(baseCfg) ?: SshConfig.ROOT
            _uiState.update { it.copy(ssh = it.ssh.copy(rootMode = mode)) }
            repo.probe(baseCfg.copy(rootMode = mode))
                .onSuccess { probe ->
                    fingerprint = repo.lastSeenFingerprint.orEmpty()
                    val ip = s.ssh.ip.trim()
                    val duplicate = prefs.serversSnapshot.first().list
                        .any { it.ssh.ip.isNotBlank() && it.ssh.ip.equals(ip, ignoreCase = true) }
                    haptics.perform(HapticEvent.SUCCESS)
                    _uiState.update { st ->
                        // Повторный probe не перетирает ручной ввод.
                        val prefill = probe.config.takeUnless { st.reinstall }
                        st.copy(
                            checkingSsh = false,
                            step = SetupStep.Config,
                            reinstall = st.reinstall || probe.config != null,
                            duplicateHost = duplicate,
                            config = prefill?.let { st.config.from(it) } ?: st.config
                        )
                    }
                }
                .onFailure { e ->
                    haptics.perform(HapticEvent.ERROR)
                    _uiState.update {
                        it.copy(checkingSsh = false, sshError = e.uiError(appContext))
                    }
                }
        }
    }

    fun submitConfig(fallbackName: String) {
        val s = _uiState.value
        if (s.step != SetupStep.Config || !s.configValid) return
        serverName = s.config.name.trim().ifBlank { fallbackName }
        applied = null
        _uiState.update {
            it.copy(
                step = SetupStep.Install,
                install = SetupInstallState(listOf(SetupTaskKind.Apply, SetupTaskKind.Persist))
            )
        }
        runInstall()
    }

    fun retryInstall() {
        val st = _uiState.value.install ?: return
        if (st.error == null) return
        _uiState.update { it.copy(install = st.copy(error = null)) }
        runInstall()
    }

    private fun advance() {
        _uiState.update { s ->
            s.copy(install = s.install?.let { it.copy(current = it.current + 1) })
        }
        val st = _uiState.value.install
        if (st != null && st.current < st.tasks.size) {
            haptics.perform(HapticEvent.STEP)
        }
    }

    private fun fail(message: String) {
        haptics.perform(HapticEvent.ERROR)
        _uiState.update { s -> s.copy(install = s.install?.copy(error = message)) }
    }

    private fun runInstall() {
        installJob?.cancel()
        installJob = viewModelScope.launch {
            val s = _uiState.value
            val cfg = s.ssh.toSshConfig().copy(hostFingerprint = fingerprint)
            val c = s.config

            val result = applied ?: repo.apply(cfg, c.toApplyOptions())
                .getOrElse { fail(it.uiError(appContext)); return@launch }
                .also {
                    applied = it
                    advance()
                }

            // NonCancellable: сервер уже настроен и запущен - уход с экрана не должен
            // оставить его без записи в приложении.
            val saved = withContext(NonCancellable) { persist(cfg, c, result) }
            if (!saved) {
                fail(appContext.getString(R.string.setup_persist_failed))
                return@launch
            }
            advance()

            haptics.perform(HapticEvent.SUCCESS)
            _uiState.update { st ->
                st.copy(
                    install = st.install?.copy(
                        done = true,
                        summary = SetupSummary(
                            serverName = serverName,
                            serverAddress = "${cfg.ip}:${c.listenPort}",
                            ownWg = c.ownWg,
                            obfProfile = c.obfProfile,
                            wgConfImported = result.ownerConf.isNotBlank()
                        )
                    )
                )
            }
        }
    }

    // Провайдер (relay или direct) не выбирается тут: сервер один, переключатель - в листе серверов.
    private suspend fun persist(cfg: SshConfig, c: SetupConfigDraft, r: ApplyResult): Boolean {
        prefs.addServer(buildServer(cfg, c, r), activate = true) ?: return false
        orchestrator.restartProxyIfRunning()
        return true
    }

    private fun buildServer(cfg: SshConfig, c: SetupConfigDraft, r: ApplyResult): Server = Server(
        name = serverName,
        ssh = cfg,
        client = ClientConfig(
            serverAddress = "${cfg.ip}:${c.listenPort}",
            callLink = c.callLink.trim(),
            tunnelTransport = if (r.ownerConf.isNotBlank()) TunnelTransport.WIREGUARD
                else TunnelTransport.NONE,
            wireGuardConfig = r.ownerConf,
            clientId = r.ownerClientId
        ),
        proxyListen = "0.0.0.0:${c.listenPort}",
        proxyConnect = if (c.ownWg) "127.0.0.1:${c.wgPort}" else c.connect.trim(),
        opts = ServerOpts(
            obfProfile = c.obfProfile,
            obfKey = r.obfKey,
            proxyMode = c.proxyMode,
            method = c.method,
            backend = c.backend,
            wgPort = c.wgPort.toIntOrNull() ?: ServerBackend.DEFAULT_WG_PORT,
            wgNet = c.wgNet.trim()
        )
    )

    private fun randomListenPort(): String = Random.nextInt(56000, 57000).toString()
}

// Ключ не шлём: на чистом хосте его сгенерирует сервер, на переустановке оставит свой -
// выданные гостям ссылки не протухнут.
private fun SetupConfigDraft.toApplyOptions() = ApplyOptions(
    method = method,
    backend = backend,
    listenPort = listenPort.toInt(),
    connect = connect.trim(),
    wgPort = wgPort.toIntOrNull() ?: ServerBackend.DEFAULT_WG_PORT,
    wgNet = wgNet.trim(),
    proxyMode = proxyMode,
    obfProfile = obfProfile
)

/** Черновик по конфигу прошлой установки: подсеть WG менять нельзя, остальное - как было. */
private fun SetupConfigDraft.from(r: RemoteConfig) = copy(
    backend = r.backend.takeIf { it in ServerBackend.VALUES } ?: backend,
    wgNet = r.wgNet.ifBlank { wgNet },
    wgPort = r.wgPort.takeIf { it > 0 }?.toString() ?: wgPort,
    connect = r.connect.ifBlank { connect },
    backendTcp = r.mode == ProxyMode.TCP,
    method = r.method.takeIf { it in ServerMethod.VALUES } ?: method,
    obfProfile = r.obfProfile.takeIf { it in ObfProfile.VALUES } ?: obfProfile,
    listenPort = r.listenPort.takeIf { it > 0 }?.toString() ?: listenPort
)
