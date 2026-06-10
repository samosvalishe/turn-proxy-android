@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.data.ObfProfile
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsControlLabel
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.util.redact
import com.freeturn.app.viewmodel.ServerState
import com.freeturn.app.viewmodel.SshConnectionState
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.serverSettingsAvailable

@Composable
fun ServerManagementScreen(
    serverViewModel: ServerViewModel,
    settingsViewModel: SettingsViewModel,
    // Кнопка смены сервера (overflow ⋮).
    onEditConnection: (() -> Unit)? = null,
    // null = legacy. Не-null = настройки конкретного сервера по id (Settings).
    serverId: String? = null,
    onBack: () -> Unit
) {
    val snapshot by settingsViewModel.serversSnapshot.collectAsStateWithLifecycle()
    val server = serverId?.let { id -> snapshot.list.firstOrNull { it.id == id } }
    // Живая SSH-сессия и состояние сервера принадлежат активному серверу. Управлять
    // ядром можно только когда редактируемый сервер активен (legacy-режим без id
    // считается активным).
    val isActive = serverId == null || serverId == snapshot.activeId
    val sshState by serverViewModel.sshState.collectAsStateWithLifecycle()
    val serverState by serverViewModel.serverState.collectAsStateWithLifecycle()
    val sshConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()
    val legacyListen by settingsViewModel.proxyListen.collectAsStateWithLifecycle()
    val legacyConnect by settingsViewModel.proxyConnect.collectAsStateWithLifecycle()
    val savedListen = server?.proxyListen ?: legacyListen
    val savedConnect = server?.proxyConnect ?: legacyConnect
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val clientCfg by settingsViewModel.clientConfig.collectAsStateWithLifecycle()
    val serverOpts by serverViewModel.serverOpts.collectAsStateWithLifecycle()
    val isRegen by serverViewModel.isRegeneratingObfKey.collectAsStateWithLifecycle()

    // Источник серверных черновиков: активный сервер рулит ЖИВЫМ конфигом (legacy/global —
    // его обновляет regen на сервере), неактивный — снимком сервера by-id (sync OFF, клиент-локально).
    val effClient = if (isActive) clientCfg else (server?.client ?: clientCfg)
    val effServer = if (isActive) serverOpts else (server?.opts ?: serverOpts)

    // --- Черновики конфигурации (без авто-сохранения) ---
    var proxyListenIp by rememberSaveable(savedListen) {
        mutableStateOf(savedListen.substringBeforeLast(":", "0.0.0.0").ifBlank { "0.0.0.0" })
    }
    var proxyListenPort by rememberSaveable(savedListen) { mutableStateOf(savedListen.substringAfterLast(":", "56000")) }
    var proxyConnect by rememberSaveable(savedConnect) { mutableStateOf(savedConnect) }
    var tcpDraft by rememberSaveable(effClient.tcpForward) { mutableStateOf(effClient.tcpForward) }
    var obfDraft by rememberSaveable(effServer.obfProfile) { mutableStateOf(effServer.obfProfile) }
    var keyDraft by rememberSaveable(effServer.obfKey) { mutableStateOf(effServer.obfKey) }

    // SSH-сессию держит хаб (ServerDetailScreen): этот экран открыт только при активном
    // подключении. Свой реконнект не нужен — не дублируем коннект на двух экранах.

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showServerMenu by rememberSaveable { mutableStateOf(false) }
    // «Подключено» = ЖИВОЙ SSH ЭТОГО сервера. Живая сессия принадлежит активному серверу,
    // поэтому для неактивного всегда false (иначе чужой коннект протёк бы сюда).
    val isConnected = isActive && sshState is SshConnectionState.Connected
    val syncOn = effClient.syncServerSwitches
    val isWorking = serverState is ServerState.Working || serverState is ServerState.Checking
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // --- Dirty-детект для apply-модели ---
    val listenFull = "${proxyListenIp.ifBlank { "0.0.0.0" }}:$proxyListenPort"
    val proxyDirty = listenFull != savedListen || proxyConnect != savedConnect
    val configDirty = proxyDirty ||
        tcpDraft != effClient.tcpForward ||
        obfDraft != effServer.obfProfile ||
        keyDraft != effServer.obfKey
    // Ключ валиден для применения: обфускация выкл, 64 hex, либо пусто при живом SSH
    // и sync ON — только тогда applyServerConfig сгенерит ключ на сервере. Иначе пустой
    // ключ блокирует apply: в конфиг уехала бы обфускация без ключа, которую CoreArgs
    // молча отбросит при запуске клиента.
    val keyOkForApply = obfDraft == ObfProfile.NONE || ObfProfile.isValidKey(keyDraft) ||
        (keyDraft.isBlank() && isConnected && syncOn)

    // Плавающий «Применить» виден только когда есть что применять: фиксирует весь черновик
    // одним рестартом и уходит назад в хаб. Невалидный/занятый стейт — FAB просто прячется
    // (поле obf-ключа само подсвечивает ошибку), действие появляется когда оно осмысленно.
    val canApply = serverSettingsAvailable(isConnected, syncOn) &&
        configDirty && keyOkForApply && !isWorking
    fun applyConfig() {
        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
        // Активный — apply в живой рантайм (один рестарт). Неактивный — пишем только снимок сервера.
        if (isActive) {
            settingsViewModel.applyServerConfig(listenFull, proxyConnect, tcpDraft, obfDraft, keyDraft)
        } else {
            serverId?.let {
                settingsViewModel.updateServerConfig(it, listenFull, proxyConnect, tcpDraft, obfDraft, keyDraft)
            }
        }
        onBack()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.provider_server_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    // Смена сервера спрятана в overflow ⋮ — заголовок остаётся чистым.
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
        floatingActionButton = {
            AnimatedVisibility(
                visible = canApply,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { applyConfig() },
                    icon = { Icon(painterResource(R.drawable.check_circle_24px), contentDescription = null) },
                    text = { Text(stringResource(R.string.server_apply)) }
                )
            }
        },
        // Экран внутри NavigationSuite — нижний бар сам держит навбар-инсет.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                    .widthIn(max = SettingsContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Неактивный сервер + sync ON: серверные правки нужно пушить на сервер, а
                // живая SSH-сессия принадлежит активному. Предлагаем сделать активным.
                // При sync OFF настройки клиент-локальны — редактируем снимок сервера ниже.
                if (!isActive && syncOn) {
                    HeroCard(
                        iconRes = R.drawable.host_24px,
                        title = stringResource(R.string.server_inactive_title),
                        desc = stringResource(R.string.server_inactive_desc),
                        actionLabel = stringResource(R.string.make_active),
                        onAction = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            serverId?.let { settingsViewModel.applyServer(it) }
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                    return@Column
                }

                // SSH-сессия не активна (упала или ещё поднимается): конфиг и действия
                // требуют живого подключения, без карточки экран остаётся пустым.
                // Исключение — settings-флоу с sync OFF: серверные настройки клиент-локальны,
                // показываем их ниже как обычно. 400мс дебаунс гасит мигание на холодном
                // заходе (config грузится async); ошибка — устоявшееся состояние, сразу.
                var lostVisible by remember { mutableStateOf(false) }
                LaunchedEffect(isConnected) {
                    if (isConnected) lostVisible = false else { delay(400); lostVisible = true }
                }
                if (!isConnected && syncOn) {
                    if (sshState is SshConnectionState.Error || lostVisible) {
                        val isErr = sshState is SshConnectionState.Error
                        HeroCard(
                            iconRes = if (isErr) R.drawable.error_24px else R.drawable.host_24px,
                            iconTint = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.server_connection_lost_title),
                            desc = stringResource(R.string.server_connection_lost_desc),
                            descTint = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            actionLabel = stringResource(R.string.reconnect),
                            onAction = {
                                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                serverViewModel.reconnectSsh()
                            }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    return@Column
                }

                AnimatedVisibility(
                    visible = !syncOn,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.sync_off_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // --- Серверный конфиг (listen/connect) — SSH-only, скрыт без подключения ---
                if (isConnected) {
                    SectionLabel(stringResource(R.string.server_config))
                    SettingsCard {
                        SettingsFieldSlot {
                            OutlinedTextField(
                                value = proxyListenIp,
                                onValueChange = { v -> proxyListenIp = v.filter { c -> c.isDigit() || c == '.' || c == ':' } },
                                label = { Text(stringResource(R.string.listen_ip)) },
                                placeholder = { Text(stringResource(R.string.listen_ip_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                supportingText = { Text(stringResource(R.string.listen_ip_desc)) }
                            )
                        }
                        SettingsRowDivider()
                        SettingsFieldSlot {
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
                        }
                        SettingsRowDivider()
                        SettingsFieldSlot {
                            OutlinedTextField(
                                value = proxyConnect,
                                onValueChange = { proxyConnect = it },
                                label = { Text(stringResource(R.string.turn_client_address)) },
                                placeholder = { Text(stringResource(R.string.turn_client_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                supportingText = { Text(stringResource(R.string.turn_client_desc)) }
                            )
                        }
                    }
                }

                // --- Синхронные настройки (apply-модель) ---
                // Гейт общий со входом в экран (ServerDetailScreen) — serverSettingsAvailable.
                if (serverSettingsAvailable(isConnected, syncOn)) {
                    SectionLabel(stringResource(R.string.server_sync_section))
                    SettingsCard {
                        // Проброс: UDP / TCP.
                        SettingsFieldSlot {
                            SettingsControlLabel(stringResource(R.string.tcp_forward_mode))
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
                        SettingsRowDivider()
                        // Профиль обфускации.
                        SettingsFieldSlot {
                            SettingsControlLabel(stringResource(R.string.obf_profile_title))
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
                        SettingsRowDivider()
                        // obf-ключ (черновик) + регенерация на сервере (живая SSH-операция).
                        if (obfDraft != ObfProfile.NONE) {
                            SettingsFieldSlot {
                                OutlinedTextField(
                                    value = if (privacyMode) effServer.obfKey.redact(true) else keyDraft,
                                    onValueChange = { if (!privacyMode) keyDraft = it },
                                    label = { Text(stringResource(R.string.server_obf_key_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = privacyMode,
                                    singleLine = true,
                                    isError = (keyDraft.isNotBlank() && !ObfProfile.isValidKey(keyDraft)) ||
                                        (keyDraft.isBlank() && !(isConnected && syncOn)),
                                    trailingIcon = {
                                        if (effServer.obfKey.isNotBlank() && !privacyMode) {
                                            IconButton(onClick = {
                                                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("obf-key", effServer.obfKey))
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
                                            keyDraft.isBlank() && !isConnected -> Text(
                                                stringResource(R.string.obf_key_manual_hint),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            keyDraft.isBlank() && !syncOn -> Text(
                                                stringResource(R.string.obf_key_empty_hint),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            keyDraft.isBlank() -> Text(stringResource(R.string.obf_key_empty_hint))
                                            !ObfProfile.isValidKey(keyDraft) -> Text(
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
                        } else {
                            // obfDraft == NONE — подсказка выбрать профиль.
                            SettingsFieldSlot {
                                Text(
                                    stringResource(R.string.obf_select_profile_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                }

                // Клиренс под плавающую кнопку, чтобы FAB не перекрывал нижний контент.
                Spacer(Modifier.height(if (canApply) 88.dp else 24.dp))
            }
        }
    }
}

/**
 * Hero-карточка состояния (неактивный сервер / потеря связи): тональный контейнер,
 * центрированная иконка + заголовок + пояснение + основная кнопка. Один источник стиля
 * для пустых/аварийных состояний экрана.
 */
@Composable
private fun HeroCard(
    iconRes: Int,
    title: String,
    desc: String,
    actionLabel: String,
    onAction: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    descTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(painterResource(iconRes), contentDescription = null, tint = iconTint)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = descTint,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) { Text(actionLabel) }
        }
    }
}

@Composable
private fun obfProfileLabel(value: String): String = when (value) {
    ObfProfile.NONE -> stringResource(R.string.obf_none)
    ObfProfile.RTPOPUS -> stringResource(R.string.obf_rtpopus)
    else -> value
}
