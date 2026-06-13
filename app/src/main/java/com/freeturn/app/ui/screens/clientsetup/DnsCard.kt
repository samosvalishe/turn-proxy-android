@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.screens.clientsetup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.freeturn.app.R
import com.freeturn.app.data.DnsList
import com.freeturn.app.data.config.DnsMode
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsControlLabel
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.components.SettingsSwitchRow

/**
 * DNS: режим резолвера, ручной список, свитч DNS оператора. Ручной список имеет приоритет
 * (см. ProxyService) - при действующем списке свитч оператора гасится, невалидные токены
 * подсвечиваются. UI и движок нормализуют ввод одним DnsList.
 */
@Composable
internal fun DnsCard(
    dnsMode: String,
    onDnsMode: (String) -> Unit,
    customDns: String,
    onCustomDns: (String) -> Unit,
    useCarrierDns: Boolean,
    onUseCarrierDns: (Boolean) -> Unit
) {
    val dnsEffective = DnsList.normalize(customDns)
    val dnsHasInvalid = DnsList.hasInvalidTokens(customDns)
    SectionLabel(stringResource(R.string.client_section_dns))
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
                        selected = dnsMode == value,
                        onClick = { onDnsMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = dnsOptions.size)
                    ) { Text(label) }
                }
            }
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            OutlinedTextField(
                value = customDns,
                onValueChange = onCustomDns,
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
        // DNS оператора - ниже ручного ввода, т.к. ручной список имеет приоритет
        // (см. ProxyService). При действующем ручном списке гасим свитч, чтобы
        // не вводить в заблуждение "включён, но не действует".
        SettingsSwitchRow(
            title = stringResource(R.string.use_carrier_dns),
            subtitle = if (dnsEffective.isEmpty()) stringResource(R.string.use_carrier_dns_desc)
                       else stringResource(R.string.use_carrier_dns_overridden),
            checked = useCarrierDns,
            enabled = dnsEffective.isEmpty(),
            onCheckedChange = onUseCarrierDns
        )
    }
}
