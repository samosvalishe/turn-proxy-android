@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.DnsMode
import com.freeturn.app.data.SplitTunnelMode
import com.freeturn.app.data.TunnelRoute
import com.freeturn.app.data.TunnelTransport
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.viewmodel.MainViewModel
import com.freeturn.app.viewmodel.SshConnectionState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * @param showFinishButton  true — онбординг-флоу, показываем кнопку «Завершить».
 *                          false — вкладка, авто-сохранение без кнопки.
 * @param onFinish  вызывается после нажатия кнопки «Завершить» (только если showFinishButton=true).
 */
@Composable
fun ClientSetupScreen(
    viewModel: MainViewModel,
    showFinishButton: Boolean = false,
    onFinish: (() -> Unit)? = null
) {
    val saved by viewModel.clientConfig.collectAsStateWithLifecycle()
    val sshConfig by viewModel.sshConfig.collectAsStateWithLifecycle()
    val sshState by viewModel.sshState.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val serverOpts by viewModel.serverOpts.collectAsStateWithLifecycle()
    val proxyListen by viewModel.proxyListen.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val isRegeneratingWrapKey by viewModel.isRegeneratingWrapKey.collectAsStateWithLifecycle()

    val isSshConnected = sshState is SshConnectionState.Connected
    val serverKnown = serverState as? com.freeturn.app.viewmodel.ServerState.Known
    // В sync-режиме эффективное значение — реальное состояние сервера из probe
    // (если запущен), иначе сохранённое. В !sync режиме — всегда сохранённое:
    // серверная и клиентская стороны могут различаться, и UI отражает клиента.
    val syncOn = saved.syncServerSwitches
    val effectiveVlessMode = if (syncOn) serverKnown?.vlessMode ?: saved.vlessMode else saved.vlessMode
    val effectiveVlessBond = if (syncOn) serverKnown?.vlessBond ?: serverOpts.vlessBond else serverOpts.vlessBond
    val effectiveWrap      = if (syncOn) serverKnown?.wrap      ?: serverOpts.wrapEnabled else serverOpts.wrapEnabled

    val context = LocalContext.current

    var serverAddress by rememberSaveable(saved.serverAddress) { mutableStateOf(saved.serverAddress) }
    var vkLink       by rememberSaveable(saved.vkLink)         { mutableStateOf(saved.vkLink) }
    var threads      by rememberSaveable(saved.threads)        { mutableFloatStateOf(saved.threads.toFloat()) }
    var streamsPerCred by rememberSaveable(saved.streamsPerCred) { mutableFloatStateOf(saved.streamsPerCred.toFloat()) }
    var useUdp       by rememberSaveable(saved.useUdp)         { mutableStateOf(saved.useUdp) }
    var manualCaptcha by rememberSaveable(saved.manualCaptcha) { mutableStateOf(saved.manualCaptcha) }
    var useCarrierDns by rememberSaveable(saved.useCarrierDns) { mutableStateOf(saved.useCarrierDns) }
    var dnsMode by rememberSaveable(saved.dnsMode) { mutableStateOf(saved.dnsMode) }
    var forcePort443 by rememberSaveable(saved.forcePort443) { mutableStateOf(saved.forcePort443) }
    var localPort    by rememberSaveable(saved.localPort)      { mutableStateOf(saved.localPort) }

    var debugMode by rememberSaveable(saved.debugMode) { mutableStateOf(saved.debugMode) }
    var magicSwitch by rememberSaveable(saved.magicSwitch) { mutableStateOf(saved.magicSwitch) }
    var magicTurn by rememberSaveable(saved.magicTurn) { mutableStateOf(saved.magicTurn) }
    var wireGuardTunnelName by rememberSaveable(saved.wireGuardTunnelName) {
        mutableStateOf(saved.wireGuardTunnelName)
    }
    var wireGuardConfig by rememberSaveable(saved.wireGuardConfig) {
        mutableStateOf(saved.wireGuardConfig)
    }
    var xrayConfig by rememberSaveable(saved.xrayConfig) {
        mutableStateOf(saved.xrayConfig)
    }
    var xrayProfileName by rememberSaveable(saved.xrayProfileName) {
        mutableStateOf(saved.xrayProfileName)
    }
    var tunnelTransport by rememberSaveable(saved.tunnelTransport) {
        mutableStateOf(saved.tunnelTransport)
    }
    var tunnelRoute by rememberSaveable(saved.tunnelRoute) {
        mutableStateOf(saved.tunnelRoute)
    }
    var splitTunnelMode by rememberSaveable(saved.splitTunnelMode) {
        mutableStateOf(saved.splitTunnelMode)
    }
    var splitTunnelApps by rememberSaveable(saved.splitTunnelApps) {
        mutableStateOf(saved.splitTunnelApps)
    }
    var showSplitAppPicker by rememberSaveable { mutableStateOf(false) }
    var lastSliderInt by rememberSaveable { mutableIntStateOf(saved.threads) }
    var lastStreamsInt by rememberSaveable { mutableIntStateOf(saved.streamsPerCred) }

    // Автозаполнение адреса сервера из SSH-конфига если поле пустое
    LaunchedEffect(sshConfig.ip, proxyListen) {
        if (serverAddress.isBlank() && sshConfig.ip.isNotBlank()) {
            val port = proxyListen.substringAfterLast(":", "56000")
            serverAddress = "${sshConfig.ip}:$port"
        }
    }

    LaunchedEffect(tunnelRoute) {
        if (tunnelRoute == TunnelRoute.DIRECT_XRAY) {
            tunnelTransport = TunnelTransport.VLESS
        }
    }

    LaunchedEffect(saved) {
        serverAddress = saved.serverAddress
        vkLink = saved.vkLink
        threads = saved.threads.toFloat()
        streamsPerCred = saved.streamsPerCred.toFloat()
        useUdp = saved.useUdp
        manualCaptcha = saved.manualCaptcha
        useCarrierDns = saved.useCarrierDns
        dnsMode = saved.dnsMode
        forcePort443 = saved.forcePort443
        localPort = saved.localPort
        debugMode = saved.debugMode
        magicSwitch = saved.magicSwitch
        magicTurn = saved.magicTurn
        wireGuardTunnelName = saved.wireGuardTunnelName
        wireGuardConfig = saved.wireGuardConfig
        xrayConfig = saved.xrayConfig
        xrayProfileName = saved.xrayProfileName
        tunnelTransport = saved.tunnelTransport
        tunnelRoute = saved.tunnelRoute
        splitTunnelMode = saved.splitTunnelMode
        splitTunnelApps = saved.splitTunnelApps
        lastSliderInt = saved.threads
        lastStreamsInt = saved.streamsPerCred
    }

    // Авто-сохранение с дебаунсом 600 мс на каждое изменение поля.
    // vlessMode исключён — сохраняется через setVlessMode с автоперезапуском сервера.
    LaunchedEffect(
        serverAddress,
        vkLink,
        threads,
        streamsPerCred,
        useUdp,
        manualCaptcha,
        useCarrierDns,
        localPort,
        dnsMode,
        forcePort443,
        debugMode,
        magicSwitch,
        magicTurn,
        wireGuardTunnelName,
        wireGuardConfig,
        xrayConfig,
        xrayProfileName,
        tunnelTransport,
        tunnelRoute,
        splitTunnelMode,
        splitTunnelApps
    ) {
        delay(600)
        viewModel.saveClientConfig(
            ClientConfig(
                serverAddress = serverAddress.trim(),
                vkLink        = vkLink.trim(),
                threads       = threads.roundToInt(),
                streamsPerCred = streamsPerCred.roundToInt(),
                useUdp        = useUdp,
                manualCaptcha = manualCaptcha,
                localPort     = localPort.trim(),
                vlessMode     = saved.vlessMode,

                debugMode     = debugMode,
                useCarrierDns = useCarrierDns,
                dnsMode       = dnsMode,
                forcePort443  = forcePort443,
                syncServerSwitches = saved.syncServerSwitches,
                magicSwitch   = magicSwitch,
                magicTurn     = magicTurn.trim(),
                wireGuardEnabled = tunnelRoute == TunnelRoute.FREETURN &&
                    tunnelTransport == TunnelTransport.WIREGUARD,
                wireGuardConfig = wireGuardConfig.trim(),
                wireGuardTunnelName = wireGuardTunnelName.trim().ifBlank { "freeturn-wg" },
                xrayEnabled = tunnelRoute == TunnelRoute.DIRECT_XRAY ||
                    tunnelTransport == TunnelTransport.VLESS,
                xrayConfig = xrayConfig.trim(),
                xrayProfileName = xrayProfileName.trim(),
                tunnelTransport = tunnelTransport,
                splitTunnelMode = splitTunnelMode,
                splitTunnelApps = splitTunnelApps.trim(),
                tunnelRoute = tunnelRoute
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.client_title)) })
        },
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

                // Подключение
                Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = serverAddress.redact(privacyMode),
                    onValueChange = { if (!privacyMode) serverAddress = it },
                    label = { Text(stringResource(R.string.server_address_label)) },
                    placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = privacyMode,
                    supportingText = { Text(stringResource(R.string.server_address_support)) }
                )

                OutlinedTextField(
                    value = vkLink.redact(privacyMode),
                    onValueChange = { if (!privacyMode) vkLink = it },
                    label = { Text(stringResource(R.string.call_link_label)) },
                    placeholder = { Text(stringResource(R.string.call_link_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = privacyMode,
                    supportingText = { Text(stringResource(R.string.call_link_support)) }
                )

                OutlinedTextField(
                    value = localPort.redact(privacyMode),
                    onValueChange = { if (!privacyMode) localPort = it },
                    label = { Text(stringResource(R.string.local_listen_address)) },
                    placeholder = { Text(stringResource(R.string.local_listen_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = privacyMode,
                    supportingText = { Text(stringResource(R.string.local_listen_support)) }
                )

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Параметры
                Text(stringResource(R.string.parameters_title), style = MaterialTheme.typography.titleMedium)

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.threads_format, threads.roundToInt()), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.threads_recommendation),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Slider(
                        value = threads,
                        onValueChange = {
                            val newInt = it.roundToInt()
                            if (newInt != lastSliderInt) {
                                HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                                lastSliderInt = newInt
                            }
                            threads = it
                        },
                        valueRange = 1f..128f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.streams_per_cred_format, streamsPerCred.roundToInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.streams_per_cred_recommendation),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = streamsPerCred,
                        onValueChange = {
                            val v = it.roundToInt()
                            if (v != lastStreamsInt) {
                                HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                                lastStreamsInt = v
                            }
                            streamsPerCred = it
                        },
                        valueRange = 1f..50f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(stringResource(R.string.dns_mode_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.dns_mode_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val dnsOptions = listOf(
                        DnsMode.AUTO to stringResource(R.string.dns_mode_auto),
                        DnsMode.UDP to stringResource(R.string.dns_mode_udp),
                        DnsMode.DOH to stringResource(R.string.dns_mode_doh)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        dnsOptions.forEachIndexed { idx, (value, label) ->
                            SegmentedButton(
                                selected = dnsMode == value,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    dnsMode = value
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = dnsOptions.size)
                            ) { Text(label) }
                        }
                    }
                }

                if (!effectiveVlessMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text(stringResource(R.string.transport_protocol), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.transport_protocol_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !useUdp,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    useUdp = false
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text(stringResource(R.string.tcp)) }
                            SegmentedButton(
                                selected = useUdp,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    useUdp = true
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text(stringResource(R.string.udp)) }
                        }
                    }
                }



                SwitchRow(
                    label = stringResource(R.string.manual_captcha),
                    description = stringResource(R.string.manual_captcha_desc),
                    checked = manualCaptcha,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        manualCaptcha = it
                    }
                )

                SwitchRow(
                    label = stringResource(R.string.use_carrier_dns),
                    description = stringResource(R.string.use_carrier_dns_desc),
                    checked = useCarrierDns,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        useCarrierDns = it
                    }
                )

                SwitchRow(
                    label = stringResource(R.string.force_port_443),
                    description = stringResource(R.string.force_port_443_desc),
                    checked = forcePort443,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        forcePort443 = it
                    }
                )

                SwitchRow(
                    label = stringResource(R.string.debug_mode),
                    description = stringResource(R.string.debug_mode_desc),
                    checked = debugMode,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        debugMode = it
                    }
                )

                SwitchRow(
                    label = stringResource(R.string.magic_switch),
                    description = stringResource(R.string.magic_switch_desc),
                    checked = magicSwitch,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        magicSwitch = it
                    }
                )

                if (magicSwitch) {
                    OutlinedTextField(
                        value = magicTurn.redact(privacyMode),
                        onValueChange = { if (!privacyMode) magicTurn = it },
                        label = { Text(stringResource(R.string.magic_switch_address_label)) },
                        placeholder = { Text(stringResource(R.string.magic_switch_address_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = privacyMode,
                        supportingText = { Text(stringResource(R.string.magic_switch_address_support)) }
                    )
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                Text(stringResource(R.string.tunnel_route_title), style = MaterialTheme.typography.titleMedium)

                Text(
                    stringResource(R.string.tunnel_route_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tunnelRoute == TunnelRoute.FREETURN,
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                            tunnelRoute = TunnelRoute.FREETURN
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.tunnel_route_freeturn)) }
                    SegmentedButton(
                        selected = tunnelRoute == TunnelRoute.DIRECT_XRAY,
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                            tunnelRoute = TunnelRoute.DIRECT_XRAY
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.tunnel_route_direct_xray)) }
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                Text(stringResource(R.string.tunnel_transport_title), style = MaterialTheme.typography.titleMedium)

                Text(
                    if (tunnelRoute == TunnelRoute.DIRECT_XRAY) stringResource(R.string.xray_enabled_desc)
                    else stringResource(R.string.tunnel_transport_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (tunnelRoute == TunnelRoute.FREETURN) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = tunnelTransport == TunnelTransport.WIREGUARD,
                            onClick = {
                                HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                tunnelTransport = TunnelTransport.WIREGUARD
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(stringResource(R.string.transport_wireguard)) }
                        SegmentedButton(
                            selected = tunnelTransport == TunnelTransport.VLESS,
                            onClick = {
                                HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                tunnelTransport = TunnelTransport.VLESS
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(stringResource(R.string.transport_vless)) }
                    }
                }

                if (tunnelRoute == TunnelRoute.FREETURN &&
                    tunnelTransport == TunnelTransport.WIREGUARD) {
                    Text(stringResource(R.string.wireguard_section), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = wireGuardTunnelName.redact(privacyMode),
                        onValueChange = { if (!privacyMode) wireGuardTunnelName = it },
                        label = { Text(stringResource(R.string.wireguard_tunnel_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = privacyMode
                    )
                    OutlinedTextField(
                        value = wireGuardConfig.redact(privacyMode),
                        onValueChange = { if (!privacyMode) wireGuardConfig = it },
                        label = { Text(stringResource(R.string.wireguard_config_label)) },
                        placeholder = { Text(stringResource(R.string.wireguard_config_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8,
                        readOnly = privacyMode,
                        supportingText = { Text(stringResource(R.string.wireguard_config_support)) }
                    )
                    SplitTunnelSettings(
                        mode = splitTunnelMode,
                        apps = splitTunnelApps,
                        privacyMode = privacyMode,
                        xrayLimit = false,
                        onModeChange = { splitTunnelMode = it },
                        onAppsChange = { splitTunnelApps = it },
                        onPickApps = { showSplitAppPicker = true }
                    )
                }

                if (tunnelRoute == TunnelRoute.DIRECT_XRAY ||
                    tunnelTransport == TunnelTransport.VLESS) {
                    Text(stringResource(R.string.xray_section), style = MaterialTheme.typography.titleMedium)
                    if (xrayProfileName.isNotBlank()) {
                        Text(
                            stringResource(R.string.xray_profile_active, xrayProfileName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        stringResource(R.string.xray_enabled_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = xrayConfig.redact(privacyMode),
                        onValueChange = {
                            if (!privacyMode) {
                                xrayConfig = it
                                xrayProfileName = ""
                            }
                        },
                        label = { Text(stringResource(R.string.xray_config_label)) },
                        placeholder = { Text(stringResource(R.string.xray_config_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 10,
                        readOnly = privacyMode,
                        supportingText = { Text(stringResource(R.string.xray_config_support)) }
                    )
                    SplitTunnelSettings(
                        mode = splitTunnelMode,
                        apps = splitTunnelApps,
                        privacyMode = privacyMode,
                        xrayLimit = true,
                        onModeChange = { splitTunnelMode = it },
                        onAppsChange = { splitTunnelApps = it },
                        onPickApps = { showSplitAppPicker = true }
                    )
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                Text(
                    stringResource(R.string.server_sync_section),
                    style = MaterialTheme.typography.titleMedium
                )

                SwitchRow(
                    label = stringResource(R.string.sync_server_switches),
                    description = stringResource(R.string.sync_server_switches_desc),
                    checked = syncOn,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        viewModel.setSyncServerSwitches(it)
                    }
                )

                // В sync-режиме менять эти флаги без SSH опасно (рассинхрон).
                // В !sync — клиентские, SSH не нужен.
                val controlsEnabled = if (syncOn) isSshConnected else true
                val lockedHint = if (syncOn)
                    stringResource(R.string.locked_disconnect_hint) else null

                SwitchRow(
                    label = stringResource(R.string.client_kcp_fec),
                    description = lockedHint?.takeIf { !isSshConnected }
                        ?: stringResource(R.string.client_kcp_fec_desc),
                    checked = serverOpts.kcpFec,
                    enabled = controlsEnabled,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        viewModel.setServerKcpFec(it)
                    }
                )

                SwitchRow(
                    label = stringResource(R.string.vless_mode),
                    description = lockedHint?.takeIf { !isSshConnected }
                        ?: stringResource(R.string.vless_mode_desc),
                    checked = effectiveVlessMode,
                    enabled = controlsEnabled,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        viewModel.setVlessMode(it)
                    }
                )

                SwitchRow(
                    label = stringResource(R.string.client_vless_bond),
                    description = lockedHint?.takeIf { !isSshConnected }
                        ?: stringResource(R.string.client_vless_bond_desc),
                    checked = effectiveVlessBond,
                    enabled = controlsEnabled && effectiveVlessMode,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        viewModel.setServerVlessBond(it)
                    }
                )

                SwitchRow(
                    label = stringResource(R.string.client_wrap_enabled),
                    description = lockedHint?.takeIf { !isSshConnected }
                        ?: stringResource(R.string.client_wrap_desc),
                    checked = effectiveWrap,
                    enabled = controlsEnabled,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        viewModel.setServerWrapEnabled(it)
                    }
                )

                if (effectiveWrap) {
                    val wrapKeyRegex = remember { Regex("^[0-9a-fA-F]{64}$") }
                    // Локальный черновик: правки в TextField не дёргают saveServerOpts/restart
                    // на каждом keystroke. Применение — отдельной кнопкой ниже, только когда
                    // ключ валидный (64 hex). Серверный ключ из serverOpts — источник истины:
                    // при внешнем обновлении (regen/probe) черновик пересинхронизируется.
                    var wrapKeyDraft by rememberSaveable(serverOpts.wrapKey) {
                        mutableStateOf(serverOpts.wrapKey)
                    }
                    val draftValid = wrapKeyDraft.matches(wrapKeyRegex)
                    val draftDirty = wrapKeyDraft != serverOpts.wrapKey
                    OutlinedTextField(
                        value = if (privacyMode) serverOpts.wrapKey.redact(true) else wrapKeyDraft,
                        onValueChange = { if (!privacyMode) wrapKeyDraft = it },
                        label = { Text(stringResource(R.string.server_wrap_key_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = privacyMode,
                        singleLine = true,
                        isError = wrapKeyDraft.isNotBlank() && !draftValid,
                        trailingIcon = {
                            if (serverOpts.wrapKey.isNotBlank() && !privacyMode) {
                                IconButton(onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                    cm?.setPrimaryClip(
                                        android.content.ClipData.newPlainText("wrap-key", serverOpts.wrapKey)
                                    )
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.content_copy_24px),
                                        contentDescription = stringResource(R.string.copy)
                                    )
                                }
                            }
                        },
                        supportingText = {
                            when {
                                wrapKeyDraft.isBlank() -> Text(
                                    stringResource(R.string.wrap_key_empty_hint),
                                    color = MaterialTheme.colorScheme.error
                                )
                                !draftValid -> Text(
                                    stringResource(R.string.wrap_key_invalid_hint),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                    if (!privacyMode && draftDirty) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                viewModel.setWrapKey(wrapKeyDraft)
                            },
                            enabled = draftValid,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.wrap_key_apply))
                        }
                    }
                    if (isSshConnected) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                    viewModel.regenerateWrapKey()
                                },
                                enabled = !isRegeneratingWrapKey,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isRegeneratingWrapKey) {
                                    androidx.compose.material3.CircularWavyProgressIndicator(
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.server_wrap_regen_in_progress))
                                } else {
                                    Text(stringResource(R.string.server_wrap_regen))
                                }
                            }
                        }
                    }
                }

                // Кнопка «Завершить» — только в онбординг-флоу
                if (showFinishButton && onFinish != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serverAddress.isNotBlank() && vkLink.isNotBlank(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.finish_setup), style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSplitAppPicker) {
        SplitTunnelAppPickerDialog(
            selected = splitTunnelApps.toPackageSet(),
            onDismiss = { showSplitAppPicker = false },
            onApply = { selected ->
                splitTunnelApps = selected.sorted().joinToString("\n")
                showSplitAppPicker = false
            }
        )
    }
}

@Composable
private fun SplitTunnelSettings(
    mode: String,
    apps: String,
    privacyMode: Boolean,
    xrayLimit: Boolean,
    onModeChange: (String) -> Unit,
    onAppsChange: (String) -> Unit,
    onPickApps: () -> Unit
) {
    val context = LocalContext.current
    Text(stringResource(R.string.split_tunnel_title), style = MaterialTheme.typography.titleMedium)
    Text(
        if (xrayLimit) stringResource(R.string.split_tunnel_xray_limit)
        else stringResource(R.string.split_tunnel_desc),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val options = listOf(
        SplitTunnelMode.ALL to stringResource(R.string.split_tunnel_all),
        SplitTunnelMode.INCLUDE to stringResource(R.string.split_tunnel_include),
        SplitTunnelMode.EXCLUDE to stringResource(R.string.split_tunnel_exclude)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                    onModeChange(value)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) { Text(label) }
        }
    }
    if (mode != SplitTunnelMode.ALL) {
        Button(
            onClick = {
                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                onPickApps()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !privacyMode
        ) {
            Text(stringResource(R.string.split_tunnel_pick_apps))
        }
        OutlinedTextField(
            value = apps.redact(privacyMode),
            onValueChange = { if (!privacyMode) onAppsChange(it) },
            label = { Text(stringResource(R.string.split_tunnel_apps_label)) },
            placeholder = { Text(stringResource(R.string.split_tunnel_apps_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            readOnly = privacyMode,
            supportingText = { Text(stringResource(R.string.split_tunnel_apps_support)) }
        )
    }
}

@Composable
private fun SplitTunnelAppPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var selectedApps by remember(selected) { mutableStateOf(selected) }
    val apps = remember {
        context.installedInternetApps()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_tunnel_pick_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(apps) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = app.packageName in selectedApps,
                            onCheckedChange = { checked ->
                                selectedApps = if (checked) selectedApps + app.packageName
                                else selectedApps - app.packageName
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedApps) }) {
                Text(stringResource(R.string.split_tunnel_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private data class AppChoice(
    val label: String,
    val packageName: String
)

private fun android.content.Context.installedInternetApps(): List<AppChoice> {
    val pm = packageManager
    val flags = android.content.pm.PackageManager.GET_PERMISSIONS
    val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledPackages(flags)
    }
    return packages
        .asSequence()
        .filter { info ->
            info.packageName != packageName &&
                info.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
        }
        .map { info ->
            val appInfo = info.applicationInfo
            AppChoice(
                label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.packageName,
                packageName = info.packageName
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<AppChoice> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()
}

private fun String.toPackageSet(): Set<String> =
    split(',', '\n', ' ', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
