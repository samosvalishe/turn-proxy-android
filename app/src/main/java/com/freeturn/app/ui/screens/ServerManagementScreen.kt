@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.data.ObfProfile
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.ServerState
import com.freeturn.app.viewmodel.SshConnectionState
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagementScreen(
    serverViewModel: ServerViewModel,
    settingsViewModel: SettingsViewModel,
    // Шаг мастера онбординга «продолжить к настройке клиента». В settings-флоу не нужен.
    onContinue: (() -> Unit)? = null,
    // Кнопка смены сервера (overflow ⋮). null в онбординге.
    onEditConnection: (() -> Unit)? = null,
    // null = онбординг/legacy. Не-null = настройки сервера конкретного профиля (Settings).
    profileId: String? = null,
    onBack: (() -> Unit)? = null
) {
    // Settings-флоу vs мастер онбординга. В settings: apply-модель (черновики → «Применить»,
    // один рестарт), карточки сопряжения, смена сервера. В онбординге — proxy + действия.
    val settingsFlow = onBack != null
    val snapshot by settingsViewModel.profilesSnapshot.collectAsStateWithLifecycle()
    val profile = profileId?.let { id -> snapshot.list.firstOrNull { it.id == id } }
    // Живая SSH-сессия и состояние сервера принадлежат активному серверу. Управлять
    // ядром можно только когда редактируемый профиль активен (или онбординг).
    val isActive = profileId == null || profileId == snapshot.activeId
    val sshState by serverViewModel.sshState.collectAsStateWithLifecycle()
    val serverState by serverViewModel.serverState.collectAsStateWithLifecycle()
    val sshConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()
    val legacyListen by settingsViewModel.proxyListen.collectAsStateWithLifecycle()
    val legacyConnect by settingsViewModel.proxyConnect.collectAsStateWithLifecycle()
    val savedListen = profile?.proxyListen ?: legacyListen
    val savedConnect = profile?.proxyConnect ?: legacyConnect
    val sshLog by serverViewModel.sshLog.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val clientCfg by settingsViewModel.clientConfig.collectAsStateWithLifecycle()
    val serverLogs by serverViewModel.serverLogs.collectAsStateWithLifecycle()
    val isWgWorking by serverViewModel.isWgWorking.collectAsStateWithLifecycle()
    val lastWgConfig by serverViewModel.lastWgConfig.collectAsStateWithLifecycle()
    val serverOpts by serverViewModel.serverOpts.collectAsStateWithLifecycle()
    val isRegen by serverViewModel.isRegeneratingObfKey.collectAsStateWithLifecycle()

    // --- Черновики конфигурации (без авто-сохранения) ---
    var proxyListenIp by rememberSaveable(savedListen) {
        mutableStateOf(savedListen.substringBeforeLast(":", "0.0.0.0").ifBlank { "0.0.0.0" })
    }
    var proxyListenPort by rememberSaveable(savedListen) { mutableStateOf(savedListen.substringAfterLast(":", "56000")) }
    var proxyConnect by rememberSaveable(savedConnect) { mutableStateOf(savedConnect) }
    var tcpDraft by rememberSaveable(clientCfg.tcpForward) { mutableStateOf(clientCfg.tcpForward) }
    var obfDraft by rememberSaveable(serverOpts.obfProfile) { mutableStateOf(serverOpts.obfProfile) }
    var keyDraft by rememberSaveable(serverOpts.obfKey) { mutableStateOf(serverOpts.obfKey) }

    // SSH-сессию держит хаб (ServerDetailScreen): этот экран в settings-флоу открыт только
    // при активном подключении, в онбординг сюда приходят уже Connected. Свой реконнект
    // не нужен — не дублируем коннект на двух экранах.

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showServerMenu by rememberSaveable { mutableStateOf(false) }
    val isConnected = sshState is SshConnectionState.Connected
    // Дебаунс карточки сопряжения: при открытии config грузится из DataStore async,
    // а sshState стартует Disconnected — без задержки карточка мигает на доли секунды
    // до прихода Connected. Ошибку показываем сразу (уже устаканившееся состояние).
    var pairingPromptVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        if (isConnected) {
            pairingPromptVisible = false
        } else {
            delay(400)
            pairingPromptVisible = true
        }
    }
    val showPairing = sshState is SshConnectionState.Error || pairingPromptVisible
    val isWorking = serverState is ServerState.Working || serverState is ServerState.Checking
    val serverKnown = serverState as? ServerState.Known
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // --- Dirty-детект для apply-модели ---
    val obfKeyRegex = remember { Regex("^[0-9a-fA-F]{64}$") }
    val listenFull = "${proxyListenIp.ifBlank { "0.0.0.0" }}:$proxyListenPort"
    val proxyDirty = listenFull != savedListen || proxyConnect != savedConnect
    val configDirty = settingsFlow && (proxyDirty ||
        tcpDraft != clientCfg.tcpForward ||
        obfDraft != serverOpts.obfProfile ||
        keyDraft != serverOpts.obfKey)
    // Ключ валиден для применения: обфускация выкл, ИЛИ пусто (сервер сгенерит), ИЛИ 64 hex.
    val keyOkForApply = obfDraft == ObfProfile.NONE || keyDraft.isBlank() || keyDraft.matches(obfKeyRegex)


    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(
                        if (onBack != null) R.string.provider_server_settings else R.string.server
                    ))
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    // App bar разгружен: SSH-статус живёт в карточке статуса ниже, смена
                    // сервера спрятана в overflow ⋮ — заголовок остаётся в одну строку.
                    if (isActive && onEditConnection != null) {
                        Box {
                            IconButton(onClick = { showServerMenu = true }) {
                                Icon(
                                    painterResource(R.drawable.more_vert_24px),
                                    contentDescription = stringResource(R.string.change_server)
                                )
                            }
                            DropdownMenu(
                                expanded = showServerMenu,
                                onDismissRequest = { showServerMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.change_server)) },
                                    onClick = {
                                        showServerMenu = false
                                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                        onEditConnection()
                                    },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.host_24px), contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Settings-флоу (onBack != null) — внутри NavigationSuite, бар держит навбар-инсет.
        // Онбординг (onBack == null) — полноэкранный, инсет держим сами.
        contentWindowInsets = if (onBack != null) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Неактивный профиль: живая SSH-сессия и управление ядром недоступны.
                // Предлагаем сделать активным — после этого SSH/управление оживают.
                if (!isActive) {
                    InactiveServerCard(
                        onMakeActive = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            profileId?.let { settingsViewModel.applyProfile(it) }
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                    return@Column
                }

                // Settings-флоу без активного сопряжения → приглашение/прогресс/ошибка
                // вместо бесполезных задизейбленных контролов. В онбординге сюда приходят
                // уже Connected, поэтому ветка management как раньше.
                if (settingsFlow && !isConnected) {
                    // showPairing дебаунсит первичный показ — пустой экран до 400мс
                    // вместо мигающей карточки при загрузке config / авто-сопряжении.
                    if (showPairing) {
                        SshPairingPrompt(
                            state = sshState,
                            configBlank = sshConfig.ip.isBlank(),
                            onConnect = { onEditConnection?.invoke() },
                            onRetry = { serverViewModel.reconnectSsh() }
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                    return@Column
                }

                AnimatedVisibility(
                    visible = !clientCfg.syncServerSwitches,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            stringResource(R.string.sync_off_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Статус сервера теперь в хабе (ServerDetailScreen.ServerStatusCard) —
                // тут не дублируем. На этом экране — конфиг (apply-модель) + действия.

                // Server config
                Text(
                    stringResource(R.string.server_config),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )

                OutlinedTextField(
                    value = proxyListenIp,
                    onValueChange = { v -> proxyListenIp = v.filter { c -> c.isDigit() || c == '.' || c == ':' } },
                    label = { Text(stringResource(R.string.listen_ip)) },
                    placeholder = { Text(stringResource(R.string.listen_ip_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.listen_ip_desc)) }
                )

                OutlinedTextField(
                    value = proxyListenPort,
                    onValueChange = { proxyListenPort = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.listen_port)) },
                    placeholder = { Text(stringResource(R.string.listen_port_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(stringResource(R.string.listen_port_desc)) }
                )

                OutlinedTextField(
                    value = proxyConnect,
                    onValueChange = { proxyConnect = it },
                    label = { Text(stringResource(R.string.turn_client_address)) },
                    placeholder = { Text(stringResource(R.string.turn_client_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.turn_client_desc)) }
                )

                // --- Синхронные настройки сервера (apply-модель, только settings-флоу) ---
                if (settingsFlow) {
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    Text(
                        stringResource(R.string.server_sync_section),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )

                    // Проброс: UDP / TCP.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.tcp_forward_mode), style = MaterialTheme.typography.bodyMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !tcpDraft,
                                onClick = { HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON); tcpDraft = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text(stringResource(R.string.udp)) }
                            SegmentedButton(
                                selected = tcpDraft,
                                onClick = { HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON); tcpDraft = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text(stringResource(R.string.tcp)) }
                        }
                    }

                    // Профиль обфускации.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.obf_profile_title), style = MaterialTheme.typography.bodyMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ObfProfile.ALL.forEachIndexed { idx, value ->
                                SegmentedButton(
                                    selected = obfDraft == value,
                                    onClick = { HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON); obfDraft = value },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = ObfProfile.ALL.size)
                                ) { Text(obfProfileLabel(value)) }
                            }
                        }
                    }

                    // obf-ключ (черновик) + регенерация на сервере (живая SSH-операция).
                    if (obfDraft != ObfProfile.NONE) {
                        OutlinedTextField(
                            value = if (privacyMode) serverOpts.obfKey.redact(true) else keyDraft,
                            onValueChange = { if (!privacyMode) keyDraft = it },
                            label = { Text(stringResource(R.string.server_obf_key_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = privacyMode,
                            singleLine = true,
                            isError = keyDraft.isNotBlank() && !keyDraft.matches(obfKeyRegex),
                            trailingIcon = {
                                if (serverOpts.obfKey.isNotBlank() && !privacyMode) {
                                    IconButton(onClick = {
                                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("obf-key", serverOpts.obfKey))
                                    }) {
                                        Icon(
                                            painterResource(R.drawable.content_copy_24px),
                                            contentDescription = stringResource(R.string.copy)
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                when {
                                    keyDraft.isBlank() -> Text(stringResource(R.string.obf_key_empty_hint))
                                    !keyDraft.matches(obfKeyRegex) -> Text(
                                        stringResource(R.string.obf_key_invalid_hint),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                        if (isConnected && !privacyMode) {
                            TextButton(
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                    serverViewModel.regenerateObfKey()
                                },
                                enabled = !isRegen,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isRegen) {
                                    CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.obf_key_regen_in_progress))
                                } else {
                                    Text(stringResource(R.string.obf_key_regen))
                                }
                            }
                        }
                    }

                    // Применить — фиксирует весь черновик одним рестартом, затем назад в хаб.
                    Button(
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            settingsViewModel.applyServerConfig(listenFull, proxyConnect, tcpDraft, obfDraft, keyDraft)
                            onBack?.invoke()
                        },
                        enabled = configDirty && keyOkForApply && !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(painterResource(R.drawable.check_circle_24px), null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.server_apply))
                    }
                    Text(
                        stringResource(R.string.server_apply_restart_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }


                // Action buttons
                FilledTonalButton(
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        settingsViewModel.saveProxyServerConfig("${proxyListenIp.ifBlank { "0.0.0.0" }}:$proxyListenPort", proxyConnect)
                        serverViewModel.installServer()
                    },
                    enabled = isConnected && !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(painterResource(R.drawable.cloud_download_24px), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.install))
                }

                Button(
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        settingsViewModel.saveProxyServerConfig("${proxyListenIp.ifBlank { "0.0.0.0" }}:$proxyListenPort", proxyConnect)
                        serverViewModel.startServer()
                    },
                    enabled = (isConnected && !isWorking
                            && serverKnown?.installed == true) && !serverKnown.running,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(painterResource(R.drawable.play_arrow_24px), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start_server))
                }

                OutlinedButton(
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        serverViewModel.stopServer()
                    },
                    enabled = isConnected && !isWorking && serverKnown?.running == true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(painterResource(R.drawable.stop_24px), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stop_server))
                }

                // «Продолжить к настройке клиента» — шаг мастера онбординга (onBack == null).
                // В settings-флоу (onBack != null) этой кнопки нет — там провайдер уже
                // даёт отдельный вход в «Настройки подключения».
                if (serverKnown?.running == true && onBack == null) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            onContinue?.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.continue_client_setup))
                        Spacer(Modifier.width(8.dp))
                        Icon(painterResource(R.drawable.arrow_forward_24px), null)
                    }
                }

                // WireGuard управление (установка / получение конфига пира)
                if (isConnected) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "WireGuard",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(12.dp))

                            FilledTonalButton(
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                    serverViewModel.wgInstall()
                                },
                                enabled = !isWorking && !isWgWorking,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            ) {
                                if (isWgWorking) {
                                    androidx.compose.material3.CircularWavyProgressIndicator(
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(painterResource(R.drawable.cloud_download_24px), null)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.wg_install_btn))
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                    serverViewModel.wgShowPeer()
                                },
                                enabled = !isWorking && !isWgWorking,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(painterResource(R.drawable.refresh_24px), null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.wg_show_peer_btn))
                            }

                            if (lastWgConfig != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.wg_config_saved),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.extendedColorScheme.info
                                )
                            }
                        }
                    }
                }

                // Журнал сервера (journalctl/server.log через SSH).
                // Скрываем без SSH-подключения — симметрично с SSH-логом ниже.
                if (isConnected) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.server_journal_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                    serverViewModel.fetchServerLogs()
                                },
                                enabled = isConnected && serverLogs != "…"
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.refresh_24px),
                                    contentDescription = stringResource(R.string.server_journal_refresh)
                                )
                            }
                            if (serverLogs != null) {
                                IconButton(
                                    onClick = {
                                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                        serverViewModel.clearServerLogs()
                                    },
                                    enabled = serverLogs != "…"
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.delete_24px),
                                        contentDescription = stringResource(R.string.server_journal_clear)
                                    )
                                }
                            }
                        }
                        if (serverLogs != null) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                val text = when (serverLogs) {
                                    "…"  -> stringResource(R.string.server_journal_loading)
                                    "(лог пуст)" -> stringResource(R.string.server_journal_empty)
                                    else -> serverLogs ?: ""
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
                }

                // SSH-лог (вывод всех команд)
                if (sshLog.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.ssh_log_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            val listState = rememberLazyListState()
                            LaunchedEffect(sshLog.size) {
                                if (sshLog.isNotEmpty()) listState.animateScrollToItem(sshLog.lastIndex)
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp)
                                        .padding(10.dp)
                                ) {
                                    items(sshLog) { line ->
                                        val isHeader = line.startsWith("===")
                                        val isError = line.contains("ERROR", ignoreCase = true) ||
                                                      line.contains("error", ignoreCase = true) ||
                                                      line.contains("failed", ignoreCase = true)
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = when {
                                                isHeader -> MaterialTheme.colorScheme.primary
                                                isError  -> MaterialTheme.colorScheme.error
                                                else     -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Карточка для неактивного профиля: управление ядром требует активного сервера. */
@Composable
private fun InactiveServerCard(onMakeActive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painterResource(R.drawable.host_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.server_inactive_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.server_inactive_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onMakeActive,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) { Text(stringResource(R.string.make_active)) }
        }
    }
}

/**
 * Состояние сопряжения для основной вкладки сервера, когда соединение ещё не
 * установлено: приглашение подключиться (нет конфига), прогресс (идёт авто-/ручное
 * сопряжение) или ошибка с повтором.
 */
@Composable
private fun SshPairingPrompt(
    state: SshConnectionState,
    configBlank: Boolean,
    onConnect: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    // Disconnected с сохранённым конфигом — транзитное: авто-сопряжение вот-вот
    // сработает, показываем прогресс, а не кнопку «Подключиться» (без мигания).
    val connecting = state is SshConnectionState.Connecting ||
        (state is SshConnectionState.Disconnected && !configBlank)
    // Ключ для AnimatedContent: морфим только при смене фазы, а не при каждом
    // обновлении state.message внутри Error.
    val phase = when {
        state is SshConnectionState.Error -> "error"
        connecting -> "connecting"
        else -> "prompt"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = phase,
            label = "ssh-pairing",
            modifier = Modifier.fillMaxWidth()
        ) { target ->
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (target) {
                    "error" -> {
                        val message = (state as? SshConnectionState.Error)?.message.orEmpty()
                        // Иконка + текст — единый узел для TalkBack: озвучивается как ошибка.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.semantics(mergeDescendants = true) {}
                        ) {
                            Icon(
                                painterResource(R.drawable.error_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = {
                                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                onRetry()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) { Text(stringResource(R.string.reconnect)) }
                    }

                    "connecting" -> {
                        Text(
                            stringResource(R.string.ssh_connecting),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    else -> {
                        Icon(
                            painterResource(R.drawable.host_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.server_not_paired_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                onConnect()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) { Text(stringResource(R.string.connect_btn)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun obfProfileLabel(value: String): String = when (value) {
    ObfProfile.NONE -> stringResource(R.string.obf_none)
    ObfProfile.RTPOPUS -> stringResource(R.string.obf_rtpopus)
    else -> value
}

