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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
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
import com.freeturn.app.R
import com.freeturn.app.data.Profile
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.components.SettingsRowIcon
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.ui.util.hapticClickable
import com.freeturn.app.viewmodel.ServerHubStatus
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.SshConnectionState


/** Корневой экран настроек (нижнее меню). */
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onOpenServers: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SettingsCard {
                    SettingsEntryRow(
                        iconRes = R.drawable.database_24px,
                        title = stringResource(R.string.settings_servers),
                        subtitle = stringResource(R.string.settings_servers_desc),
                        onClick = onOpenServers
                    )
                }

                Spacer(Modifier.height(16.dp))

                SectionLabel(stringResource(R.string.backup_title))
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                val json = settingsViewModel.exportAllProfiles()
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_TEXT, json)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "FreeTurn Backup")
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(send, context.getString(R.string.backup_create))
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(painterResource(R.drawable.open_in_new_24px), null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.backup_create))
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                val clipText = clip?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (clipText != null) {
                                    val count = settingsViewModel.importProfiles(clipText)
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(
                                            if (count > 0) R.string.backup_restored_toast
                                            else R.string.backup_restore_failed,
                                            count
                                        ),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        R.string.backup_clipboard_empty,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(painterResource(R.drawable.content_copy_24px), null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.backup_restore))
                        }
                    }
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
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
        // Экран всегда внутри NavigationSuite — нижний бар сам держит навбар-инсет.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (snapshot.loaded && snapshot.list.isEmpty()) {
            EmptyServers(modifier = Modifier.fillMaxSize().padding(padding))
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
                SettingsCard {
                    ordered.forEachIndexed { index, p ->
                        if (index > 0) SettingsRowDivider()
                        ServerListRow(
                            profile = p,
                            isActive = snapshot.activeId == p.id,
                            privacyMode = privacyMode,
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
    onClick: () -> Unit
) {
    // Подзаголовок: адрес сервера + метка «SSH», если сопряжение настроено. Сам SSH-ip
    // не дублируем (он уже не несёт пользе в списке) — достаточно факта наличия.
    val sub = listOfNotNull(
        profile.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode),
        stringResource(R.string.profile_has_ssh).takeIf { profile.ssh.ip.isNotBlank() }
    ).joinToString(" · ").ifBlank { "—" }

    // «Активный» = выбранный профиль (а не статус SSH-подключения). Озвучиваем для TalkBack.
    val activeBadge = stringResource(R.string.profile_active_badge)
    val rowDesc = if (isActive) "${profile.name}, $activeBadge" else profile.name

    // Круглый аватар вместо Sunny-формы — нейтральнее, не выбивается в плотном списке.
    // Активный — primaryContainer, остальные — приглушённый surface-контейнер.
    val iconContainer = if (isActive) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val iconTint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hapticClickable(HapticUtil.Pattern.CLICK, onClick = onClick)
            .semantics { contentDescription = rowDesc }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.database_24px),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Тональный бейдж «Активный» вместо зелёного pill — читается как
                // «выбранный профиль», а не как живой статус подключения.
                if (isActive) ActiveBadge(activeBadge)
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painterResource(R.drawable.chevron_right_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveBadge(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
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
    onOpenServerSettings: (String) -> Unit
) {
    val context = LocalContext.current
    val snapshot by settingsViewModel.profilesSnapshot.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val sshState by serverViewModel.sshState.collectAsStateWithLifecycle()
    val sshConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()
    val coreStatus by serverViewModel.hubStatus.collectAsStateWithLifecycle()
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
        !isActive -> ServerHubStatus.Offline(
            serverAddress = profile?.client?.serverAddress?.takeIf { it.isNotBlank() }?.redact(privacyMode),
            sshIp = profile?.ssh?.ip?.takeIf { it.isNotBlank() }?.redact(privacyMode)
        )
        profile?.ssh?.ip.isNullOrBlank() -> ServerHubStatus.NotPaired
        else -> coreStatus
    }
    // Вход в «Настройки сервера» доступен только при живом ядре (Online).
    val connected = status is ServerHubStatus.Online

    // Best-effort авто-сопряжение активного сервера при входе (не дублируем на других экранах).
    LaunchedEffect(isActive, sshConfig.ip, sshState) {
        if (isActive && sshConfig.ip.isNotBlank() && sshState is SshConnectionState.Disconnected) {
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
                navigationIcon = { BackButton(onBack) },
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
                    ServerStatusCard(
                        status = status,
                        privacyMode = privacyMode,
                        onActivate = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            settingsViewModel.applyProfile(profileId)
                        },
                        onRetry = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            serverViewModel.reconnectSsh()
                        }
                    )

                    // Мастер-свитч синхронизации — перенесён из «Настроек сервера» в хаб.
                    SettingsCard {
                        SyncToggleRow(
                            checked = profile.client.syncServerSwitches,
                            onCheckedChange = { v ->
                                HapticUtil.perform(context, if (v) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                                if (isActive) settingsViewModel.setSyncServerSwitches(v)
                                else settingsViewModel.updateProfileClient(profileId) { it.copy(syncServerSwitches = v) }
                            }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.provider_vk_calls))
                SettingsCard {
                    SettingsEntryRow(
                        iconRes = R.drawable.mobile_24px,
                        title = stringResource(R.string.provider_connection_settings),
                        subtitle = stringResource(R.string.provider_connection_settings_desc),
                        onClick = { onOpenConnection(profileId) }
                    )
                    SettingsRowDivider()
                    SettingsEntryRow(
                        iconRes = R.drawable.wifi_24px,
                        title = stringResource(R.string.connection_mode_title),
                        subtitle = stringResource(R.string.provider_connection_mode_desc),
                        onClick = { onOpenConnectionMode(profileId) }
                    )
                    // «Настройки сервера» требуют живого SSH — показываем только при
                    // успешном подключении (best-effort коннект идёт при входе в хаб).
                    if (isActive && connected) {
                        SettingsRowDivider()
                        SettingsEntryRow(
                            iconRes = R.drawable.database_24px,
                            title = stringResource(R.string.provider_server_settings),
                            subtitle = stringResource(R.string.provider_server_settings_desc),
                            onClick = { onOpenServerSettings(profileId) }
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
 * дублирующего чипа; детали (SSH, режим, версия) — meta-чипы во [FlowRow], переносятся на
 * узком экране. Тело меняется одним [AnimatedContent] (size-spring, M3 expressive); фазы
 * холодного захода (disconnected/connecting/checking) свёрнуты в Connecting — один переход в Online.
 */
@Composable
private fun ServerStatusCard(
    status: ServerHubStatus,
    privacyMode: Boolean,
    onActivate: () -> Unit,
    onRetry: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = status,
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
                    is ServerHubStatus.Online -> OnlineContent(s, privacyMode)
                    ServerHubStatus.Connecting -> BusyContent(stringResource(R.string.pill_connecting))
                    is ServerHubStatus.Working -> BusyContent(s.action)
                    is ServerHubStatus.Failed -> FailedContent(s.message, onRetry)
                    is ServerHubStatus.Offline -> OfflineContent(s, onActivate)
                    ServerHubStatus.NotPaired -> StatusHero(
                        color = MaterialTheme.extendedColorScheme.warning,
                        title = stringResource(R.string.pill_not_paired),
                        subtitle = stringResource(R.string.not_paired_hint)
                    )
                }
            }
        }
    }
}

/** Стабильный ключ фазы — AnimatedContent анимирует только при его смене. */
private fun ServerHubStatus.phaseKey(): Int = when (this) {
    is ServerHubStatus.Offline -> 0
    ServerHubStatus.NotPaired -> 1
    ServerHubStatus.Connecting -> 2
    is ServerHubStatus.Working -> 3
    is ServerHubStatus.Online -> 4
    is ServerHubStatus.Failed -> 5
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

/** Компактный meta-чип детали (SSH/режим/версия). Переносится во FlowRow на узком экране. */
@Composable
private fun MetaChip(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
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
 * Живое ядро: hero несёт главный статус ОДИН раз (работает/остановлен/не установлено),
 * детали — meta-чипы во [FlowRow] (SSH, режим, обфускация, версия, стадия установки). Чипы
 * переносятся на узком экране, ничего не сплющивается.
 */
@Composable
private fun OnlineContent(status: ServerHubStatus.Online, privacyMode: Boolean) {
    val ext = MaterialTheme.extendedColorScheme
    val (color, title) = when {
        !status.installed -> ext.warning to stringResource(R.string.hub_not_installed)
        status.running -> ext.success to stringResource(R.string.hub_server_running)
        else -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.hub_server_stopped)
    }
    StatusHero(color = color, title = title)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        status.sshIp.redact(privacyMode).takeIf { it.isNotBlank() }?.let {
            MetaChip("SSH · $it", color = ext.info)
        }
        if (status.running) {
            MetaChip(if (status.tcpMode == true) stringResource(R.string.tcp) else stringResource(R.string.udp))
            if (status.obfProfile == "rtpopus") MetaChip(stringResource(R.string.obf_rtpopus))
        }
        if (status.installed && !status.version.isNullOrBlank()) {
            MetaChip("v${status.version}")
        }
        status.installStage?.let { stage ->
            val label = when (stage) {
                "cached" -> stringResource(R.string.server_install_cached)
                "downloaded" -> stringResource(R.string.server_install_downloaded)
                else -> stage
            }
            MetaChip(label, color = ext.info)
        }
    }
}

/** Ошибка подключения/команды — hero + причина + «Переподключиться». */
@Composable
private fun FailedContent(message: String?, onRetry: () -> Unit) {
    StatusHero(
        color = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.hub_connect_failed),
        subtitle = message
    )
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.reconnect))
    }
}

/** Неактивный профиль — hero + адреса (meta-чипы) + «Сделать активным». */
@Composable
private fun OfflineContent(status: ServerHubStatus.Offline, onActivate: () -> Unit) {
    StatusHero(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(R.string.pill_offline),
        subtitle = stringResource(R.string.server_inactive_desc)
    )
    if (status.serverAddress != null || status.sshIp != null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            status.serverAddress?.let { MetaChip(it) }
            status.sshIp?.let { MetaChip("SSH · $it") }
        }
    }
    Button(onClick = onActivate, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.make_active))
    }
}

/** Мастер-свитч синхронизации с сервером (в хабе). */
@Composable
private fun SyncToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsRowIcon(R.drawable.cached_24px)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.sync_server_switches), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.sync_server_switches_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painterResource(R.drawable.arrow_back_24px),
            contentDescription = stringResource(R.string.back)
        )
    }
}

@Composable
private fun EmptyServers(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(112.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painterResource(R.drawable.database_outlined_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_empty_servers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
