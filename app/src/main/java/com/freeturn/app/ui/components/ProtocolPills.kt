package com.freeturn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.theme.Spacing

/**
 * Чипы доступа: протокол (WireGuard/прокси) + признак обфускации. Общий вид для
 * sheet выдачи доступа и sheet импорта.
 */
@Composable
fun ProtocolPills(wg: Boolean, obfOn: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Pill(
            iconRes = if (wg) R.drawable.vpn_key_24px else R.drawable.public_24px,
            text = stringResource(if (wg) R.string.share_protocol_wg else R.string.share_protocol_proxy),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (obfOn) {
            Pill(
                iconRes = R.drawable.check_circle_24px,
                text = stringResource(R.string.share_chip_obf),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Pill(iconRes: Int, text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp)
            )
            Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}
