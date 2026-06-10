@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.home

import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.SplitTunnelMode
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.screens.SplitTunnelModal
import com.freeturn.app.viewmodel.ProxyState
import com.freeturn.app.viewmodel.ProxyViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Главный экран — тонкий слой над ViewModel: собирает состояния, держит
 * системные ланчеры (VPN-согласие, стартовые разрешения) и раскладывает
 * чистые компоненты: [ConnectionHero], [SplitTunnelChip], [ServersSheetContent],
 * [UpdateDialogs]. Без единого сервера лист и тоггл скрыты — показывается
 * [HomeEmptyState] с CTA добавления.
 */
@Composable
fun HomeScreen(
    settingsViewModel: SettingsViewModel,
    proxyViewModel: ProxyViewModel,
    onOpenLogs: () -> Unit,
    onOpenServerSettings: (String) -> Unit,
    onAddServer: () -> Unit
) {
    val context = LocalContext.current
    val proxyState by proxyViewModel.proxyState.collectAsStateWithLifecycle()
    val connectedSince by proxyViewModel.connectedSince.collectAsStateWithLifecycle()
    val uptimeText = rememberProxyUptime(connectedSince)
    val clientConfig by settingsViewModel.clientConfig.collectAsStateWithLifecycle()
    val nerdMode by settingsViewModel.nerdMode.collectAsStateWithLifecycle()
    val updateState by settingsViewModel.updateState.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val serversSnapshot by settingsViewModel.serversSnapshot.collectAsStateWithLifecycle()

    RequestStartupPermissions()

    val showSplitSheet = rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Standard bottom sheet: свёрнутый peek = карточка активного сервера,
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
            val vpnIntent: Intent? = VpnService.prepare(context)
            if (vpnIntent != null) {
                wireGuardPermissionLauncher.launch(vpnIntent)
                return
            }
        }
        proxyViewModel.startProxy()
    }

    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    val topBar: @Composable () -> Unit = {
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
    }

    // Без серверов листу нечего показывать и запускать нечего: вместо
    // BottomSheetScaffold — обычный Scaffold с приглашением добавить сервер.
    // Пока снимок не загружен — пустое тело, чтобы ни sheet, ни empty-state
    // не мигали на старте.
    when {
        !serversSnapshot.loaded ->
            Scaffold(topBar = topBar, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding))
            }

        serversSnapshot.list.isEmpty() ->
            Scaffold(topBar = topBar, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                HomeEmptyState(
                    onAddServer = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        onAddServer()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

        else -> BottomSheetScaffold(
            scaffoldState = sheetScaffoldState,
            sheetPeekHeight = 112.dp,
            sheetContainerColor = sheetColor,
            sheetContent = {
                ServersSheetContent(
                    snapshot = serversSnapshot,
                    privacyMode = privacyMode,
                    onApplyServer = { id ->
                        settingsViewModel.applyServer(id)
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
            topBar = topBar,
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
                    ConnectionHero(
                        state = proxyState,
                        uptimeText = uptimeText,
                        onToggle = {
                            when (proxyState) {
                                is ProxyState.Idle, is ProxyState.Error -> {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    startProxyWithTunnel()
                                }
                                // CaptchaRequired: прокси под капчей работает — тоггл его останавливает.
                                is ProxyState.Running, is ProxyState.Connecting,
                                is ProxyState.Starting, is ProxyState.CaptchaRequired -> {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_OFF)
                                    proxyViewModel.stopProxy()
                                }
                            }
                        }
                    )
                }

                // Ссылка-индикатор split-tunneling прямо над свёрнутым листом сервера.
                // Только в WG-режиме: без конфига WireGuard (proxy-режим) сплит не работает.
                if (clientConfig.wireGuardActive) {
                    SplitTunnelChip(
                        splitActive = clientConfig.splitTunnelMode != SplitTunnelMode.ALL,
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            showSplitSheet.value = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
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

    UpdateDialogs(
        updateState = updateState,
        onDownload = settingsViewModel::downloadUpdate,
        onInstall = settingsViewModel::installUpdate,
        onReset = settingsViewModel::resetUpdateState
    )
}
