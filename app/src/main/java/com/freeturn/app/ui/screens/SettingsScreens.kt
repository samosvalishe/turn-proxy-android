@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontFamily
import com.freeturn.app.R
import com.freeturn.app.data.CoreArgs
import com.freeturn.app.data.ObfProfile
import com.freeturn.app.data.Profile
import com.freeturn.app.domain.server.ServerCommand
import com.freeturn.app.domain.server.ServerOptions
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.EmptyServersState
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.ServerRow
import com.freeturn.app.ui.components.SettingsBackButton
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.SettingsGroupItem
import com.freeturn.app.ui.components.SettingsSwitchRow
import com.freeturn.app.ui.components.settingsItemShape
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.ServerHubStatus
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.SshConnectionState
import com.freeturn.app.viewmodel.serverSettingsAvailable


/** Корневой экран настроек (нижнее меню): серверы, приложение, продвинутые, о проекте. */
@Composable
fun SettingsScreen(
    onOpenServers: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior
            )
        },
        // Корневой экран — внутри NavigationSuite, нижний бар сам держит инсет.
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
                // Сегментированная группа (M3 expressive): пункты с микро-зазором,
                // наружные углы большие. Новые пункты добавляются строкой в группу,
                // при разрастании — разбиение на группы с SectionLabel.
                SettingsGroup {
                    SettingsGroupItem(0, 4) {
                        SettingsEntryRow(
                            iconRes = R.drawable.database_24px,
                            title = stringResource(R.string.settings_servers),
                            subtitle = stringResource(R.string.settings_servers_desc),
                            onClick = onOpenServers
                        )
                    }
                    SettingsGroupItem(1, 4) {
                        SettingsEntryRow(
                            iconRes = R.drawable.mobile_24px,
                            title = stringResource(R.string.settings_app),
                            subtitle = stringResource(R.string.settings_app_desc),
                            onClick = onOpenApp
                        )
                    }
                    SettingsGroupItem(2, 4) {
                        SettingsEntryRow(
                            iconRes = R.drawable.tune_24px,
                            title = stringResource(R.string.settings_advanced),
                            subtitle = stringResource(R.string.settings_advanced_desc),
                            onClick = onOpenAdvanced
                        )
                    }
                    SettingsGroupItem(3, 4) {
                        SettingsEntryRow(
                            iconRes = R.drawable.info_24px,
                            title = stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_about_desc),
                            onClick = onOpenAbout
                        )
                    }
                }
            }
        }
    }
}

/**
 * «Продвинутые» — мастер-свитч «Режим отладки» ([SettingsViewModel.nerdMode]): включает
 * отладочную информацию в хабе сервера (журнал, подробные логи) и кнопку логов на главном экране.
 */
@Composable
fun AdvancedScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val nerdMode by settingsViewModel.nerdMode.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_advanced)) },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
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
                SettingsCard {
                    SettingsSwitchRow(
                        title = stringResource(R.string.nerd_mode),
                        subtitle = stringResource(R.string.nerd_mode_desc),
                        iconRes = R.drawable.terminal_24px,
                        checked = nerdMode,
                        onCheckedChange = { settingsViewModel.setNerdMode(it) }
                    )
                }
            }
        }
    }
}

/** Список добавленных серверов (профилей). Клик по серверу → его детальный экран. */
@Composable
fun ServersListScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenServer: (String) -> Unit
) {
    val snapshot by settingsViewModel.profilesSnapshot.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_servers)) },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
        // Экран всегда внутри NavigationSuite — нижний бар сам держит навбар-инсет.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (snapshot.loaded && snapshot.list.isEmpty()) {
            EmptyServersState(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Активный профиль закрепляем сверху — быстрый доступ к текущему серверу.
                val ordered = remember(snapshot.list, snapshot.activeId) {
                    snapshot.list.sortedByDescending { it.id == snapshot.activeId }
                }
                SettingsGroup {
                    ordered.forEachIndexed { index, p ->
                        ServerListRow(
                            profile = p,
                            isActive = snapshot.activeId == p.id,
                            privacyMode = privacyMode,
                            shape = settingsItemShape(index, ordered.size),
                            onClick = { onOpenServer(p.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerListRow(
    profile: Profile,
    isActive: Boolean,
    privacyMode: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    // Подзаголовок: адрес сервера + метка «SSH», если сопряжение настроено. Сам SSH-ip
    // не дублируем (он уже не несёт пользе в списке) — достаточно факта наличия.
    val sub = listOfNotNull(
        profile.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode),
        stringResource(R.string.profile_has_ssh).takeIf { profile.ssh.ip.isNotBlank() }
    ).joinToString(" · ").ifBlank { "—" }
    ServerRow(
        name = profile.name,
        subtitle = sub,
        isActive = isActive,
        shape = shape,
        onClick = onClick,
        trailingIconRes = R.drawable.chevron_right_24px
    )
}

/**
 * Детальный экран сервера — плоский хаб: шапка-сводка, вход в настройки провайдера
 * (подключение / сервер), удаление через меню в TopAppBar.
 */
@Composable
fun ServerDetailScreen(
    profileId: String,
    settingsViewModel: SettingsViewModel,
    serverViewModel: ServerViewModel,
    onBack: () -> Unit,
    onOpenConnection: (String) -> Unit,
    onOpenConnectionMode: (String) -> Unit,
    onOpenServerSettings: (String) -> Unit,
    onOpenNerdInfo: (String) -> Unit,
    onConfigureSsh: () -> Unit
) {
    val context = LocalContext.current
    val snapshot by settingsViewModel.profilesSnapshot.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val sshState by serverViewModel.sshState.collectAsStateWithLifecycle()
    val sshConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()
    val coreStatus by serverViewModel.hubStatus.collectAsStateWithLifecycle()
    val nerdMode by settingsViewModel.nerdMode.collectAsStateWithLifecycle()
    val profile = snapshot.list.firstOrNull { it.id == profileId }
    val isActive = snapshot.activeId == profileId

    // Профиль удалён (например, из этого же экрана) — выходим назад.
    if (snapshot.loaded && profile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Единая статус-модель хаба: core-статус от VM (активный сервер) + profile-контекст.
    // Порядок гарантирует отсутствие мигания: пока снапшот не загружен — skeleton, а не Offline.
    val status: ServerHubStatus = when {
        !snapshot.loaded -> ServerHubStatus.Connecting
        !isActive -> ServerHubStatus.Offline
        profile?.ssh?.ip.isNullOrBlank() -> ServerHubStatus.NotPaired
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
    val headerSubtitle = profile?.let { p ->
        p.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode)
            ?: p.ssh.ip.takeIf { it.isNotBlank() }?.let { "SSH ${it.redact(privacyMode)}" }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(profile?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                if (profile != null) {
                    // SSH не настроен → синхронизировать нечего: тоггл гасим и держим OFF.
                    val sshConfigured = profile.ssh.ip.isNotBlank()
                    ServerStatusCard(
                        status = status,
                        syncOn = sshConfigured && profile.client.syncServerSwitches,
                        onActivate = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            settingsViewModel.applyProfile(profileId)
                        },
                        onRetry = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            serverViewModel.reconnectSsh()
                        },
                        onConfigureSsh = onConfigureSsh
                    )

                    // Мастер-свитч синхронизации — перенесён из «Настроек сервера» в хаб.
                    // Без подзаголовка: описание раздувало карточку, предупреждение при выкл
                    // даёт баннер на экране настроек сервера. Без SSH синхронизировать нечего —
                    // тоггл недоступен и показан выключенным.
                    SettingsCard {
                        SettingsSwitchRow(
                            title = stringResource(R.string.sync_server_switches),
                            iconRes = R.drawable.cached_24px,
                            checked = sshConfigured && profile.client.syncServerSwitches,
                            enabled = sshConfigured,
                            onCheckedChange = { v ->
                                if (isActive) settingsViewModel.setSyncServerSwitches(v)
                                else settingsViewModel.updateProfileClient(profileId) { it.copy(syncServerSwitches = v) }
                            }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.provider_vk_calls))
                // «Настройки сервера»: при sync ON правки пушатся на сервер → нужен живой
                // SSH; при sync OFF клиент-локальны → вход доступен и оффлайн. Правило
                // общее с ServerManagementScreen — serverSettingsAvailable.
                val syncOn = profile?.client?.syncServerSwitches == true
                // Connecting/Working — transient: SSH ещё поднимается. Пункт не должен мигать,
                // держим его видимым на время подключения (сам экран при входе покажет
                // дебаунс-карту потери связи, а не пустоту). Прячем только в терминальных
                // disconnected-состояниях (Failed/NotPaired) при sync ON.
                val connecting = status is ServerHubStatus.Connecting || status is ServerHubStatus.Working
                // Без isActive-гейта: неактивный профиль форсит status=Offline → connected/connecting=false,
                // поэтому serverSettingsAvailable даёт true только при sync OFF (клиент-локальные настройки),
                // а при sync ON остаётся скрытым (пушить на сервер нечем без живого SSH активного профиля).
                val showServerSettings = serverSettingsAvailable(connected || connecting, syncOn)
                val entryCount = if (showServerSettings) 3 else 2
                SettingsGroup {
                    SettingsGroupItem(0, entryCount) {
                        SettingsEntryRow(
                            iconRes = R.drawable.mobile_24px,
                            title = stringResource(R.string.provider_connection_settings),
                            subtitle = stringResource(R.string.provider_connection_settings_desc),
                            onClick = { onOpenConnection(profileId) }
                        )
                    }
                    SettingsGroupItem(1, entryCount) {
                        SettingsEntryRow(
                            iconRes = R.drawable.wifi_24px,
                            title = stringResource(R.string.connection_mode_title),
                            subtitle = stringResource(R.string.provider_connection_mode_desc),
                            onClick = { onOpenConnectionMode(profileId) }
                        )
                    }
                    if (showServerSettings) {
                        SettingsGroupItem(2, entryCount) {
                            SettingsEntryRow(
                                iconRes = R.drawable.database_24px,
                                title = stringResource(R.string.provider_server_settings),
                                subtitle = stringResource(R.string.provider_server_settings_desc),
                                onClick = { onOpenServerSettings(profileId) }
                            )
                        }
                    }
                }

                // «Отладочная информация» — отдельный экран, вход гейтится глобальным
                // nerdMode (Продвинутые → Режим отладки).
                if (nerdMode && profile != null) {
                    SettingsCard {
                        SettingsEntryRow(
                            iconRes = R.drawable.terminal_24px,
                            title = stringResource(R.string.nerd_section_title),
                            subtitle = stringResource(R.string.nerd_section_desc),
                            onClick = { onOpenNerdInfo(profileId) }
                        )
                    }
                }
            }
        }
    }

    if (showDelete && profile != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.server_delete_confirm_title)) },
            text = { Text(stringResource(R.string.server_delete_confirm_desc, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                        settingsViewModel.deleteProfile(profileId)
                        showDelete = false
                        // Навигацию назад делает null-guard выше (profile станет null
                        // после async-удаления) — не зовём pop тут, иначе двойной pop.
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.profile_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showRename && profile != null) {
        var newName by remember(profile.id) { mutableStateOf(profile.name) }
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
                        settingsViewModel.renameProfile(profileId, newName)
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

/**
 * Карточка статуса сервера в хабе. Один источник истины — [ServerHubStatus] (собран в VM).
 * Дизайн: hero-строка (живой индикатор + заголовок фазы) несёт статус ОДИН раз — без
 * дублирующих тегов (детали ядра живут в «Отладочной информации»). Тело меняется одним
 * [AnimatedContent] (size-spring, M3 expressive); фазы холодного захода
 * (disconnected/connecting/checking) свёрнуты в Connecting — один переход в Online.
 */
@Composable
private fun ServerStatusCard(
    status: ServerHubStatus,
    syncOn: Boolean,
    onActivate: () -> Unit,
    onRetry: () -> Unit,
    onConfigureSsh: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    // Sync OFF — live-фазы ядра (online/connecting/working/failed) нерелевантны: клиент с
    // сервером не общается. Схлопываем их в нейтральную заглушку. Actionable-состояния
    // (Offline/NotPaired) остаются — это setup, а не live-статус.
    val effective = if (!syncOn && status.isLivePhase()) ServerHubStatus.SyncOff else status
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = effective,
            // Переход только при смене ФАЗЫ; правки данных внутри Online обновляют тело на месте.
            contentKey = { it.phaseKey() },
            transitionSpec = {
                if (reducedMotion) {
                    (fadeIn(snap()) togetherWith fadeOut(snap()))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                } else {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(120)))
                        .using(
                            SizeTransform(clip = false) { _, _ ->
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            }
                        )
                }
            },
            label = "hub_status",
            modifier = Modifier.fillMaxWidth()
        ) { s ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (s) {
                    is ServerHubStatus.Online -> OnlineContent(s)
                    ServerHubStatus.Connecting -> BusyContent(stringResource(R.string.pill_connecting))
                    is ServerHubStatus.Working -> BusyContent(s.action)
                    ServerHubStatus.Failed -> FailedContent(onRetry)
                    ServerHubStatus.Offline -> OfflineContent(onActivate)
                    ServerHubStatus.NotPaired -> NotPairedContent(onConfigureSsh)
                    ServerHubStatus.SyncOff -> StatusHero(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.hub_sync_off)
                    )
                }
            }
        }
    }
}

/** Стабильный ключ фазы — AnimatedContent анимирует только при его смене. */
private fun ServerHubStatus.phaseKey(): Int = when (this) {
    ServerHubStatus.Offline -> 0
    ServerHubStatus.NotPaired -> 1
    ServerHubStatus.Connecting -> 2
    is ServerHubStatus.Working -> 3
    is ServerHubStatus.Online -> 4
    ServerHubStatus.Failed -> 5
    ServerHubStatus.SyncOff -> 6
}

/** Live-фазы зависят от живого SSH/ядра — при sync OFF схлопываются в [ServerHubStatus.SyncOff]. */
private fun ServerHubStatus.isLivePhase(): Boolean = when (this) {
    is ServerHubStatus.Online, ServerHubStatus.Connecting,
    is ServerHubStatus.Working, ServerHubStatus.Failed -> true
    else -> false
}

/**
 * Hero-строка статуса: живой индикатор-«ореол» слева + заголовок фазы и опц. подзаголовок.
 * Статус озвучивается один раз тут (никакого дублирующего чипа). [liveRegion] — TalkBack
 * объявляет смену фазы. Заголовок onSurface (читаемость), цвет несёт индикатор.
 */
@Composable
private fun StatusHero(
    color: Color,
    title: String,
    subtitle: String? = null,
    pulsing: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        StatusIndicator(color, pulsing)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Цветная точка-«ореол» статуса. В busy-фазах ореол мягко пульсирует (reduced-motion → статично). */
@Composable
private fun StatusIndicator(color: Color, pulsing: Boolean) {
    val reducedMotion = LocalReducedMotion.current
    val active = pulsing && !reducedMotion
    val t = rememberInfiniteTransition(label = "halo")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "halo_phase"
    )
    val haloAlpha = if (active) lerp(0.06f, 0.30f, phase) else 0.16f
    val haloScale = if (active) lerp(0.80f, 1.18f, phase) else 1f
    val animatedColor by animateColorAsState(color, label = "indicator_color")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale }
                .background(animatedColor.copy(alpha = haloAlpha), CircleShape)
        )
        Box(modifier = Modifier.size(12.dp).background(animatedColor, CircleShape))
    }
}

/** Busy-фаза (подключение/серверное действие): hero с пульсом + тонкий wavy-индикатор. */
@Composable
private fun BusyContent(title: String) {
    StatusHero(
        color = MaterialTheme.colorScheme.secondary,
        title = title,
        pulsing = true
    )
    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
}

/**
 * Живое ядро: hero несёт главный статус ОДИН раз (работает/остановлен/не установлено).
 * Детальные теги (SSH, режим, обфускация, версия) тут не дублируем — они живут в
 * «Отладочной информации» (NerdScreen → «Состояние ядра»).
 */
@Composable
private fun OnlineContent(status: ServerHubStatus.Online) {
    val ext = MaterialTheme.extendedColorScheme
    val (color, title) = when {
        !status.installed -> ext.warning to stringResource(R.string.hub_not_installed)
        status.running -> ext.success to stringResource(R.string.hub_server_running)
        else -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.hub_server_stopped)
    }
    StatusHero(color = color, title = title)
}

/**
 * Ошибка подключения/команды — hero + «Переподключиться». Конкретную причину НЕ показываем:
 * это внутренняя java/SSH-ошибка, а не серверная — юзеру бесполезна.
 */
@Composable
private fun FailedContent(onRetry: () -> Unit) {
    StatusHero(
        color = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.hub_connect_failed)
    )
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.reconnect))
    }
}

/** Неактивный профиль — hero + «Сделать активным». Адреса не дублируем: они в шапке хаба. */
@Composable
private fun OfflineContent(onActivate: () -> Unit) {
    StatusHero(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(R.string.pill_offline),
        subtitle = stringResource(R.string.server_inactive_desc)
    )
    Button(onClick = onActivate, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.make_active))
    }
}

/** SSH не настроен — hero + «Настроить подключение» (открывает экран SSH-сетапа). */
@Composable
private fun NotPairedContent(onConfigureSsh: () -> Unit) {
    StatusHero(
        color = MaterialTheme.extendedColorScheme.warning,
        title = stringResource(R.string.pill_not_paired),
        subtitle = stringResource(R.string.not_paired_hint)
    )
    Button(onClick = onConfigureSsh, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Icon(painterResource(R.drawable.host_24px), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.configure_ssh))
    }
}

/** Тег релиза приходит и как "1.0.3", и как "v1.0.3" — нормализуем без "vv". */
private fun versionLabel(version: String): String = "v${version.removePrefix("v")}"

/**
 * «Отладочная информация» — отдельный экран (вход из хаба, гейт по nerdMode): отладочные
 * per-profile флаги (подробные логи, показ логов) + состояние ядра + журнал сервера
 * и SSH-лог. Потоки логов собираем только здесь — хаб на них не подписан.
 */
@Composable
fun NerdScreen(
    profileId: String,
    settingsViewModel: SettingsViewModel,
    serverViewModel: ServerViewModel,
    onBack: () -> Unit
) {
    val snapshot by settingsViewModel.profilesSnapshot.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val coreStatus by serverViewModel.hubStatus.collectAsStateWithLifecycle()
    val profile = snapshot.list.firstOrNull { it.id == profileId }
    val isActive = snapshot.activeId == profileId

    // Профиль удалён — выходим назад (как в хабе).
    if (snapshot.loaded && profile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // hubStatus принадлежит активному серверу — для неактивного живого статуса нет.
    val online = if (isActive) coreStatus as? ServerHubStatus.Online else null

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nerd_section_title)) },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior
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
                if (profile != null) {
                    NerdContent(
                        profile = profile,
                        online = online,
                        privacyMode = privacyMode,
                        settingsViewModel = settingsViewModel,
                        serverViewModel = serverViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun NerdContent(
    profile: Profile,
    online: ServerHubStatus.Online?,
    privacyMode: Boolean,
    settingsViewModel: SettingsViewModel,
    serverViewModel: ServerViewModel
) {
    val context = LocalContext.current
    val profileId = profile.id
    val client = profile.client
    val sshLog by serverViewModel.sshLog.collectAsStateWithLifecycle()
    val journalLoading by serverViewModel.journalLoading.collectAsStateWithLifecycle()

    // Per-profile отладочные флаги. updateProfileClient разводит active/inactive и
    // применяет logsEnabled живьём — отдельные VM-сеттеры не нужны.
    SettingsGroup {
        SettingsGroupItem(0, 2) {
            SettingsSwitchRow(
                title = stringResource(R.string.debug_mode),
                subtitle = stringResource(R.string.debug_mode_desc),
                checked = client.debugMode,
                onCheckedChange = { v ->
                    settingsViewModel.updateProfileClient(profileId) { it.copy(debugMode = v) }
                }
            )
        }
        SettingsGroupItem(1, 2) {
            SettingsSwitchRow(
                title = stringResource(R.string.logs_enabled),
                subtitle = stringResource(R.string.logs_enabled_desc),
                checked = client.logsEnabled,
                onCheckedChange = { v ->
                    settingsViewModel.updateProfileClient(profileId) { it.copy(logsEnabled = v) }
                }
            )
        }
    }

    // Живое состояние ядра — только при живом SSH (online != null).
    if (online != null) CoreStateCard(online, privacyMode)

    // Параметры запуска реконструируются из конфига — видны всегда, даже оффлайн.
    LaunchParamsCard(profile, privacyMode)

    // Единый SSH-лог: копит весь вывод команд (включая ошибки сопряжения и server.log,
    // который тянется кнопкой ниже). Показываем при живом SSH (нужна кнопка журнала)
    // либо если в логе уже что-то есть.
    if (online != null || sshLog.isNotEmpty()) {
        SshLogCard(
            lines = sshLog,
            canFetchJournal = online != null,
            journalLoading = journalLoading,
            onFetchJournal = {
                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                serverViewModel.fetchServerLogs()
            },
            onClear = {
                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                serverViewModel.clearSshLog()
            }
        )
    }
}

/** Сырое состояние ядра: подписанные строки «ключ — значение» вместо безымянных чипов. */
@Composable
private fun CoreStateCard(online: ServerHubStatus.Online, privacyMode: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.nerd_core_state), style = MaterialTheme.typography.titleMedium)
            // Одно логичное состояние: «работает» уже подразумевает «установлено».
            // Не установлено → остановлен → работает.
            val stateRes = when {
                !online.installed -> R.string.nerd_state_not_installed
                !online.running -> R.string.nerd_state_stopped
                else -> R.string.nerd_state_running
            }
            NerdStateRow(stringResource(R.string.nerd_state_label), stringResource(stateRes))
            online.version?.takeIf { it.isNotBlank() }?.let {
                NerdStateRow(stringResource(R.string.nerd_version_label), versionLabel(it), mono = true)
            }
            NerdStateRow(stringResource(R.string.profile_has_ssh), online.sshIp.redact(privacyMode), mono = true)
        }
    }
}

@Composable
private fun NerdStateRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

/**
 * Параметры запуска ядра — реальные argv, которыми стартуют серверный и клиентский
 * бинарники. Реконструируются из конфига (тот же [CoreArgs.client], что и в движке;
 * серверный — через [ServerCommand]), поэтому видны всегда, даже без живого SSH.
 * Секреты (obf-ключ, vk-ссылка, адрес пира/TURN) маскируются под privacyMode.
 */
@Composable
private fun LaunchParamsCard(profile: Profile, privacyMode: Boolean) {
    val serverCmd = remember(profile, privacyMode) { serverCommandLine(profile, privacyMode) }
    val clientCmd = remember(profile, privacyMode) { clientCommandLine(profile, privacyMode) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.nerd_launch_params), style = MaterialTheme.typography.titleMedium)
            LaunchParamBlock(stringResource(R.string.nerd_launch_server), serverCmd)
            LaunchParamBlock(stringResource(R.string.nerd_launch_client), clientCmd)
        }
    }
}

@Composable
private fun LaunchParamBlock(label: String, commandLine: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LogPane(commandLine)
    }
}

/** Флаги клиента (-flag value), значение которых прячем под privacyMode. */
private val CLIENT_SECRET_FLAGS = setOf("-peer", "-link", "-obf-key", "-turn")

/** Командная строка клиентского ядра (как в движке) с маской секретов. */
private fun clientCommandLine(profile: Profile, privacy: Boolean): String {
    val argv = CoreArgs.client(profile.client, profile.server)
    val sb = StringBuilder("freeturn")
    var i = 0
    while (i < argv.size) {
        val tok = argv[i]
        sb.append(' ').append(tok)
        if (tok in CLIENT_SECRET_FLAGS && i + 1 < argv.size) {
            sb.append(' ').append(argv[i + 1].redact(privacy))
            i += 2
        } else {
            i += 1
        }
    }
    return sb.toString()
}

/** Командная строка серверного ядра (как уходит по SSH в free-turn-control.sh). */
private fun serverCommandLine(profile: Profile, privacy: Boolean): String {
    val opts = ServerOptions(
        listen = profile.proxyListen,
        connect = profile.proxyConnect,
        tcpMode = profile.client.tcpForward,
        obfProfile = if (profile.server.obfEnabled) profile.server.obfProfile else ObfProfile.NONE,
        obfKey = if (profile.server.obfEnabled) profile.server.obfKey else ""
    )
    // Серверные флаги в форме --flag=value: маскируем хвост после '=' у секретов.
    val shown = ServerCommand.Start(opts).toArgv().joinToString(" ") { tok ->
        val eq = tok.indexOf('=')
        if (eq > 0 && tok.substring(0, eq) == "--obf-key")
            tok.substring(0, eq + 1) + tok.substring(eq + 1).redact(privacy)
        else tok
    }
    return "free-turn-control.sh $shown"
}

/** Тёмная моноширинная вставка лога внутри карточек nerd-секции. */
@Composable
private fun LogPane(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, autoScroll: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        val scroll = rememberScrollState()
        if (autoScroll) {
            LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(scroll)
                .padding(10.dp)
        )
    }
}

/**
 * Единый SSH-лог: весь вывод команд сопряжения/управления + server.log (тянется кнопкой
 * «Журнал сервера») — всё идёт сюда через runCmd.
 *
 * Раскладка трёхэтажная, чтобы ничего не толкалось в одной строке: шапка (заголовок +
 * счётчик строк), терминал, ряд действий во всю ширину. Терминал на inverseSurface —
 * настоящая тёмная консоль в светлой теме, с приглашением «❯» и мигающим курсором.
 */
@Composable
private fun SshLogCard(
    lines: List<String>,
    canFetchJournal: Boolean,
    journalLoading: Boolean,
    onFetchJournal: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.ssh_log_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (lines.isNotEmpty()) {
                    AnimatedContent(
                        targetState = lines.size,
                        transitionSpec = {
                            (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false))
                        },
                        label = "ssh_log_count"
                    ) { count ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            SshTerminalPane(lines)

            // Действия отдельным рядом во всю ширину — в шапке им тесно на узких экранах.
            if (canFetchJournal || lines.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (canFetchJournal) {
                        FilledTonalButton(
                            onClick = onFetchJournal,
                            enabled = !journalLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (journalLoading) {
                                LoadingIndicator(modifier = Modifier.size(22.dp))
                            } else {
                                Icon(
                                    painterResource(R.drawable.cloud_download_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ssh_log_fetch_journal), maxLines = 1)
                        }
                    }
                    if (lines.isNotEmpty()) {
                        if (canFetchJournal) {
                            FilledTonalIconButton(onClick = onClear) {
                                Icon(
                                    painterResource(R.drawable.delete_24px),
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        } else {
                            // Оффлайн: журнал не потянуть, очистка — единственное действие.
                            FilledTonalButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                                Icon(
                                    painterResource(R.drawable.delete_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Консоль SSH-лога: тёмная (inverseSurface) моноширинная панель с приглашением «❯» и
 * мигающим курсором в конце — пустой лог выглядит как ждущий терминал, а не дырка.
 * Автопрокрутка к свежим строкам.
 */
@Composable
private fun SshTerminalPane(lines: List<String>) {
    // Тональные роли M3 — как у LogPane соседних карточек: панель живёт в теме
    // (light/dark/dynamic color), консольность несут моно-шрифт, промпт и курсор.
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) { scroll.scrollTo(scroll.maxValue) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp, max = 400.dp)
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            if (lines.isEmpty()) {
                Text(
                    "# ${stringResource(R.string.ssh_log_empty)}",
                    style = mono,
                    color = fg.copy(alpha = 0.55f)
                )
            } else {
                // Склейка дорогая (кап 500 строк) — кэшируем по содержимому лога.
                val text = remember(lines) { lines.joinToString("\n") }
                Text(text, style = mono, color = fg)
            }
            Row {
                Text("❯ ", style = mono, color = accent)
                if (LocalReducedMotion.current) {
                    Text("▍", style = mono, color = accent)
                } else {
                    // Альфа в draw-фазе через graphicsLayer: чтение анимации в композиции
                    // рекомпозило бы строку на каждом кадре мигания.
                    val blink = rememberInfiniteTransition(label = "ssh_cursor")
                        .animateFloat(
                            initialValue = 1f,
                            targetValue = 0.1f,
                            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                            label = "ssh_cursor_alpha"
                        )
                    Text(
                        "▍",
                        style = mono,
                        color = accent,
                        modifier = Modifier.graphicsLayer { alpha = blink.value }
                    )
                }
            }
        }
    }
}

