@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.freeturn.app.R
import com.freeturn.app.data.Server
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsRowIcon
import com.freeturn.app.ui.util.hapticClickable
import com.freeturn.app.ui.theme.Spacing

/** Выбор сервера для шаринга: карточка-строка с выпадающим списком SSH-серверов. */
@Composable
fun ServerSelector(
    servers: List<Server>,
    selected: Server?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hapticClickable(HapticUtil.Pattern.CLICK) { expanded = true }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                SettingsRowIcon(R.drawable.host_24px)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.share_server_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        selected?.name ?: stringResource(R.string.share_server_none),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    painterResource(R.drawable.unfold_more_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text(server.name) },
                    onClick = {
                        expanded = false
                        onSelect(server.id)
                    },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.host_24px), contentDescription = null)
                    }
                )
            }
        }
    }
}
