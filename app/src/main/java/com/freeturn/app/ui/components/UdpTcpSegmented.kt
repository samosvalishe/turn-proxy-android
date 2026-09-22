package com.freeturn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.ui.theme.Spacing

@Composable
fun UdpTcpSegmented(
    tcp: Boolean,
    onTcp: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    tcpDisabledReason: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SettingsControlLabel(label)
        ConnectedChoiceRow(
            options = listOf(
                ChoiceOption(false, stringResource(R.string.udp)),
                ChoiceOption(true, stringResource(R.string.tcp), disabledReason = tcpDisabledReason)
            ),
            selected = tcp,
            onSelect = onTcp
        )
        if (tcpDisabledReason != null) {
            Text(
                tcpDisabledReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
