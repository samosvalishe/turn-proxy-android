@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.screens.servermanagement

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsControlLabel
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.util.redact

/**
 * Синхронные серверные настройки (apply-модель): проброс UDP/TCP, профиль обфускации и
 * obf-ключ (черновик). Регенерация/копирование ключа - через колбэки; рестарт случается
 * по общей кнопке "Применить" на экране.
 */
@Composable
internal fun ServerSyncCard(
    tcp: Boolean,
    onTcp: (Boolean) -> Unit,
    obfProfile: String,
    onObfProfile: (String) -> Unit,
    keyDraft: String,
    onKeyDraft: (String) -> Unit,
    savedObfKey: String,
    privacyMode: Boolean,
    onCopyKey: () -> Unit,
    onRegenKey: () -> Unit
) {
    SectionLabel(stringResource(R.string.server_sync_section))
    SettingsCard {
        // Проброс: UDP / TCP.
        SettingsFieldSlot {
            SettingsControlLabel(stringResource(R.string.tcp_forward_mode))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !tcp,
                    onClick = { onTcp(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.udp)) }
                SegmentedButton(
                    selected = tcp,
                    onClick = { onTcp(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.tcp)) }
            }
        }
        SettingsRowDivider()
        // Профиль обфускации.
        SettingsFieldSlot {
            SettingsControlLabel(stringResource(R.string.obf_profile_title))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ObfProfile.VALUES.forEachIndexed { idx, value ->
                    SegmentedButton(
                        selected = obfProfile == value,
                        onClick = { onObfProfile(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = ObfProfile.VALUES.size)
                    ) { Text(obfProfileLabel(value)) }
                }
            }
        }
        SettingsRowDivider()
        if (obfProfile != ObfProfile.NONE) {
            SettingsFieldSlot {
                OutlinedTextField(
                    value = if (privacyMode) savedObfKey.redact(true) else keyDraft,
                    onValueChange = { if (!privacyMode) onKeyDraft(it) },
                    label = { Text(stringResource(R.string.server_obf_key_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = privacyMode,
                    singleLine = true,
                    isError = keyDraft.isNotBlank() && !ObfProfile.isValidKey(keyDraft),
                    trailingIcon = {
                        if (savedObfKey.isNotBlank() && !privacyMode) {
                            IconButton(onClick = onCopyKey) {
                                Icon(
                                    painterResource(R.drawable.content_copy_24px),
                                    contentDescription = stringResource(R.string.copy)
                                )
                            }
                        }
                    },
                    supportingText = {
                        when {
                            keyDraft.isBlank() -> Text(stringResource(R.string.obf_key_empty_hint))
                            !ObfProfile.isValidKey(keyDraft) -> Text(
                                stringResource(R.string.obf_key_invalid_hint),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                if (!privacyMode) {
                    TextButton(
                        onClick = onRegenKey,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.obf_key_regen))
                    }
                }
            }
        } else {
            // obfProfile == NONE - подсказка выбрать профиль.
            SettingsFieldSlot {
                Text(
                    stringResource(R.string.obf_select_profile_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun obfProfileLabel(value: String): String = when (value) {
    ObfProfile.NONE -> stringResource(R.string.obf_none)
    ObfProfile.RTPOPUS -> stringResource(R.string.obf_rtpopus)
    else -> value
}
