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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
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
    proxyViewModel: ProxyViewModel,
    onOpenLogs: () -> Unit,
    onOpenServerSettings: (String) -> Unit
) {
    val context = LocalContext.current
    val proxyState by proxyViewModel.proxyState.collectAsStateWithLifecycle()
    val connectedSince by proxyViewModel.connectedSince.collectAsStateWithLifecycle()
    val uptimeText = rememberProxyUptime(connectedSince)
    val clientConfig by settingsViewModel.clientConfig.collectAsStateWithLifecycle()
    val nerdMode by settingsViewModel.nerdMode.collectAsStateWithLifecycle()

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
    val showSplitSheet = rememberSaveable { mutableStateOf(false) }
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
            ServersSheetContent(
                settingsViewModel = settingsViewModel,
                snapshot = profilesSnapshot,
                privacyMode = privacyMode,
                onCollapse = {
                    scope.launch { sheetScaffoldState.bottomSheetState.partialExpand() }
                },
                onOpenServerSettings = { id ->
                    // Сворачиваем лист перед уходом в хаб — вернувшись, юзер видит главный
                    // экран, а не распахнутый список.
                    scope.launch { sheetScaffoldState.bottomSheetState.partialExpand() }
                    onOpenServerSettings(id)
                }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.turn_proxy_title)) },
                actions = {
                    // Вход в экран логов — при «Показывать логи» И включённом nerdMode.
                    // Обе галки обязаны быть видимыми одновременно: иначе выключение
                    // nerdMode оставляло бы кнопку без доступного тоггла её выключить.
                    if (clientConfig.logsEnabled && nerdMode) {
                        IconButton(onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            onOpenLogs()
                        }) {
                            Icon(
                                painterResource(R.drawable.terminal_24px),
                                contentDescription = stringResource(R.string.open_logs)
                            )
                        }
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

