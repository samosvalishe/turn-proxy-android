@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.domain.SshConnectionState
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsBackButton
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.SettingsGroupItem
import com.freeturn.app.ui.components.SettingsSwitchRow
import com.freeturn.app.ui.util.redact
import com.freeturn.app.viewmodel.ServerHubStatus
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.serverSettingsAvailable

/**
 * Детальный экран сервера — плоский хаб: шапка-сводка, вход в настройки провайдера
 * (подключение / сервер), удаление через меню в TopAppBar.
 */
@Composable
fun ServerDetailScreen(
    serverId: String,
    settingsViewModel: SettingsViewModel,
    serverViewModel: ServerViewModel,
    onBack: () -> Unit,
    onOpenConnection: (String) -> Unit,
    onOpenConnectionMode: (String) -> Unit,
    onOpenServerSettings: (String) -> Unit,
    onOpenNerdInfo: (String) -> Unit
) {
    val context = LocalContext.current
    val snapshot by settingsViewModel.serversSnapshot.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val sshState by serverViewModel.sshState.collectAsStateWithLifecycle()
    val sshConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()
    val coreStatus by serverViewModel.hubStatus.collectAsStateWithLifecycle()
    val nerdMode by settingsViewModel.nerdMode.collectAsStateWithLifecycle()
    val server = snapshot.list.firstOrNull { it.id == serverId }
    val isActive = snapshot.activeId == serverId

    // Сервер удалён (например, из этого же экрана) — выходим назад.
    if (snapshot.loaded && server == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Единая статус-модель хаба: core-статус от VM (активный сервер) + server-контекст.
    // Порядок гарантирует отсутствие мигания: пока снапшот не загружен — skeleton, а не Offline.
    val status: ServerHubStatus = when {
        !snapshot.loaded -> ServerHubStatus.Connecting
        !isActive -> ServerHubStatus.Offline
        server?.ssh?.ip.isNullOrBlank() -> ServerHubStatus.NotPaired
        else -> coreStatus
    }
    // Вход в «Настройки сервера» доступен только при живом ядре (Online).
    val online = status as? ServerHubStatus.Online
    val connected = online != null

    // Best-effort авто-сопряжение активного сервера при входе (не дублируем на других экранах).
    // Откладываем старт на длительность nav-перехода: иначе reconnect мгновенно дёргает
    // sshState→Connecting, и hub-карточка запускает AnimatedContent size-spring + wavy-индикатор
    // ОДНОВРЕМЕННО со slide-переходом — это и есть пролаг при заходе. После перехода — плавно.
    LaunchedEffect(isActive, sshConfig.ip, sshState) {
        if (isActive && sshConfig.ip.isNotBlank() && sshState is SshConnectionState.Disconnected) {
            kotlinx.coroutines.delay(350)
            serverViewModel.reconnectSsh()
        }
    }

    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Подзаголовок hero-шапки: адрес сервера либо SSH-ip (что есть).
    val headerSubtitle = server?.let { p ->
        p.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode)
            ?: p.ssh.ip.takeIf { it.isNotBlank() }?.let { "SSH ${it.redact(privacyMode)}" }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(server?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                subtitle = headerSubtitle?.let { sub ->
                    { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior,
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.more_vert_24px),
                                contentDescription = stringResource(R.string.menu_delete_server)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            // Управление ядром — только при живом сервере (Online). Пункты
                            // адаптивны к состоянию: установка/обновление, старт, стоп. Во время
                            // действия статус уходит в Working → online == null → пункты прячутся.
                            if (online != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(
                                            if (online.installed) R.string.server_update else R.string.server_install
                                        ))
                                    },
                                    onClick = {
                                        showMenu = false
                                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                        serverViewModel.installServer()
                                    },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.cloud_download_24px), contentDescription = null)
                                    }
                                )
                                if (online.installed && !online.running) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.start_server)) },
                                        onClick = {
                                            showMenu = false
                                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                            serverViewModel.startServer()
                                        },
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.play_arrow_24px), contentDescription = null)
                                        }
                                    )
                                }
                                if (online.running) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.stop_server)) },
                                        onClick = {
                                            showMenu = false
                                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                            serverViewModel.stopServer()
                                        },
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.stop_24px), contentDescription = null)
                                        }
                                    )
                                }
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_rename_server)) },
                                onClick = { showMenu = false; showRename = true },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.edit_24px),
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.menu_delete_server),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { showMenu = false; showDelete = true },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.delete_24px),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        // Экран всегда внутри NavigationSuite — нижний бар сам держит навбар-инсет.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                if (server != null) {
                    // SSH не настроен → синхронизировать нечего: тоггл гасим и держим OFF.
                    val sshConfigured = server.ssh.ip.isNotBlank()
                    ServerStatusCard(
                        status = status,
                        syncOn = sshConfigured && server.client.syncServerSwitches,
                        onActivate = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            settingsViewModel.applyServer(serverId)
                        },
                        onRetry = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            serverViewModel.reconnectSsh()
                        }
                    )

                    // Мастер-свитч синхронизации — перенесён из «Настроек сервера» в хаб.
                    // Без подзаголовка: описание раздувало карточку, предупреждение при выкл
                    // даёт баннер на экране настроек сервера. Без SSH синхронизировать нечего —
                    // тоггл недоступен и показан выключенным.
                    SettingsCard {
                        SettingsSwitchRow(
                            title = stringResource(R.string.sync_server_switches),
                            iconRes = R.drawable.cached_24px,
                            checked = sshConfigured && server.client.syncServerSwitches,
                            enabled = sshConfigured,
                            onCheckedChange = { v ->
                                if (isActive) settingsViewModel.setSyncServerSwitches(v)
                                else settingsViewModel.updateServerClient(serverId) { it.copy(syncServerSwitches = v) }
                            }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.provider_vk_calls))
                // «Настройки сервера»: при sync ON правки пушатся на сервер → нужен живой
                // SSH; при sync OFF клиент-локальны → вход доступен и оффлайн. Правило
                // общее с ServerManagementScreen — serverSettingsAvailable.
                val syncOn = server?.client?.syncServerSwitches == true
                // Connecting/Working — transient: SSH ещё поднимается. Пункт не должен мигать,
                // держим его видимым на время подключения (сам экран при входе покажет
                // дебаунс-карту потери связи, а не пустоту). Прячем только в терминальных
                // disconnected-состояниях (Failed/NotPaired) при sync ON.
                val connecting = status is ServerHubStatus.Connecting || status is ServerHubStatus.Working
                // Без isActive-гейта: неактивный сервер форсит status=Offline → connected/connecting=false,
                // поэтому serverSettingsAvailable даёт true только при sync OFF (клиент-локальные настройки),
                // а при sync ON остаётся скрытым (пушить на сервер нечем без живого SSH активного сервера).
                val showServerSettings = serverSettingsAvailable(connected || connecting, syncOn)
                val entryCount = if (showServerSettings) 3 else 2
                SettingsGroup {
                    SettingsGroupItem(0, entryCount) {
                        SettingsEntryRow(
                            iconRes = R.drawable.mobile_24px,
                            title = stringResource(R.string.provider_connection_settings),
                            subtitle = stringResource(R.string.provider_connection_settings_desc),
                            onClick = { onOpenConnection(serverId) }
                        )
                    }
                    SettingsGroupItem(1, entryCount) {
                        SettingsEntryRow(
                            iconRes = R.drawable.wifi_24px,
                            title = stringResource(R.string.connection_mode_title),
                            subtitle = stringResource(R.string.provider_connection_mode_desc),
                            onClick = { onOpenConnectionMode(serverId) }
                        )
                    }
                    if (showServerSettings) {
                        SettingsGroupItem(2, entryCount) {
                            SettingsEntryRow(
                                iconRes = R.drawable.database_24px,
                                title = stringResource(R.string.provider_server_settings),
                                subtitle = stringResource(R.string.provider_server_settings_desc),
                                onClick = { onOpenServerSettings(serverId) }
                            )
                        }
                    }
                }

                // «Отладочная информация» — отдельный экран, вход гейтится глобальным
                // nerdMode (Продвинутые → Режим отладки).
                if (nerdMode && server != null) {
                    SettingsCard {
                        SettingsEntryRow(
                            iconRes = R.drawable.terminal_24px,
                            title = stringResource(R.string.nerd_section_title),
                            subtitle = stringResource(R.string.nerd_section_desc),
                            onClick = { onOpenNerdInfo(serverId) }
                        )
                    }
                }
            }
        }
    }

    if (showDelete && server != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.server_delete_confirm_title)) },
            text = { Text(stringResource(R.string.server_delete_confirm_desc, server.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                        settingsViewModel.deleteServer(serverId)
                        showDelete = false
                        // Навигацию назад делает null-guard выше (server станет null
                        // после async-удаления) — не зовём pop тут, иначе двойной pop.
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.server_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showRename && server != null) {
        var newName by remember(server.id) { mutableStateOf(server.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename_server_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.server_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        settingsViewModel.renameServer(serverId, newName)
                        showRename = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
