@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.TunnelTransport
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.ProxyState
import com.freeturn.app.viewmodel.ProxyViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран «Режим подключения»: явный переключатель Proxy / VPN (WireGuard). В VPN-режиме
 * — импорт .conf из файла, имя туннеля и split-tunnel модалкой (для активного сервера).
 * Режим хранится в [ClientConfig.tunnelTransport] (NONE = proxy, WIREGUARD = vpn).
 */
@Composable
fun ConnectionModeScreen(
    settingsViewModel: SettingsViewModel,
    proxyViewModel: ProxyViewModel,
    profileId: String? = null,
    onBack: (() -> Unit)? = null
) {
    val snapshot by settingsViewModel.profilesSnapshot.collectAsStateWithLifecycle()
    val legacyClient by settingsViewModel.clientConfig.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val proxyState by proxyViewModel.proxyState.collectAsStateWithLifecycle()

    val profile = profileId?.let { id -> snapshot.list.firstOrNull { it.id == id } }
    val saved = profile?.client ?: legacyClient
    val isActive = profileId == null || profileId == snapshot.activeId

    fun clientEdit(transform: (ClientConfig) -> ClientConfig) {
        if (profileId != null) {
            settingsViewModel.updateProfileClient(profileId, transform)
        } else {
            settingsViewModel.saveClientConfig(transform(settingsViewModel.clientConfig.value), snapshot.activeId)
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // userPickedVpn держит VPN-выбор на сессию экрана: пока конфиг пуст, транспорт при
    // чтении нормализуется в NONE (WIREGUARD без конфига = выключен, см. AppPreferences),
    // и без локального флага сегмент мигал бы обратно на Proxy. Сброс по saved.tunnelTransport.
    var userPickedVpn by remember(profileId, saved.tunnelTransport) { mutableStateOf<Boolean?>(null) }
    val isVpn = userPickedVpn ?: (saved.tunnelTransport == TunnelTransport.WIREGUARD)

    var wgConfig by remember(saved.wireGuardConfig) { mutableStateOf(saved.wireGuardConfig) }
    var wgName by remember(saved.wireGuardTunnelName) { mutableStateOf(saved.wireGuardTunnelName) }

    // Единая запись WG-настроек: транспорт всегда выставляется по [isVpn] вместе с конфигом.
    // Иначе segment-выбор VPN при пустом конфиге нормализуется в NONE, а последующая
    // загрузка .conf транспорт не переписывает — VPN тихо остаётся выключенным.
    fun persistWg(vpn: Boolean = isVpn) {
        clientEdit {
            it.copy(
                tunnelTransport = if (vpn) TunnelTransport.WIREGUARD else TunnelTransport.NONE,
                wireGuardConfig = wgConfig.trim(),
                wireGuardTunnelName = wgName.trim().ifBlank { TunnelTransport.DEFAULT_TUNNEL_NAME }
            )
        }
    }

    var showSplitSheet by rememberSaveable { mutableStateOf(false) }
    // Лист только для активного сервера — при потере активности скрываем.
    LaunchedEffect(isActive) { if (!isActive) showSplitSheet = false }

    // Дебаунс WG-полей 600 мс. Первый прогон (значения = saved) пропускаем, чтобы не писать
    // на входе. pendingSave → flush при выходе: dispose отменяет корутину и теряет правку.
    var wgDirty by remember(profileId) { mutableStateOf(false) }
    var pendingSave by remember(profileId) { mutableStateOf(false) }
    LaunchedEffect(wgConfig, wgName) {
        if (!wgDirty) { wgDirty = true; return@LaunchedEffect }
        pendingSave = true
        delay(600)
        persistWg()
        pendingSave = false
    }
    val flush by rememberUpdatedState { persistWg() }
    DisposableEffect(Unit) {
        onDispose { if (pendingSave) flush() }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (!text.isNullOrBlank()) {
                    wgConfig = text
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connection_mode_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                }
            )
        },
        // Всегда внутри NavigationSuite — нижний бар держит навбар-инсет сам.
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
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isVpn,
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                            userPickedVpn = false
                            persistWg(vpn = false)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.mode_proxy)) }
                    SegmentedButton(
                        selected = isVpn,
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                            userPickedVpn = true
                            persistWg(vpn = true)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.mode_vpn)) }
                }

                Text(
                    stringResource(if (isVpn) R.string.mode_vpn_desc else R.string.mode_proxy_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isVpn) {
                    val configLoaded = wgConfig.isNotBlank()

                    // --- Конфигурация туннеля ---
                    SectionLabel(stringResource(R.string.connection_config_section))
                    SettingsCard {
                        // Конфиг задаётся только файлом — вставка текстом давала тот же
                        // результат, а endpoint всё равно подменяется в рантайме.
                        SettingsEntryRow(
                            iconRes = R.drawable.cloud_download_24px,
                            title = stringResource(R.string.load_wg_conf),
                            trailingRes = if (configLoaded) R.drawable.check_circle_24px else null,
                            trailingTint = MaterialTheme.extendedColorScheme.success,
                            enabled = !privacyMode,
                            onClick = { filePicker.launch("*/*") }
                        )
                        SettingsRowDivider()
                        Box(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = wgName.redact(privacyMode),
                                onValueChange = { if (!privacyMode) wgName = it },
                                label = { Text(stringResource(R.string.wireguard_tunnel_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                readOnly = privacyMode
                            )
                        }
                    }

                    // --- Раздельное туннелирование (модалка как на главном) ---
                    SectionLabel(stringResource(R.string.split_tunnel_title))
                    SettingsCard {
                        SettingsEntryRow(
                            iconRes = R.drawable.mobile_24px,
                            title = stringResource(R.string.split_tunnel_title),
                            trailingRes = R.drawable.unfold_more_24px,
                            enabled = isActive,
                            onClick = { showSplitSheet = true }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Тот же лист split-tunnel, что на главном экране. Только для активного сервера —
    // живой прокси/состояние принадлежат ему.
    if (showSplitSheet && isActive) {
        SplitTunnelModal(
            settingsViewModel = settingsViewModel,
            mode = saved.splitTunnelMode,
            apps = saved.splitTunnelApps,
            locked = proxyState !is ProxyState.Idle && proxyState !is ProxyState.Error,
            onDismiss = { showSplitSheet = false },
            // Тот же фон листа, что на главном экране (HomeScreen.sheetColor).
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    }
}
