package com.freeturn.app.ui.screens.clientsetup

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.freeturn.app.R
import com.freeturn.app.ui.components.LabeledTextField
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.util.redact

/** Адреса подключения: сервер, ссылка звонка (только relay), локальный listen. */
@Composable
internal fun ConnectionCard(
    serverAddress: String,
    onServerAddress: (String) -> Unit,
    showCallLink: Boolean,
    callLink: String,
    onCallLink: (String) -> Unit,
    localPort: String,
    onLocalPort: (String) -> Unit,
    privacyMode: Boolean
) {
    SectionLabel(stringResource(R.string.connection_title))
    SettingsCard {
        SettingsFieldSlot {
            LabeledTextField(
                value = serverAddress.redact(privacyMode),
                onValueChange = { if (!privacyMode) onServerAddress(it) },
                labelRes = R.string.server_address_label,
                placeholderRes = R.string.server_address_placeholder,
                supportingRes = R.string.server_address_support,
                readOnly = privacyMode,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
        if (showCallLink) {
            SettingsRowDivider()
            SettingsFieldSlot {
                LabeledTextField(
                    value = callLink.redact(privacyMode),
                    onValueChange = { if (!privacyMode) onCallLink(it) },
                    labelRes = R.string.call_link_label,
                    placeholderRes = R.string.call_link_placeholder,
                    supportingRes = R.string.call_link_support,
                    readOnly = privacyMode,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            LabeledTextField(
                value = localPort.redact(privacyMode),
                onValueChange = { if (!privacyMode) onLocalPort(it) },
                labelRes = R.string.local_listen_address,
                placeholderRes = R.string.local_listen_placeholder,
                supportingRes = R.string.local_listen_support,
                readOnly = privacyMode,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
    }
}
