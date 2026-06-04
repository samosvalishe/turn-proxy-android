@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onContinue: () -> Unit,
    // null — поток онбординга (SERVER_MANAGEMENT_OB): сюда приходят уже сопряжёнными,
    // SSH-логика не активна, экран рендерится как раньше. Не-null — основная вкладка:
    // включает авто-сопряжение при открытии, кнопку смены сервера и состояния
    // «не сопряжено / подключаюсь / ошибка».
    onEditConnection: (() -> Unit)? = null
) {
    val mainFlow = onEditConnection != null
    val sshState by serverViewModel.sshState.collectAsStateWithLifecycle()
    val serverState by serverViewModel.serverState.collectAsStateWithLifecycle()
    val sshConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()
    val savedListen by settingsViewModel.proxyListen.collectAsStateWithLifecycle()
    val savedConnect by settingsViewModel.proxyConnect.collectAsStateWithLifecycle()
    val sshLog by serverViewModel.sshLog.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val installStage by serverViewModel.serverInstallStage.collectAsStateWithLifecycle()
    val clientCfg by settingsViewModel.clientConfig.collectAsStateWithLifecycle()
    val serverLogs by serverViewModel.serverLogs.collectAsStateWithLifecycle()
    val isWgWorking by serverViewModel.isWgWorking.collectAsStateWithLifecycle()
    val lastWgConfig by serverViewModel.lastWgConfig.collectAsStateWithLifecycle()

    var proxyListenIp by rememberSaveable(savedListen) {
        mutableStateOf(savedListen.substringBeforeLast(":", "0.0.0.0").ifBlank { "0.0.0.0" })
    }
    var proxyListenPort by rememberSaveable(savedListen) { mutableStateOf(savedListen.substringAfterLast(":", "56000")) }
    var proxyConnect by rememberSaveable(savedConnect) { mutableStateOf(savedConnect) }

    LaunchedEffect(proxyListenIp, proxyListenPort, proxyConnect) {
        kotlinx.coroutines.delay(400)
        val ip = proxyListenIp.ifBlank { "0.0.0.0" }
        val listen = "$ip:$proxyListenPort"
        if (listen != savedListen || proxyConnect != savedConnect) {
            settingsViewModel.saveProxyServerConfig(listen, proxyConnect)
        }
    }

    // Авто-сопряжение при открытии основной вкладки: есть сохранённый конфиг, но
    // соединение не установлено → пробуем переподключиться. Триггер только из
    // Disconnected (после неудачи состояние Error — без бесконечного цикла). Ключ по
    // ip корректно отрабатывает асинхронную загрузку конфига из DataStore.
    LaunchedEffect(sshConfig.ip, mainFlow) {
        if (mainFlow && sshConfig.ip.isNotBlank() && sshState is SshConnectionState.Disconnected) {
            serverViewModel.reconnectSsh()
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    SshStatusBadge(sshState = sshState, ip = sshConfig.ip.redact(privacyMode))
                    // Вход в форму сопряжения / смены сервера (перенесён с главного экрана).
                    if (onEditConnection != null) {
                        IconButton(onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            onEditConnection()
                        }) {
                            Icon(
                                painterResource(R.drawable.host_24px),
                                contentDescription = stringResource(R.string.connection)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Основная вкладка без активного сопряжения → приглашение/прогресс/ошибка
                // вместо бесполезных задизейбленных контролов. В онбординге (mainFlow=false)
                // сюда приходят уже Connected, поэтому ветка management как раньше.
                if (mainFlow && !isConnected) {
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

                // Status card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.server_status), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        StatusRow(
                            label = stringResource(R.string.ssh_connection),
                            isActive = isConnected,
                            activeLabel = stringResource(R.string.paired),
                            activeColor = MaterialTheme.extendedColorScheme.info
                        )
                        Spacer(Modifier.height(10.dp))
                        StatusRow(stringResource(R.string.free_turn_proxy), serverKnown?.running == true)

                        if (serverKnown?.installed == true && !serverKnown.version.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.server_version_label, serverKnown.version),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        installStage?.let { stage ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when (stage) {
                                    "cached" -> stringResource(R.string.server_install_cached)
                                    "downloaded" -> stringResource(R.string.server_install_downloaded)
                                    else -> stage
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.extendedColorScheme.info
                            )
                        }

                        when (serverState) {
                            is ServerState.Checking -> {
                                Spacer(Modifier.height(12.dp))
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            is ServerState.Working -> {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    (serverState as ServerState.Working).action,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            is ServerState.Error -> {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.error_format, (serverState as ServerState.Error).message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }
                }

                // Server config
                Text(stringResource(R.string.server_config), style = MaterialTheme.typography.titleMedium)

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

                // Индикатор TCP-форвард режима сервера
                if (serverKnown?.running == true && serverKnown.tcpMode == true) {
                    Text(
                        text = stringResource(R.string.server_running_tcp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.extendedColorScheme.info
                    )
                }

                // Режим туннеля / обфускация управляются на клиентском экране — общий source.
                // Здесь не дублируем, чтобы не путать пользователя двумя точками контроля.

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

                if (serverKnown?.running == true) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            onContinue()
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
                            androidx.compose.material3.IconButton(
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
                                androidx.compose.material3.IconButton(
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
private fun SshStatusBadge(sshState: SshConnectionState, ip: String) {
    val connected = sshState is SshConnectionState.Connected
    val dotColor = if (connected) MaterialTheme.extendedColorScheme.info
                   else MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (connected) ip else stringResource(R.string.not_connected),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    isActive: Boolean,
    activeLabel: String = stringResource(R.string.active),
    activeColor: Color = MaterialTheme.extendedColorScheme.success
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isActive) activeColor else MaterialTheme.colorScheme.outline,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isActive) activeLabel else stringResource(R.string.inactive),
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.outline
            )
        }
    }
}
