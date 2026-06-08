@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.freeturn.app.data.SplitTunnelMode
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.ProxyState
import com.freeturn.app.viewmodel.UpdateState
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.ProxyViewModel

import androidx.core.net.toUri

@SuppressLint("BatteryLife")
@Composable
fun HomeScreen(
    settingsViewModel: SettingsViewModel,
    proxyViewModel: ProxyViewModel
) {
    val context = LocalContext.current
    val proxyState by proxyViewModel.proxyState.collectAsStateWithLifecycle()
    val connectedSince by proxyViewModel.connectedSince.collectAsStateWithLifecycle()
    val uptimeText = rememberProxyUptime(connectedSince)
    val clientConfig by settingsViewModel.clientConfig.collectAsStateWithLifecycle()

    // Запрос разрешений при первом открытии главного экрана
    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* пользователь закрыл диалог батареи — результат нас не интересует */ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // После диалога уведомлений — запрашиваем исключение из оптимизации батареи
        val pm = context.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            batteryOptLauncher.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(400) // даём экрану отрисоваться
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

        if (needsNotification) {
            // Запрашиваем нотификации; батарею запросим в callback выше
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Нотификации уже есть — сразу проверяем батарею
            val pm = context.getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                batteryOptLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                )
            }
        }
    }

    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val profilesSnapshot by settingsViewModel.profilesSnapshot.collectAsStateWithLifecycle()
    val showBottomSheet = rememberSaveable { mutableStateOf(false) }
    val showSplitSheet = rememberSaveable { mutableStateOf(false) }
    val bottomSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Standard bottom sheet: свёрнутый peek = карточка активного профиля,
    // тянется вверх до полного списка серверов. skipHiddenState — sheet всегда
    // виден, не прячется полностью.
    val sheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )
    // WireGuard-туннелю нужно согласие пользователя на VPN. После выдачи —
    // запускаем прокси (а ProxyService уже поднимет WG поверх него).
    val wireGuardPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (VpnService.prepare(context) == null) {
            proxyViewModel.startProxy()
        }
    }

    fun startProxyWithTunnel() {
        if (clientConfig.wireGuardActive) {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                wireGuardPermissionLauncher.launch(vpnIntent)
                return
            }
        }
        proxyViewModel.startProxy()
    }

    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetPeekHeight = 112.dp,
        sheetContainerColor = sheetColor,
        sheetContent = {
            ProfilesSheetContent(
                settingsViewModel = settingsViewModel,
                snapshot = profilesSnapshot,
                privacyMode = privacyMode,
                onCollapse = {
                    scope.launch { sheetScaffoldState.bottomSheetState.partialExpand() }
                }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.turn_proxy_title)) },
                actions = {
                    IconButton(onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        showBottomSheet.value = true
                    }) {
                        Icon(painterResource(R.drawable.info_24px), contentDescription = stringResource(R.string.info_desc))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProxyToggleButton(
                state = proxyState,
                onClick = {
                    when (proxyState) {
                        is ProxyState.Idle, is ProxyState.Error -> {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                            startProxyWithTunnel()
                        }
                        is ProxyState.Running, is ProxyState.Connecting, is ProxyState.Starting -> {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_OFF)
                            proxyViewModel.stopProxy()
                        }
                        else -> {}
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = when (val s = proxyState) {
                    is ProxyState.Running -> {
                        val base = stringResource(R.string.proxy_active)
                        val counts = if (s.total > 0) "${s.active}/${s.total}" else "${s.active}"
                        if (uptimeText != null) "$base — $counts · $uptimeText"
                        else "$base — $counts"
                    }
                    is ProxyState.Connecting -> {
                        val base = stringResource(R.string.proxy_connecting)
                        val counts = if (s.total > 0) " — ${s.active}/${s.total}" else ""
                        // Таймер всё ещё показываем: сессия не завершилась, просто
                        // потоки временно отвалились и ядро переподключается.
                        if (uptimeText != null) "$base$counts · $uptimeText" else "$base$counts"
                    }
                    is ProxyState.Starting -> stringResource(R.string.proxy_connecting)
                    is ProxyState.Error -> s.message
                    is ProxyState.CaptchaRequired -> stringResource(R.string.proxy_captcha_required)
                    else -> stringResource(R.string.proxy_press_to_start)
                },
                // "tnum" — tabular numbers: все цифры одинаковой ширины. Без него
                // тикающий таймер и меняющийся счётчик N/M сдвигают остальной текст.
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = when (proxyState) {
                    is ProxyState.Running -> MaterialTheme.extendedColorScheme.success
                    is ProxyState.Error -> MaterialTheme.colorScheme.error
                    is ProxyState.CaptchaRequired -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
        }

        // Ссылка-индикатор split-tunneling прямо над свёрнутым листом сервера.
        // Только в WG-режиме: без конфига WireGuard (proxy-режим) сплит не работает.
        if (clientConfig.wireGuardActive) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    showSplitSheet.value = true
                }
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (clientConfig.splitTunnelMode == SplitTunnelMode.ALL)
                    stringResource(R.string.split_tunnel_status_off)
                else stringResource(R.string.split_tunnel_status_on),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                painterResource(R.drawable.unfold_more_24px),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }
        }
    }

    if (showBottomSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet.value = false },
            sheetState = bottomSheetState,
            containerColor = sheetColor
        ) {
            InfoBottomSheet(
                settingsViewModel = settingsViewModel,
                containerColor = sheetColor,
                privacyMode = privacyMode,
                onPrivacyModeChange = { settingsViewModel.setPrivacyMode(it) }
            )
        }
    }

    if (showSplitSheet.value) {
        SplitTunnelModal(
            settingsViewModel = settingsViewModel,
            mode = clientConfig.splitTunnelMode,
            apps = clientConfig.splitTunnelApps,
            locked = proxyState !is ProxyState.Idle && proxyState !is ProxyState.Error,
            onDismiss = { showSplitSheet.value = false },
            containerColor = sheetColor
        )
    }

    UpdateDialogs(settingsViewModel)
}

// Диалоги обновления

@Suppress("AssignedValueIsNeverRead")
@Composable
private fun UpdateDialogs(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val updateState by settingsViewModel.updateState.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf(false) }

    when (val state = updateState) {
        is UpdateState.Available -> if (!dismissed) {
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text(stringResource(R.string.update_available_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.update_available, state.version))
                        if (state.changelog.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    state.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        dismissed = true
                        settingsViewModel.downloadUpdate()
                    }) { Text(stringResource(R.string.update_download)) }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = true }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.update_downloading, state.progress))
                        Spacer(Modifier.height(12.dp))
                        LinearWavyProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = { settingsViewModel.resetUpdateState() },
                title = { Text(stringResource(R.string.update_ready_title)) },
                text = { Text(stringResource(R.string.update_ready_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        settingsViewModel.installUpdate()
                    }) { Text(stringResource(R.string.update_install)) }
                },
                dismissButton = {
                    TextButton(onClick = { settingsViewModel.resetUpdateState() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        else -> {}
    }
}

// Кнопка прокси

@Composable
private fun ProxyToggleButton(state: ProxyState, onClick: () -> Unit) {
    val extended = MaterialTheme.extendedColorScheme
    val buttonLabel = when (state) {
        is ProxyState.Starting, is ProxyState.Connecting -> stringResource(R.string.proxy_connecting)
        is ProxyState.Running -> stringResource(R.string.proxy_active_stop)
        is ProxyState.Error -> stringResource(R.string.proxy_error_restart)
        else -> stringResource(R.string.start_proxy)
    }
    val containerColor by animateColorAsState(
        targetValue = when (state) {
            is ProxyState.Running -> extended.successContainer
            is ProxyState.Error -> MaterialTheme.colorScheme.errorContainer
            is ProxyState.Starting, is ProxyState.Connecting ->
                MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(500),
        label = "btn_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when (state) {
            is ProxyState.Running -> extended.onSuccessContainer
            is ProxyState.Error -> MaterialTheme.colorScheme.onErrorContainer
            is ProxyState.Starting, is ProxyState.Connecting ->
                MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(500),
        label = "btn_fg"
    )
    val scale by animateFloatAsState(
        targetValue = if (state is ProxyState.Starting || state is ProxyState.Connecting) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(148.dp)
            .scale(scale)
            .clip(CircleShape)
            .semantics { contentDescription = buttonLabel },
        shape = CircleShape,
        color = containerColor,
        tonalElevation = if (state is ProxyState.Running) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is ProxyState.Starting, is ProxyState.Connecting ->
                    CircularWavyProgressIndicator(color = contentColor)
                is ProxyState.Running -> Icon(
                    painterResource(R.drawable.check_circle_24px), stringResource(R.string.proxy_active_stop),
                    Modifier.size(52.dp), tint = contentColor
                )
                is ProxyState.Error -> Icon(
                    painterResource(R.drawable.error_24px), stringResource(R.string.proxy_error_restart),
                    Modifier.size(52.dp), tint = contentColor
                )
                else -> Icon(
                    painterResource(R.drawable.play_arrow_24px), stringResource(R.string.start_proxy),
                    Modifier.size(52.dp), tint = contentColor
                )
            }
        }
    }
}

// Bottom sheet

@Composable
private fun InfoBottomSheet(
    settingsViewModel: SettingsViewModel,
    containerColor: Color,
    privacyMode: Boolean,
    onPrivacyModeChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val dynamicTheme by settingsViewModel.dynamicTheme.collectAsStateWithLifecycle()
    val updateState by settingsViewModel.updateState.collectAsStateWithLifecycle()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" }
        catch (_: Exception) { "—" }
    }

    val listColors = ListItemDefaults.colors(containerColor = containerColor)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Обновление
        item {
            UpdateListItem(
                state = updateState,
                colors = listColors,
                onCheck = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    settingsViewModel.checkForUpdate()
                },
                onDownload = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    settingsViewModel.downloadUpdate()
                },
                onInstall = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    settingsViewModel.installUpdate()
                }
            )
        }

        item { HorizontalDivider() }

        // Ссылки
        item {
            RepoLinkItem(
                title = stringResource(R.string.android_client),
                subtitle = "samosvalishe/turn-proxy-android",
                url = "https://github.com/samosvalishe/turn-proxy-android",
                containerColor = containerColor,
                onHaptic = { HapticUtil.perform(context, HapticUtil.Pattern.SELECTION) },
                onOpen = { uriHandler.openUri(it) }
            )
        }

        item {
            RepoLinkItem(
                title = stringResource(R.string.proxy_core),
                subtitle = "samosvalishe/free-turn-proxy",
                url = "https://github.com/samosvalishe/free-turn-proxy",
                containerColor = containerColor,
                onHaptic = { HapticUtil.perform(context, HapticUtil.Pattern.SELECTION) },
                onOpen = { uriHandler.openUri(it) }
            )
        }

        item {
            RepoLinkItem(
                title = stringResource(R.string.tg_channel),
                subtitle = null,
                url = "https://t.me/+53nh4UNiSv5lNTgy",
                containerColor = containerColor,
                onHaptic = { HapticUtil.perform(context, HapticUtil.Pattern.SELECTION) },
                onOpen = { uriHandler.openUri(it) }
            )
        }

        item { HorizontalDivider() }

        // Настройки интерфейса
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.privacy_mode_title)) },
                supportingContent = { Text(stringResource(R.string.privacy_mode_desc)) },
                colors = listColors,
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = privacyMode,
                        onCheckedChange = {
                            HapticUtil.perform(
                                context,
                                if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF
                            )
                            onPrivacyModeChange(it)
                        }
                    )
                }
            )
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dynamic_theme_title)) },
                supportingContent = { Text(stringResource(R.string.dynamic_theme_desc)) },
                colors = listColors,
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = dynamicTheme,
                        onCheckedChange = {
                            HapticUtil.perform(
                                context,
                                if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF
                            )
                            settingsViewModel.setDynamicTheme(it)
                        }
                    )
                }
            )
        }

        item { HorizontalDivider() }

        // Резервное копирование
        var showBackupDialog by rememberSaveable { mutableStateOf(false) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_title)) },
                supportingContent = { Text(stringResource(R.string.backup_desc)) },
                colors = listColors,
                trailingContent = {
                    Icon(
                        painterResource(R.drawable.database_outlined_24px),
                        contentDescription = stringResource(R.string.backup_title),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    showBackupDialog = true
                }
            )
        }

        item { HorizontalDivider() }

        // Сброс
        item {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.reset_settings),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                colors = listColors,
                trailingContent = {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = stringResource(R.string.reset),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    showResetDialog = true
                }
            )
        }

        item {
            Text(
                text = "v$appVersion",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(R.string.backup_title)) },
            text = { Text(stringResource(R.string.backup_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackupDialog = false
                        val json = settingsViewModel.exportAllProfiles()
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(android.content.Intent.EXTRA_TEXT, json)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "FreeTurn Backup")
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(send, context.getString(R.string.backup_create))
                        )
                    }
                ) { Text(stringResource(R.string.backup_create)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackupDialog = false
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
                    }
                ) { Text(stringResource(R.string.backup_restore)) }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_all_settings_title)) },
            text = { Text(stringResource(R.string.reset_all_settings_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        settingsViewModel.resetAllSettings()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// Пункт обновления в bottom sheet

@Composable
private fun UpdateListItem(
    state: UpdateState,
    colors: ListItemColors,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val supportingText = when (state) {
        is UpdateState.Idle -> stringResource(R.string.update_tap_to_check)
        is UpdateState.Checking -> stringResource(R.string.update_checking)
        is UpdateState.Available -> stringResource(R.string.update_available, state.version)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.progress)
        is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready_desc_short)
        is UpdateState.NoUpdate -> stringResource(R.string.update_no_update)
        is UpdateState.Error -> stringResource(R.string.update_error, state.message)
    }

    ListItem(
        headlineContent = {
            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleSmall)
        },
        supportingContent = {
            Column {
                Text(
                    supportingText,
                    color = if (state is UpdateState.Error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state is UpdateState.Downloading) {
                    LinearWavyProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        },
        trailingContent = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download))
                }
                is UpdateState.ReadyToInstall -> TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.update_install))
                }
                is UpdateState.Idle, is UpdateState.NoUpdate, is UpdateState.Error ->
                    TextButton(onClick = onCheck) {
                        Text(stringResource(R.string.update_check))
                    }
                else -> {}
            }
        },
        colors = colors
    )
}

// Общие компоненты

@Composable
private fun RepoLinkItem(
    title: String,
    subtitle: String?,
    url: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onHaptic: () -> Unit,
    onOpen: (String) -> Unit
) {
    Surface(
        onClick = {
            onHaptic()
            onOpen(url)
        },
        color = containerColor
    ) {
        ListItem(
            headlineContent = { Text(title) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            supportingContent = if (subtitle != null) ({
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }) else null,
            trailingContent = {
                Icon(
                    painterResource(R.drawable.open_in_new_24px),
                    contentDescription = stringResource(R.string.btn_open),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}


/**
 * Прилипшая к низу M3 «карточка» — единственная точка входа в управление профилями.
 * Виден всегда: с сохранённым активным профилем показывает его имя, без — лейбл
 * «несохранённая конфигурация» с приглашением открыть sheet.
 */
internal fun String.redact(enabled: Boolean) = if (enabled) "••••••" else this

/**
 * Форматирует uptime прокси в «mm:ss» или «h:mm:ss», тикая раз в секунду.
 *
 * Источник времени — `SystemClock.elapsedRealtime()`, как и у `connectedSince`
 * в `ProxyServiceState`: это устойчиво к переводу системных часов (обычный
 * `System.currentTimeMillis()` при изменении времени показал бы отрицательные
 * или разорванные интервалы).
 *
 * Возвращает null, если прокси ни разу не подключался в текущей сессии.
 */
@Composable
private fun rememberProxyUptime(connectedSince: Long?): String? {
    if (connectedSince == null) return null
    // Тик состояния, принудительно переформатирующий строку раз в секунду.
    // Пересоздаётся при смене connectedSince (новая сессия).
    val tick = androidx.compose.runtime.produceState(initialValue = 0L, connectedSince) {
        while (true) {
            value = android.os.SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val now = tick.value.coerceAtLeast(connectedSince)
    val totalSec = ((now - connectedSince) / 1_000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    // Ведущий ноль у минут, чтобы строка не прыгала при переходе 9:59 → 10:00.
    // Вместе с fontFeatureSettings="tnum" у Text это даёт полностью стабильную
    // ширину в первый час работы. Смена ширины остаётся только на переходе
    // 59:59 → 1:00:00 (раз за сессию) и 9:59:59 → 10:00:00.
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

