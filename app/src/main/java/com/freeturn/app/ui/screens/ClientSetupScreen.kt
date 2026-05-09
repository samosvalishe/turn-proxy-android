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
    var localPort    by rememberSaveable(saved.localPort)      { mutableStateOf(saved.localPort) }
    var captchaSolver by rememberSaveable(saved.captchaSolver) { mutableStateOf(saved.captchaSolver) }
    var debugMode by rememberSaveable(saved.debugMode) { mutableStateOf(saved.debugMode) }
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
    // vlessMode исключён — сохраняется через setVlessMode с автоперезапуском сервера.
    LaunchedEffect(serverAddress, vkLink, threads, streamsPerCred, useUdp, manualCaptcha, useCarrierDns, localPort, captchaSolver, debugMode) {
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
                captchaSolver = captchaSolver,
                debugMode     = debugMode,
                useCarrierDns = useCarrierDns,
                syncServerSwitches = saved.syncServerSwitches
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(stringResource(R.string.captcha_solver_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.captcha_solver_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val solverOptions = listOf("v2", "v1")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        solverOptions.forEachIndexed { idx, value ->
                            SegmentedButton(
                                selected = captchaSolver == value,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    captchaSolver = value
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = solverOptions.size)
                            ) { Text(value) }
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
                    label = stringResource(R.string.debug_mode),
                    description = stringResource(R.string.debug_mode_desc),
                    checked = debugMode,
                    onCheckedChange = {
                        HapticUtil.perform(context, if (it) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF)
                        debugMode = it
                    }
                )

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
                    OutlinedTextField(
                        value = serverOpts.wrapKey.redact(privacyMode),
                        onValueChange = { /* read-only */ },
                        label = { Text(stringResource(R.string.server_wrap_key_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        singleLine = true,
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
                            if (serverOpts.wrapKey.isBlank()) {
                                Text(
                                    stringResource(R.string.wrap_key_empty_hint),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
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
