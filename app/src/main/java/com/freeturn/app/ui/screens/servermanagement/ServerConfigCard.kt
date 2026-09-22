package com.freeturn.app.ui.screens.servermanagement

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

/**
 * Серверный конфиг прокси: внешний порт и адрес чужого VPN. SSH-only, при живом подключении.
 * [connect] null - свой WG, адрес бэкенда выводит сервер.
 */
@Composable
internal fun ServerConfigCard(
    listenPort: String,
    onListenPort: (String) -> Unit,
    connect: String?,
    onConnect: (String) -> Unit
) {
    SectionLabel(stringResource(R.string.server_config))
    SettingsCard {
        SettingsFieldSlot {
            LabeledTextField(
                value = listenPort,
                onValueChange = { onListenPort(it.filter { c -> c.isDigit() }) },
                labelRes = R.string.listen_port,
                placeholderRes = R.string.listen_port_placeholder,
                supportingRes = R.string.listen_port_desc,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        if (connect != null) {
            SettingsRowDivider()
            SettingsFieldSlot {
                LabeledTextField(
                    value = connect,
                    onValueChange = onConnect,
                    labelRes = R.string.setup_connect_label,
                    placeholderRes = R.string.setup_connect_placeholder,
                    supportingRes = R.string.setup_connect_desc,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }
        }
    }
}
