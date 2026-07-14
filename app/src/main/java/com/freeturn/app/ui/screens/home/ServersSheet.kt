@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.HapticUtil
import com.freeturn.app.data.config.Provider
import com.freeturn.app.data.server.ServersSnapshot
import com.freeturn.app.ui.components.ServerRow
import com.freeturn.app.ui.components.settingsItemShape
import com.freeturn.app.ui.util.pasteFromClipboard
import com.freeturn.app.ui.util.redact
import com.freeturn.app.ui.theme.Spacing

@Composable
internal fun ServersSheetContent(
    snapshot: ServersSnapshot,
    privacyMode: Boolean = false,
    callLink: String = "",
    // Прокси запущен - правку ссылки на звонок блокируем (новая комната = реконнект).
    callLinkLocked: Boolean = false,
    onApplyServer: (String) -> Unit = {},
    onOpenServerSettings: (String) -> Unit = {},
    onSaveCallLink: (String) -> Unit = {}
) {
    val active = snapshot.active
    // Менять ссылку можно только у сохранённого активного сервера и пока прокси стоит.
    val callLinkEditable = active != null && !callLinkLocked
    var showCallLinkDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val serverName = active?.name ?: stringResource(R.string.server_unsaved_label)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(serverName) } },
                state = rememberTooltipState(),
                enableUserInput = active != null
            ) {
                Text(
                    serverName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val sub = active?.let {
                (it.client.serverAddress.takeIf { a -> a.isNotBlank() }
                    ?: it.ssh.ip.takeIf { a -> a.isNotBlank() })?.redact(privacyMode)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                sub.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(16.dp))

        ProviderChip(
            current = active?.client?.provider ?: Provider.VK,
            editable = callLinkEditable,
            onClick = { showCallLinkDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.servers_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.lg, bottom = Spacing.sm)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            itemsIndexed(snapshot.list, key = { _, p -> p.id }) { index, p ->
                val isActive = snapshot.activeId == p.id
                val sub = listOfNotNull(
                    p.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode),
                    p.ssh.ip.takeIf { it.isNotBlank() }?.let { "SSH ${it.redact(privacyMode)}" }
                ).joinToString(" · ").ifBlank { "-" }
                ServerRow(
                    name = p.name,
                    subtitle = sub,
                    isActive = isActive,
                    shape = settingsItemShape(index, snapshot.list.size),
                    inactiveContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { if (!isActive) onApplyServer(p.id) },
                    trailing = {
                        IconButton(onClick = { onOpenServerSettings(p.id) }) {
                            Icon(
                                painterResource(R.drawable.settings_outlined_24px),
                                contentDescription = stringResource(R.string.nav_settings),
                                tint = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }

    if (showCallLinkDialog) {
        CallLinkDialog(
            initial = callLink,
            onSave = { showCallLinkDialog = false; onSaveCallLink(it) },
            onDismiss = { showCallLinkDialog = false }
        )
    }
}

@Composable
private fun ProviderChip(
    current: String,
    editable: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chipPadding = PaddingValues(start = Spacing.sm, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm)
    if (editable) {
        Button(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(),
            contentPadding = chipPadding,
            modifier = modifier
        ) {
            ProviderChipContent(current, showEdit = true)
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(chipPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ProviderChipContent(current, showEdit = false)
            }
        }
    }
}

@Composable
private fun RowScope.ProviderChipContent(current: String, showEdit: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialShapes.Sunny.toShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.nearby_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp)
        )
    }
    Text(
        providerLabel(current),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(start = Spacing.sm)
    )
    if (showEdit) {
        Icon(
            painterResource(R.drawable.edit_24px),
            contentDescription = stringResource(R.string.call_link_edit),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .padding(start = Spacing.sm)
                .size(18.dp)
        )
    }
}

@Composable
private fun CallLinkDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialShapes.Sunny.toShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.link_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = { Text(stringResource(R.string.call_link_label)) },
        text = {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(R.string.call_link_placeholder)) },
                supportingText = { Text(stringResource(R.string.call_link_support)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(onClick = {
                        context.pasteFromClipboard()?.takeIf { it.isNotBlank() }?.let {
                            link = it.trim()
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.content_paste_24px),
                            contentDescription = stringResource(R.string.paste)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.SUCCESS)
                    onSave(link.trim())
                },
                enabled = link.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun providerLabel(value: String): String = when (value) {
    Provider.VK -> stringResource(R.string.provider_vk)
    else -> value
}

