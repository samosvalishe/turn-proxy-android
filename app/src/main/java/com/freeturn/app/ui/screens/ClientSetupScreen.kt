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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.freeturn.app.data.ObfProfile
import com.freeturn.app.data.Provider
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.viewmodel.SettingsViewModel
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
    viewModel: SettingsViewModel,
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
    val isRegeneratingObfKey by viewModel.isRegeneratingObfKey.collectAsStateWithLifecycle()

    val isSshConnected = sshState is SshConnectionState.Connected
    val serverKnown = serverState as? com.freeturn.app.viewmodel.ServerState.Known
    // В sync-режиме эффективное значение — реальное состояние сервера из probe
    // (если запущен), иначе сохранённое. В !sync режиме — всегда сохранённое:
    // серверная и клиентская стороны могут различаться, и UI отражает клиента.
    val syncOn = saved.syncServerSwitches
    val effectiveTcpForward = if (syncOn) serverKnown?.tcpMode ?: saved.tcpForward else saved.tcpForward
    val effectiveObfProfile = if (syncOn) serverKnown?.obfProfile ?: serverOpts.obfProfile else serverOpts.obfProfile

    val context = LocalContext.current

    var serverAddress by rememberSaveable(saved.serverAddress) { mutableStateOf(saved.serverAddress) }
    var vkLink       by rememberSaveable(saved.vkLink)         { mutableStateOf(saved.vkLink) }
    var provider     by rememberSaveable(saved.provider)       { mutableStateOf(saved.provider) }
    var threads      by rememberSaveable(saved.threads)        { mutableFloatStateOf(saved.threads.toFloat()) }
    var streamsPerCred by rememberSaveable(saved.streamsPerCred) { mutableFloatStateOf(saved.streamsPerCred.toFloat()) }
    var useUdp       by rememberSaveable(saved.useUdp)         { mutableStateOf(saved.useUdp) }
    var manualCaptcha by rememberSaveable(saved.manualCaptcha) { mutableStateOf(saved.manualCaptcha) }
    var useCarrierDns by rememberSaveable(saved.useCarrierDns) { mutableStateOf(saved.useCarrierDns) }
    var dnsMode by rememberSaveable(saved.dnsMode) { mutableStateOf(saved.dnsMode) }
    var localPort    by rememberSaveable(saved.localPort)      { mutableStateOf(saved.localPort) }

    var debugMode by rememberSaveable(saved.debugMode) { mutableStateOf(saved.debugMode) }
    var magicSwitch by rememberSaveable(saved.magicSwitch) { mutableStateOf(saved.magicSwitch) }
    var magicTurn by rememberSaveable(saved.magicTurn) { mutableStateOf(saved.magicTurn) }
    var lastSliderInt by rememberSaveable { mutableIntStateOf(saved.threads) }
    var lastStreamsInt by rememberSaveable { mutableIntStateOf(saved.streamsPerCred) }

    // Автозаполнение адреса сервера из SSH-конфига если поле пустое
    LaunchedEffect(sshConfig.ip, proxyListen) {
        if (serverAddress.isBlank() && sshConfig.ip.isNotBlank()) {
            val port = proxyListen.substringAfterLast(":", "56000")
            serverAddress = "${sshConfig.ip}:$port"
        }
    }

    // Авто-сохранение с дебаунсом 600 мс на каждое изменение поля.
    // tcpForward/bond исключены — сохраняются через setTcpForward/setBond с автоперезапуском.
    LaunchedEffect(serverAddress, vkLink, provider, threads, streamsPerCred, useUdp, manualCaptcha, useCarrierDns, localPort, dnsMode, debugMode, magicSwitch, magicTurn) {
        delay(600)
        viewModel.saveClientConfig(
            ClientConfig(
                serverAddress = serverAddress.trim(),
                vkLink        = vkLink.trim(),
                provider      = provider,
                threads       = threads.roundToInt(),
                streamsPerCred = streamsPerCred.roundToInt(),
                useUdp        = useUdp,
                manualCaptcha = manualCaptcha,
                localPort     = localPort.trim(),
                tcpForward    = saved.tcpForward,
                bond          = saved.bond,

                debugMode     = debugMode,
                useCarrierDns = useCarrierDns,
                dnsMode       = dnsMode,
                syncServerSwitches = saved.syncServerSwitches,
                magicSwitch   = magicSwitch,
                magicTurn     = magicTurn.trim()
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

                // Провайдер TURN-creds (-provider). Пока только vk; список расширяемый.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.provider_title), style = MaterialTheme.typography.bodyMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        Provider.ALL.forEachIndexed { idx, value ->
                            SegmentedButton(
                                selected = provider == value,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    provider = value
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = Provider.ALL.size)
                            ) { Text(providerLabel(value)) }
                        }
                    }
                }

                if (provider == Provider.VK) {
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
                }

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
                        DnsMode.PLAIN to stringResource(R.string.dns_mode_udp),
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

                // TURN-транспорт (-transport tcp|udp) ортогонален режиму туннеля.
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
                    label = stringResource(R.string.tcp_forward_mode),
                    description = lockedHint?.takeIf { !isSshConnected }
                        ?: stringResource(R.string.tcp_forward_mode_desc),
                    checked = effectiveTcpForward,
                    enabled = controlsEnabled,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        viewModel.setTcpForward(it)
                    }
                )

                // Bond — client-only флаг (сервер детектит сам). SSH не нужен;
                // активен только в TCP-форвард режиме.
                SwitchRow(
                    label = stringResource(R.string.client_bond),
                    description = stringResource(R.string.client_bond_desc),
                    checked = saved.bond,
                    enabled = effectiveTcpForward,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        viewModel.setBond(it)
                    }
                )

                // Профиль обфускации — табы (как транспорт). Должен совпадать с сервером.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(stringResource(R.string.obf_profile_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            lockedHint?.takeIf { !isSshConnected }
                                ?: stringResource(R.string.obf_profile_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ObfProfile.ALL.forEachIndexed { idx, value ->
                            SegmentedButton(
                                selected = effectiveObfProfile == value,
                                enabled = controlsEnabled,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    viewModel.setObfProfile(value)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = ObfProfile.ALL.size)
                            ) { Text(obfProfileLabel(value)) }
                        }
                    }
                }

                if (effectiveObfProfile != ObfProfile.NONE) {
                    val obfKeyRegex = remember { Regex("^[0-9a-fA-F]{64}$") }
                    // Локальный черновик: правки в TextField не дёргают saveServerOpts/restart
                    // на каждом keystroke. Применение — отдельной кнопкой ниже, только когда
                    // ключ валидный (64 hex). Серверный ключ из serverOpts — источник истины:
                    // при внешнем обновлении (regen/probe) черновик пересинхронизируется.
                    var obfKeyDraft by rememberSaveable(serverOpts.obfKey) {
                        mutableStateOf(serverOpts.obfKey)
                    }
                    val draftValid = obfKeyDraft.matches(obfKeyRegex)
                    val draftDirty = obfKeyDraft != serverOpts.obfKey
                    OutlinedTextField(
                        value = if (privacyMode) serverOpts.obfKey.redact(true) else obfKeyDraft,
                        onValueChange = { if (!privacyMode) obfKeyDraft = it },
                        label = { Text(stringResource(R.string.server_obf_key_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = privacyMode,
                        singleLine = true,
                        isError = obfKeyDraft.isNotBlank() && !draftValid,
                        trailingIcon = {
                            if (serverOpts.obfKey.isNotBlank() && !privacyMode) {
                                IconButton(onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                    cm?.setPrimaryClip(
                                        android.content.ClipData.newPlainText("obf-key", serverOpts.obfKey)
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
                                obfKeyDraft.isBlank() -> Text(
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
                                viewModel.setObfKey(obfKeyDraft)
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
                                    viewModel.regenerateObfKey()
                                },
                                enabled = !isRegeneratingObfKey,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isRegeneratingObfKey) {
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
}

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

@Composable
private fun providerLabel(value: String): String = when (value) {
    Provider.VK -> stringResource(R.string.provider_vk)
    else -> value
}

@Composable
private fun obfProfileLabel(value: String): String = when (value) {
    ObfProfile.NONE -> stringResource(R.string.obf_none)
    ObfProfile.RTPOPUS -> stringResource(R.string.obf_rtpopus)
    else -> value
}
