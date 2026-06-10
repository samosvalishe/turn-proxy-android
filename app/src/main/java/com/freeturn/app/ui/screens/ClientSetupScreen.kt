@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.DnsList
import com.freeturn.app.data.DnsMode
import com.freeturn.app.data.Provider
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsControlLabel
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.SettingsGroupItem
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.components.SettingsSwitchRow
import com.freeturn.app.ui.util.redact
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun ClientSetupScreen(
    settingsViewModel: SettingsViewModel,
    serverViewModel: ServerViewModel,
    // null = активный сервер; не-null = конкретный сервер по id (Settings-флоу).
    serverId: String? = null,
    onBack: (() -> Unit)? = null
) {
    val snapshot by settingsViewModel.serversSnapshot.collectAsStateWithLifecycle()
    val activeClient by settingsViewModel.clientConfig.collectAsStateWithLifecycle()
    val sshConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()
    val serverState by serverViewModel.serverState.collectAsStateWithLifecycle()
    val activeProxyListen by settingsViewModel.proxyListen.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()

    // Источник данных: конкретный сервер по id либо активный.
    val server = serverId?.let { id -> snapshot.list.firstOrNull { it.id == id } }
    val saved = server?.client ?: activeClient
    // Активный сервер рулит живым рантаймом: SSH-сессия, рестарты, sync с сервером.
    // Для неактивного редактируем только хранимые данные.
    val isActive = serverId == null || serverId == snapshot.activeId
    val effSshIp = server?.ssh?.ip ?: sshConfig.ip
    val effProxyListen = server?.proxyListen ?: activeProxyListen

    // Единая точка записи client-конфига: сервер by-id либо активный.
    fun clientEdit(transform: (ClientConfig) -> ClientConfig) {
        if (serverId != null) {
            settingsViewModel.updateServerClient(serverId, transform)
        } else {
            settingsViewModel.saveClientConfig(transform(settingsViewModel.clientConfig.value), snapshot.activeId)
        }
    }

    val serverKnown = serverState as? com.freeturn.app.viewmodel.ServerState.Known
    // TCP-форвард: в sync-режиме у активного сервера берём реальное состояние из probe
    // (если запущен), иначе — сохранённое значение клиента. Нужно лишь для показа Bond.
    val syncOn = saved.syncServerSwitches
    val effectiveTcpForward = if (isActive && syncOn && serverKnown?.running == true)
        serverKnown.tcpMode ?: saved.tcpForward else saved.tcpForward

    val context = LocalContext.current

    // remember, НЕ rememberSaveable: rememberSaveable восстановил бы stale-поля из
    // bundle при возврате на экран, и авто-сейв зеркалил бы их в чужой сервер.
    var serverAddress by remember(saved.serverAddress) { mutableStateOf(saved.serverAddress) }
    var vkLink       by remember(saved.vkLink)         { mutableStateOf(saved.vkLink) }
    var threads      by remember(saved.threads)        { mutableFloatStateOf(saved.threads.toFloat()) }
    var streamsPerCred by remember(saved.streamsPerCred) { mutableFloatStateOf(saved.streamsPerCred.toFloat()) }
    var localPort    by remember(saved.localPort)      { mutableStateOf(saved.localPort) }
    var magicTurn by remember(saved.magicTurn) { mutableStateOf(saved.magicTurn) }
    var customDns by remember(saved.customDns) { mutableStateOf(saved.customDns) }

    // Автозаполнение адреса сервера из SSH-конфига если поле пустое
    LaunchedEffect(effSshIp, effProxyListen) {
        if (serverAddress.isBlank() && effSshIp.isNotBlank()) {
            val port = effProxyListen.substringAfterLast(":", "56000")
            serverAddress = "$effSshIp:$port"
        }
    }

    // Авто-сохранение с дебаунсом 600 мс для текстовых полей и ползунков.
    // clientEdit пишет по id редактируемого сервера — смена активного за время
    // дебаунса не затирает чужой конфиг.
    LaunchedEffect(
        serverAddress, vkLink, threads, streamsPerCred, localPort, magicTurn, customDns
    ) {
        delay(600)
        clientEdit { current ->
            current.copy(
                serverAddress = serverAddress.trim(),
                vkLink        = vkLink.trim(),
                threads       = threads.roundToInt(),
                streamsPerCred = streamsPerCred.roundToInt(),
                localPort     = localPort.trim(),
                magicTurn     = magicTurn.trim(),
                customDns     = customDns.trim()
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.provider_connection_settings)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = if (onBack != null) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Подключение: адреса сервера/звонка/локальный ---
                SectionLabel(stringResource(R.string.connection_title))
                SettingsCard {
                    SettingsFieldSlot {
                        OutlinedTextField(
                            value = serverAddress.redact(privacyMode),
                            onValueChange = { if (!privacyMode) serverAddress = it },
                            label = { Text(stringResource(R.string.server_address_label)) },
                            placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = privacyMode,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            supportingText = { Text(stringResource(R.string.server_address_support)) }
                        )
                    }
                    if (saved.provider == Provider.VK) {
                        SettingsRowDivider()
                        SettingsFieldSlot {
                            OutlinedTextField(
                                value = vkLink.redact(privacyMode),
                                onValueChange = { if (!privacyMode) vkLink = it },
                                label = { Text(stringResource(R.string.call_link_label)) },
                                placeholder = { Text(stringResource(R.string.call_link_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                readOnly = privacyMode,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                supportingText = { Text(stringResource(R.string.call_link_support)) }
                            )
                        }
                    }
                    SettingsRowDivider()
                    SettingsFieldSlot {
                        OutlinedTextField(
                            value = localPort.redact(privacyMode),
                            onValueChange = { if (!privacyMode) localPort = it },
                            label = { Text(stringResource(R.string.local_listen_address)) },
                            placeholder = { Text(stringResource(R.string.local_listen_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = privacyMode,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            supportingText = { Text(stringResource(R.string.local_listen_support)) }
                        )
                    }
                }

                // --- Производительность: потоки и потоки-на-аккаунт ---
                SectionLabel(stringResource(R.string.client_section_performance))
                SettingsCard {
                    SettingsFieldSlot {
                        SliderRow(
                            valueLabel = stringResource(R.string.threads_format, threads.roundToInt()),
                            hint = stringResource(R.string.threads_recommendation),
                            value = threads,
                            valueRange = 1f..128f,
                            onValueChange = { threads = it },
                            context = context
                        )
                    }
                    SettingsRowDivider()
                    SettingsFieldSlot {
                        SliderRow(
                            valueLabel = stringResource(R.string.streams_per_cred_format, streamsPerCred.roundToInt()),
                            hint = stringResource(R.string.streams_per_cred_recommendation),
                            value = streamsPerCred,
                            valueRange = 1f..50f,
                            onValueChange = { streamsPerCred = it },
                            context = context
                        )
                    }
                }

                // --- DNS: режим резолвера, ручной список, DNS оператора ---
                SectionLabel(stringResource(R.string.client_section_dns))
                // Ручной DNS. UI и движок (ProxyService) нормализуют ввод одним DnsList:
                // свитч оператора гасится только когда ручной список РЕАЛЬНО применится,
                // невалидные токены подсвечиваются isError — поле не врёт про приоритет.
                val dnsEffective = DnsList.normalize(customDns)
                val dnsHasInvalid = DnsList.hasInvalidTokens(customDns)
                SettingsCard {
                    SettingsFieldSlot {
                        SettingsControlLabel(
                            title = stringResource(R.string.dns_mode_title),
                            desc = stringResource(R.string.dns_mode_desc)
                        )
                        val dnsOptions = listOf(
                            DnsMode.AUTO to stringResource(R.string.dns_mode_auto),
                            DnsMode.PLAIN to stringResource(R.string.dns_mode_udp),
                            DnsMode.DOH to stringResource(R.string.dns_mode_doh)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            dnsOptions.forEachIndexed { idx, (value, label) ->
                                SegmentedButton(
                                    selected = saved.dnsMode == value,
                                    onClick = {
                                        HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                        clientEdit { it.copy(dnsMode = value) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = dnsOptions.size)
                                ) { Text(label) }
                            }
                        }
                    }
                    SettingsRowDivider()
                    SettingsFieldSlot {
                        OutlinedTextField(
                            value = customDns,
                            onValueChange = { customDns = it },
                            label = { Text(stringResource(R.string.dns_custom_label)) },
                            placeholder = { Text("8.8.8.8, 1.1.1.1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = dnsHasInvalid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            supportingText = {
                                Text(stringResource(
                                    if (dnsHasInvalid) R.string.dns_custom_invalid else R.string.dns_custom_hint
                                ))
                            }
                        )
                    }
                    SettingsRowDivider()
                    // DNS оператора — ниже ручного ввода, т.к. ручной список имеет приоритет
                    // (см. ProxyService). При действующем ручном списке гасим свитч, чтобы
                    // не вводить в заблуждение «включён, но не действует».
                    SettingsSwitchRow(
                        title = stringResource(R.string.use_carrier_dns),
                        subtitle = if (dnsEffective.isEmpty()) stringResource(R.string.use_carrier_dns_desc)
                                   else stringResource(R.string.use_carrier_dns_overridden),
                        checked = saved.useCarrierDns,
                        enabled = dnsEffective.isEmpty(),
                        onCheckedChange = { v -> clientEdit { it.copy(useCarrierDns = v) } }
                    )
                }

                // --- Дополнительно: транспорт TURN + флаги ---
                SectionLabel(stringResource(R.string.client_section_advanced))
                // TURN-транспорт (-transport tcp|udp) ортогонален режиму туннеля.
                SettingsCard {
                    SettingsFieldSlot {
                        SettingsControlLabel(
                            title = stringResource(R.string.transport_protocol),
                            desc = stringResource(R.string.transport_protocol_desc)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !saved.useUdp,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    clientEdit { it.copy(useUdp = false) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text(stringResource(R.string.tcp)) }
                            SegmentedButton(
                                selected = saved.useUdp,
                                onClick = {
                                    HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                    clientEdit { it.copy(useUdp = true) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text(stringResource(R.string.udp)) }
                        }
                    }
                }

                // Капча + Bond — сегментированная группа свитчей.
                // Bond — client-only флаг (сервер детектит сам), только в TCP-режиме.
                val toggleCount = if (effectiveTcpForward) 2 else 1
                SettingsGroup {
                    SettingsGroupItem(0, toggleCount) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.manual_captcha),
                            subtitle = stringResource(R.string.manual_captcha_desc),
                            checked = saved.manualCaptcha,
                            onCheckedChange = { v -> clientEdit { it.copy(manualCaptcha = v) } }
                        )
                    }
                    if (effectiveTcpForward) {
                        SettingsGroupItem(1, toggleCount) {
                            SettingsSwitchRow(
                                title = stringResource(R.string.client_bond),
                                subtitle = stringResource(R.string.client_bond_desc),
                                checked = saved.bond,
                                // bond триггерит рестарт прокси только у активного; иначе пишем данные.
                                onCheckedChange = { v ->
                                    if (isActive) settingsViewModel.setBond(v) else clientEdit { it.copy(bond = v) }
                                }
                            )
                        }
                    }
                }

                // Альтернативный TURN-узел — свитч + адрес (раскрывается при включении).
                SettingsCard {
                    SettingsSwitchRow(
                        title = stringResource(R.string.magic_switch),
                        subtitle = stringResource(R.string.magic_switch_desc),
                        checked = saved.magicSwitch,
                        onCheckedChange = { v -> clientEdit { it.copy(magicSwitch = v) } }
                    )
                    if (saved.magicSwitch) {
                        SettingsRowDivider()
                        SettingsFieldSlot {
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
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Ползунок с подписью-значением и пояснением. Хаптик щёлкает на каждое целочисленное
 * деление (а не на каждый float-кадр) — состояние держится локально внутри строки.
 */
@Composable
private fun SliderRow(
    valueLabel: String,
    hint: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    context: android.content.Context
) {
    var lastInt by remember { mutableIntStateOf(value.roundToInt()) }
    SettingsControlLabel(title = valueLabel, desc = hint)
    Slider(
        value = value,
        onValueChange = {
            val newInt = it.roundToInt()
            if (newInt != lastInt) {
                HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                lastInt = newInt
            }
            onValueChange(it)
        },
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth()
    )
}
