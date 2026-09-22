@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.screens.servermanagement

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.ui.components.ChoiceOption
import com.freeturn.app.ui.components.OptionDropdown
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.obfProfileLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsControlLabel
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.components.SettingsSliderRow
import com.freeturn.app.ui.components.UdpTcpSegmented
import com.freeturn.app.ui.util.redact
import kotlin.math.roundToInt

/**
 * Синхронные серверные настройки (apply-модель): проброс UDP/TCP, профиль обфускации и
 * obf-ключ (черновик). Регенерация/копирование ключа - через колбэки; рестарт случается
 * по общей кнопке "Применить" на экране.
 */
@Composable
internal fun ServerSyncCard(
    tcp: Boolean,
    onTcp: (Boolean) -> Unit,
    tcpBlocked: Boolean,
    obfProfile: String,
    onObfProfile: (String) -> Unit,
    keyDraft: String,
    onKeyDraft: (String) -> Unit,
    savedObfKey: String,
    timingMs: Int,
    onTimingMs: (Int) -> Unit,
    privacyMode: Boolean,
    onCopyKey: () -> Unit,
    onRegenKey: () -> Unit,
    onTick: () -> Unit
) {
    SectionLabel(stringResource(R.string.server_sync_section))
    SettingsCard {
        SettingsFieldSlot {
            UdpTcpSegmented(
                tcp = tcp,
                onTcp = onTcp,
                label = stringResource(R.string.tcp_forward_mode),
                // Ядро поднимает туннель только поверх udp-проброса.
                tcpDisabledReason = if (tcpBlocked) stringResource(R.string.tcp_blocked_by_tunnel) else null
            )
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            SettingsControlLabel(stringResource(R.string.obf_profile_title))
            OptionDropdown(
                label = stringResource(R.string.obf_profile_title),
                options = ObfProfile.VALUES.map { ChoiceOption(it, obfProfileLabel(it)) },
                selected = obfProfile,
                onSelect = onObfProfile
            )
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
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onCopyKey) {
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
                        shapes = ButtonDefaults.shapes(),
                        onClick = onRegenKey,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.obf_key_regen))
                    }
                }
            }
            SettingsRowDivider()
            SettingsFieldSlot {
                SettingsSliderRow(
                    valueLabel = if (timingMs == ObfProfile.TIMING_OFF) {
                        stringResource(R.string.obf_timing_off)
                    } else {
                        stringResource(R.string.obf_timing_format, timingMs)
                    },
                    hint = stringResource(R.string.obf_timing_hint),
                    value = timingMs.toFloat(),
                    valueRange = 0f..ObfProfile.TIMING_MAX.toFloat(),
                    // Шаг 5 мс: разницу в 1 мс на глаз не отличить, а слайдер дёрганый.
                    onValueChange = { v ->
                        val step = ObfProfile.TIMING_STEP
                        onTimingMs((v / step).roundToInt() * step)
                    },
                    onTick = onTick
                )
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
