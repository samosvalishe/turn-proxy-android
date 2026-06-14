@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.setup

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.server.SetupConfigDraft
import com.freeturn.app.ui.theme.Spacing

/**
 * Шаг 2 мастера - опросник: режим (VPN/Proxy), бэкенд (WG-детект/порт), внешний порт
 * turn-прокси, профиль обфускации и ссылка на звонок. Чистый компонент: state+колбэки.
 */
@Composable
fun SetupConfigStep(
    draft: SetupConfigDraft,
    wgDetectedPort: Int?,
    duplicateHost: Boolean,
    portsClash: Boolean,
    showErrors: Boolean,
    onDraftChange: (SetupConfigDraft) -> Unit,
    onRollListenPort: () -> Unit,
    onRollWgPort: () -> Unit,
    onLoadWgFile: () -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    // Высота карточки режима меняется при переключениях - сглаживаем expressive-спеком.
    val resizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val cardModifier =
        if (reducedMotion) Modifier else Modifier.animateContentSize(resizeSpec)

    if (duplicateHost) DuplicateHostPanel()

    // --- Название сервера ---
    SectionLabel(stringResource(R.string.server_name_label))
    SettingsCard {
        SettingsFieldSlot {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = { Text(stringResource(R.string.server_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                supportingText = { Text(stringResource(R.string.setup_name_hint)) }
            )
        }
    }

    // --- Режим работы + бэкенд ---
    SectionLabel(stringResource(R.string.setup_mode_section))
    SettingsCard(modifier = cardModifier) {
        SettingsFieldSlot {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = draft.vpnMode,
                    onClick = {
                        if (!draft.vpnMode) {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                            onDraftChange(draft.copy(vpnMode = true))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.mode_vpn)) }
                SegmentedButton(
                    selected = !draft.vpnMode,
                    onClick = {
                        if (draft.vpnMode) {
                            HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                            onDraftChange(draft.copy(vpnMode = false))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.mode_proxy)) }
            }
            Text(
                stringResource(
                    if (draft.vpnMode) R.string.setup_mode_vpn_desc
                    else R.string.setup_mode_proxy_desc
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsRowDivider()
        if (draft.vpnMode) {
            SettingsFieldSlot {
                // Источник конфига: настроить WG на сервере либо вставить готовый .conf.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !draft.wgCustomConf,
                        onClick = {
                            if (draft.wgCustomConf) {
                                HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                onDraftChange(draft.copy(wgCustomConf = false))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.setup_wg_source_auto)) }
                    SegmentedButton(
                        selected = draft.wgCustomConf,
                        onClick = {
                            if (!draft.wgCustomConf) {
                                HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                onDraftChange(draft.copy(wgCustomConf = true))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.setup_wg_source_custom)) }
                }
                if (draft.wgCustomConf) {
                    Text(
                        stringResource(R.string.setup_wg_custom_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (wgDetectedPort != null) {
                    // WG уже стоит - используем как бэкенд, ничего не ставим.
                    WgStatusPanel(
                        found = true,
                        title = stringResource(R.string.setup_wg_found),
                        desc = stringResource(R.string.setup_wg_found_desc, wgDetectedPort)
                    )
                } else {
                    WgStatusPanel(
                        found = false,
                        title = stringResource(R.string.setup_wg_missing),
                        desc = stringResource(R.string.setup_wg_missing_desc)
                    )
                    PortField(
                        value = draft.wgPort,
                        onValueChange = { onDraftChange(draft.copy(wgPort = it)) },
                        label = stringResource(R.string.setup_wg_port_label),
                        onRoll = onRollWgPort,
                        error = if (showErrors && !portOk(draft.wgPort)) {
                            stringResource(R.string.setup_port_invalid)
                        } else null
                    )
                }
            }
            if (draft.wgCustomConf) {
                SettingsRowDivider()
                // Та же строка загрузки .conf, что в "Режиме подключения".
                SettingsEntryRow(
                    iconRes = R.drawable.cloud_download_24px,
                    title = stringResource(R.string.load_wg_conf),
                    trailingRes = if (draft.wgConfText.isNotBlank()) R.drawable.check_circle_24px else null,
                    trailingTint = MaterialTheme.extendedColorScheme.success,
                    onClick = onLoadWgFile
                )
                SettingsRowDivider()
                SettingsFieldSlot {
                    OutlinedTextField(
                        value = draft.wgConfText,
                        onValueChange = { onDraftChange(draft.copy(wgConfText = it)) },
                        label = { Text(stringResource(R.string.setup_wg_conf_label)) },
                        placeholder = { Text(stringResource(R.string.setup_wg_conf_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        isError = showErrors && draft.wgConfText.isBlank(),
                        supportingText = if (showErrors && draft.wgConfText.isBlank()) {
                            { Text(stringResource(R.string.setup_field_required)) }
                        } else null,
                        maxLines = 10
                    )
                    BackendPortField(draft, showErrors, onDraftChange)
                }
            }
        } else {
            SettingsFieldSlot {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !draft.backendTcp,
                        onClick = {
                            if (draft.backendTcp) {
                                HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                onDraftChange(draft.copy(backendTcp = false))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.udp)) }
                    SegmentedButton(
                        selected = draft.backendTcp,
                        onClick = {
                            if (!draft.backendTcp) {
                                HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                onDraftChange(draft.copy(backendTcp = true))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.tcp)) }
                }
                Text(
                    stringResource(R.string.setup_backend_protocol_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BackendPortField(draft, showErrors, onDraftChange)
            }
        }
    }

    // --- Внешний порт turn-прокси ---
    SectionLabel(stringResource(R.string.setup_turn_section))
    SettingsCard {
        SettingsFieldSlot {
            PortField(
                value = draft.listenPort,
                onValueChange = { onDraftChange(draft.copy(listenPort = it)) },
                label = stringResource(R.string.listen_port),
                supporting = stringResource(R.string.setup_listen_port_desc),
                onRoll = onRollListenPort,
                // Конфликт портов показываем сразу, формат - после тапа по submit.
                error = when {
                    portsClash -> stringResource(R.string.setup_ports_clash)
                    showErrors && !portOk(draft.listenPort) ->
                        stringResource(R.string.setup_port_invalid)
                    else -> null
                }
            )
        }
    }

    // --- Обфускация: сегмент по всем профилям (расширяется вместе с ObfProfile.VALUES) ---
    SectionLabel(stringResource(R.string.obf_profile_title))
    SettingsCard {
        SettingsFieldSlot {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ObfProfile.VALUES.forEachIndexed { idx, value ->
                    SegmentedButton(
                        selected = draft.obfProfile == value,
                        onClick = {
                            if (draft.obfProfile != value) {
                                HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                                onDraftChange(draft.copy(obfProfile = value))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = ObfProfile.VALUES.size)
                    ) { Text(obfProfileLabel(value)) }
                }
            }
            Text(
                stringResource(
                    when (draft.obfProfile) {
                        ObfProfile.NONE -> R.string.setup_obf_hint_none
                        else -> R.string.setup_obf_hint_rtpopus
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // --- Ссылка на звонок ---
    SectionLabel(stringResource(R.string.provider_vk_calls))
    SettingsCard {
        SettingsFieldSlot {
            OutlinedTextField(
                value = draft.vkLink,
                onValueChange = { onDraftChange(draft.copy(vkLink = it)) },
                label = { Text(stringResource(R.string.call_link_label)) },
                placeholder = { Text(stringResource(R.string.call_link_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                supportingText = { Text(stringResource(R.string.setup_call_link_hint)) }
            )
        }
    }
}

private fun portOk(p: String): Boolean = p.toIntOrNull()?.let { it in 1..65535 } == true

/** Порт существующего бэкенда (127.0.0.1) - общее поле VPN-custom и proxy-режима. */
@Composable
private fun BackendPortField(
    draft: SetupConfigDraft,
    showErrors: Boolean,
    onDraftChange: (SetupConfigDraft) -> Unit
) {
    val invalid = showErrors && !portOk(draft.backendPort)
    OutlinedTextField(
        value = draft.backendPort,
        onValueChange = { v -> onDraftChange(draft.copy(backendPort = v.filter { it.isDigit() })) },
        label = { Text(stringResource(R.string.setup_backend_port_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = invalid,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        supportingText = {
            Text(stringResource(
                if (invalid) R.string.setup_port_invalid else R.string.setup_backend_port_desc
            ))
        }
    )
}

/** Предупреждение: сервер с таким IP уже есть - мастер добавит вторую запись. */
@Composable
private fun DuplicateHostPanel() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                painterResource(R.drawable.info_24px),
                contentDescription = null,
                tint = MaterialTheme.extendedColorScheme.warning,
                modifier = Modifier.size(20.dp)
            )
            Text(
                stringResource(R.string.setup_duplicate_host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Итог WG-детекта - внутренняя тональная панель (surfaceContainerHighest поверх
 * карточки): статусная иконка + заголовок и пояснение.
 */
@Composable
private fun WgStatusPanel(found: Boolean, title: String, desc: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                painterResource(if (found) R.drawable.check_circle_24px else R.drawable.cloud_download_24px),
                contentDescription = null,
                tint = if (found) MaterialTheme.extendedColorScheme.success
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Числовое поле порта с кнопкой "перегенерировать" (случайный из пула). */
@Composable
private fun PortField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onRoll: () -> Unit,
    supporting: String? = null,
    error: String? = null
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = {
                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                onRoll()
            }) {
                Icon(
                    painterResource(R.drawable.refresh_24px),
                    contentDescription = stringResource(R.string.setup_port_roll)
                )
            }
        }
    )
}

/** Подпись сегмента профиля: NONE - "Выкл", новые профили показываются именем. */
@Composable
private fun obfProfileLabel(value: String): String = when (value) {
    ObfProfile.NONE -> stringResource(R.string.obf_none)
    ObfProfile.RTPOPUS -> stringResource(R.string.obf_rtpopus)
    else -> value
}
