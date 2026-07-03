package com.freeturn.app.viewmodel.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.server.ServerOpts
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.config.TunnelTransport
import com.freeturn.app.domain.proxy.ProxyOrchestrator
import com.freeturn.app.domain.server.ServerSetupRepository
import com.freeturn.app.domain.server.ServerStartOptions
import com.freeturn.app.data.HapticUtil
import com.freeturn.app.viewmodel.uiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Шаги мастера добавления self-hosted сервера. */
enum class SetupStep { Ssh, Config, Install }

/** Задачи фазы установки; названия маппит UI (strings.xml). */
enum class SetupTaskKind { InstallCore, WireGuard, StartServer, Persist }

data class SetupSshDraft(
    val ip: String = "",
    val port: String = "22",
    val username: String = "root",
    val password: String = "",
    val authType: String = SshConfig.AUTH_PASSWORD,
    val sshKey: String = "",
    /** Определяется preflight'ом в submitSsh; ROOT до проверки. */
    val rootMode: String = SshConfig.ROOT,
    /** Пароль sudo для key-auth (password-auth переиспользует [password]). */
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
    /** Имя сервера; пустое - дефолт подставит submitConfig. */
    val name: String = "",
    /** true - VPN (WireGuard-бэкенд); false - proxy на свой бэкенд (Xray/sing-box/WG). */
    val vpnMode: Boolean = true,
    /** Wire-профиль обфускации ([ObfProfile]); по умолчанию rtpopus (включена). */
    val obfProfile: String = ObfProfile.RTPOPUS,
    /** Внешний UDP-порт turn-прокси (56000-56999 по умолчанию). */
    val listenPort: String = "",
    /** Порт WG-бэкенда для бутстрапа (когда WG на сервере не найден). */
    val wgPort: String = "",
    /** VPN: true - вставить готовый клиентский conf, WG на сервере не настраиваем. */
    val wgCustomConf: Boolean = false,
    /** VPN: текст клиентского .conf при [wgCustomConf]. */
    val wgConfText: String = "",
    /** Proxy-режим: порт существующего бэкенда на 127.0.0.1. */
    val backendPort: String = "",
    /** Proxy-режим: бэкенд по TCP (Xray/sing-box) вместо UDP-релея. */
    val backendTcp: Boolean = false,
    val vkLink: String = ""
)

data class SetupInstallState(
    val tasks: List<SetupTaskKind>,
    /** Индекс выполняемой задачи; == tasks.size - все выполнены. */
    val current: Int = 0,
    /** Ошибка задачи [current]; null - выполняется. */
    val error: String? = null,
    val done: Boolean = false,
    val summary: SetupSummary? = null
)

data class SetupSummary(
    /** Имя до уникализации хранилищем (занятое получит " (2)"). */
    val serverName: String,
    val serverAddress: String,
    val vpnMode: Boolean,
    /** Wire-профиль обфускации ([ObfProfile]); NONE - выключена. */
    val obfProfile: String,
    /** Клиентский WG-конфиг получен с сервера и сохранён в сервер. */
    val wgConfImported: Boolean,
    /** Использован существующий WireGuard - свой клиентский .conf импортируется вручную. */
    val usedExistingWg: Boolean
)

data class SetupUiState(
    val step: SetupStep = SetupStep.Ssh,
    val ssh: SetupSshDraft = SetupSshDraft(),
    val checkingSsh: Boolean = false,
    val sshError: String? = null,
    /** Порт найденного на сервере WireGuard (probe); null - не обнаружен. */
    val wgDetectedPort: Int? = null,
    /** Сервер с таким же IP уже есть в списке - мастер создаст дубликат. */
    val duplicateHost: Boolean = false,
    val config: SetupConfigDraft = SetupConfigDraft(),
    val install: SetupInstallState? = null
) {
    /**
     * Внешний порт совпал с UDP-портом бэкенда на том же хосте - оба бинда
     * конфликтуют. TCP-бэкенд (Xray/sing-box) с UDP-listen не пересекается.
     */
    val portsClash: Boolean
        get() {
            val listen = config.listenPort.toIntOrNull() ?: return false
            val backend = when {
                config.vpnMode && config.wgCustomConf -> config.backendPort.toIntOrNull()
                config.vpnMode && wgDetectedPort == null -> config.wgPort.toIntOrNull()
                config.vpnMode -> wgDetectedPort
                !config.backendTcp -> config.backendPort.toIntOrNull()
                else -> null
            }
            return backend != null && backend == listen
        }

    /** Гейт кнопки "Установить": порты и conf, нужные текущему режиму, валидны. */
    val configValid: Boolean
        get() {
            fun ok(p: String) = p.toIntOrNull()?.let { it in 1..65535 } == true
            if (!ok(config.listenPort) || portsClash) return false
            return when {
                config.vpnMode && config.wgCustomConf ->
                    config.wgConfText.isNotBlank() && ok(config.backendPort)
                config.vpnMode && wgDetectedPort == null -> ok(config.wgPort)
                !config.vpnMode -> ok(config.backendPort)
                else -> true
            }
        }
}

/**
 * Состояние мастера "Свой сервер": SSH-черновик -> опросник -> установка. Сервер
 * персистится только после успешного прохождения всех серверных шагов - выход из
 * мастера до финала не оставляет пустых записей в списке.
 */
class ServerSetupViewModel(
    private val repo: ServerSetupRepository,
    private val prefs: AppPreferences,
    private val orchestrator: ProxyOrchestrator,
    context: Context
) : ViewModel() {

    // applicationContext: ViewModel переживает Activity - иначе утечка.
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(
        SetupUiState(
            config = SetupConfigDraft(
                listenPort = randomListenPort(),
                wgPort = randomWgPort()
            )
        )
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    // Obf-ключ мастера: генерится один раз и уходит и в start, и в снимок сервера.
    private val obfKey: String = ObfProfile.generateKey()
    private var fingerprint: String = ""
    private var serverName: String = ""
    private var installJob: Job? = null

    fun setSsh(draft: SetupSshDraft) =
        _uiState.update { it.copy(ssh = draft, sshError = null) }

    fun setConfig(draft: SetupConfigDraft) =
        _uiState.update { it.copy(config = draft) }

    fun rollListenPort() =
        _uiState.update { it.copy(config = it.config.copy(listenPort = randomListenPort())) }

    fun rollWgPort() =
        _uiState.update { it.copy(config = it.config.copy(wgPort = randomWgPort())) }

    fun backToSsh() = _uiState.update { it.copy(step = SetupStep.Ssh) }

    /** Возврат к опроснику: прерывает идущую установку либо закрывает проваленную. */
    fun backToConfig() {
        // Гонка с диалогом прерывания: установка успела завершиться - не сбрасываем.
        if (_uiState.value.install?.done == true) return
        installJob?.cancel()
        _uiState.update { it.copy(step = SetupStep.Config, install = null) }
    }

    /** Шаг 1 -> 2: проверка SSH-доступа одной probe-командой + WG-детект. */
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
                    HapticUtil.perform(appContext, HapticUtil.Pattern.SUCCESS)
                    _uiState.update {
                        it.copy(
                            checkingSsh = false,
                            step = SetupStep.Config,
                            wgDetectedPort = probe.wgPort,
                            duplicateHost = duplicate,
                            // Префилл только пустого поля: ручной ввод пользователя
                            // повторная проверка SSH не перетирает.
                            config = it.config.copy(
                                backendPort = it.config.backendPort.ifBlank {
                                    (probe.wgPort ?: DEFAULT_WG_PORT).toString()
                                }
                            )
                        )
                    }
                }
                .onFailure { e ->
                    HapticUtil.perform(appContext, HapticUtil.Pattern.ERROR)
                    _uiState.update {
                        it.copy(checkingSsh = false, sshError = e.uiError(appContext))
                    }
                }
        }
    }

    /** Шаг 2 -> 3: фиксируем план задач и запускаем установку. [fallbackName] - для пустого имени. */
    fun submitConfig(fallbackName: String) {
        val s = _uiState.value
        if (s.step != SetupStep.Config || !s.configValid) return
        serverName = s.config.name.trim().ifBlank { fallbackName }
        val tasks = buildList {
            add(SetupTaskKind.InstallCore)
            // Бутстрап нового WG либо отдельный пир в существующем; свой conf -
            // сервер не трогаем, шага нет.
            if (s.config.vpnMode && !s.config.wgCustomConf) add(SetupTaskKind.WireGuard)
            add(SetupTaskKind.StartServer)
            add(SetupTaskKind.Persist)
        }
        _uiState.update { it.copy(step = SetupStep.Install, install = SetupInstallState(tasks)) }
        runInstall()
    }

    /** Повтор всей установки. Серверные шаги идемпотентны (install cached, wg-setup не перетирает). */
    fun retryInstall() {
        val st = _uiState.value.install ?: return
        if (st.error == null) return
        _uiState.update { it.copy(install = st.copy(current = 0, error = null)) }
        runInstall()
    }

    private fun advance() {
        _uiState.update { s ->
            s.copy(install = s.install?.let { it.copy(current = it.current + 1) })
        }
        // Тик на выполненную задачу; финальную закрывает SUCCESS - не дублируем.
        val st = _uiState.value.install
        if (st != null && st.current < st.tasks.size) {
            HapticUtil.perform(appContext, HapticUtil.Pattern.SELECTION)
        }
    }

    private fun fail(message: String?) {
        HapticUtil.perform(appContext, HapticUtil.Pattern.ERROR)
        _uiState.update { s ->
            s.copy(install = s.install?.copy(error = message ?: "unknown error"))
        }
    }

    private fun runInstall() {
        installJob?.cancel()
        installJob = viewModelScope.launch {
            val s = _uiState.value
            val cfg = s.ssh.toSshConfig().copy(hostFingerprint = fingerprint)
            val c = s.config

            repo.install(cfg).onFailure { fail(it.message); return@launch }
            advance()

            var wgClientConf: String? = null
            var backendPort = when {
                c.vpnMode && !c.wgCustomConf ->
                    s.wgDetectedPort ?: c.wgPort.toIntOrNull() ?: DEFAULT_WG_PORT
                else -> c.backendPort.toIntOrNull() ?: DEFAULT_WG_PORT
            }
            if (c.vpnMode && c.wgCustomConf) {
                // Готовый клиентский conf - серверный WG не трогаем.
                wgClientConf = c.wgConfText.trim()
            } else if (c.vpnMode) {
                // Endpoint клиентского конфига = локальный прокси устройства; рантайм
                // туннеля всё равно подменяет его при подъёме. wg-setup идемпотентно
                // поднимает НАШ ft-wg0 (создаёт или докручивает) и возвращает порт+conf.
                val wg = repo.wgSetup(
                    cfg,
                    port = backendPort,
                    endpoint = ClientConfig.DEFAULT_LOCAL_PORT
                ).getOrElse { fail(it.message); return@launch }
                wgClientConf = wg.clientConf
                backendPort = wg.port
                advance()
            }

            val obfOn = c.obfProfile != ObfProfile.NONE
            repo.start(
                cfg,
                ServerStartOptions(
                    listen = "0.0.0.0:${c.listenPort}",
                    connect = "127.0.0.1:$backendPort",
                    tcpMode = !c.vpnMode && c.backendTcp,
                    obfProfile = c.obfProfile,
                    obfKey = if (obfOn) obfKey else "",
                    // Авторизация по allowlist с первого запуска: владелец сидится
                    // в clients.json, сервер стартует с -clients-file.
                    clientId = prefs.ownClientId()
                )
            ).onFailure { fail(it.message); return@launch }
            advance()

            // NonCancellable: сервер уже настроен и запущен - уход с экрана не должен
            // оставить его без записи в приложении.
            withContext(NonCancellable) {
                val server = buildServer(cfg, c, backendPort, wgClientConf)
                prefs.addServer(server, activate = true)
                orchestrator.restartProxyIfRunning()
            }
            advance()

            HapticUtil.perform(appContext, HapticUtil.Pattern.SUCCESS)
            _uiState.update { st ->
                st.copy(
                    install = st.install?.copy(
                        done = true,
                        summary = SetupSummary(
                            serverName = serverName,
                            serverAddress = "${cfg.ip}:${c.listenPort}",
                            vpnMode = c.vpnMode,
                            obfProfile = c.obfProfile,
                            // Хинты только про авто-путь: свой conf пользователь дал сам.
                            wgConfImported = !c.wgCustomConf && wgClientConf != null,
                            usedExistingWg = c.vpnMode && !c.wgCustomConf && s.wgDetectedPort != null
                        )
                    )
                )
            }
        }
    }

    private fun buildServer(
        cfg: SshConfig,
        c: SetupConfigDraft,
        backendPort: Int,
        wgClientConf: String?
    ): Server = Server(
        name = serverName,
        ssh = cfg,
        client = ClientConfig(
            serverAddress = "${cfg.ip}:${c.listenPort}",
            vkLink = c.vkLink.trim(),
            tcpForward = !c.vpnMode && c.backendTcp,
            // WIREGUARD только с реальным конфигом: пустой нормализуется в NONE при чтении.
            tunnelTransport = if (c.vpnMode && !wgClientConf.isNullOrBlank())
                TunnelTransport.WIREGUARD else TunnelTransport.NONE,
            wireGuardConfig = wgClientConf.orEmpty()
        ),
        proxyListen = "0.0.0.0:${c.listenPort}",
        proxyConnect = "127.0.0.1:$backendPort",
        opts = ServerOpts(
            obfProfile = c.obfProfile,
            obfKey = if (c.obfProfile != ObfProfile.NONE) obfKey else ""
        )
    )

    private fun randomListenPort(): String = Random.nextInt(56000, 57000).toString()

    private fun randomWgPort(): String = Random.nextInt(51000, 52000).toString()

    companion object {
        private const val DEFAULT_WG_PORT = 51820
    }
}
