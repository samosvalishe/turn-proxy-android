package com.freeturn.app.ui.screens.serverdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.ui.util.HapticUtil
import com.freeturn.app.ui.components.BusyProgressIndicator
import com.freeturn.app.ui.theme.Spacing
import com.freeturn.app.viewmodel.server.ServerCleanupState

/** Подтверждение удаления сервера из приложения (без серверной очистки). */
@Composable
internal fun DeleteServerDialog(
    serverName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_delete_confirm_title)) },
        text = { Text(stringResource(R.string.server_delete_confirm_desc, serverName)) },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                    onConfirm()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.server_delete)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Машина диалогов очистки сервера от FreeTurn: предупреждение -> wavy -> итог.
 * Сервер из приложения НЕ удаляется (отдельное действие).
 */
@Composable
internal fun ServerCleanupDialog(
    state: ServerCleanupState,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    when (state) {
        ServerCleanupState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.server_delete_cleaning_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.server_delete_cleaning_desc))
                    Spacer(Modifier.height(Spacing.lg))
                    BusyProgressIndicator()
                }
            },
            confirmButton = {}
        )
        ServerCleanupState.Done -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.server_delete_done_title)) },
            text = { Text(stringResource(R.string.server_delete_done_desc)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    onClose()
                }) { Text(stringResource(R.string.server_delete_done_confirm)) }
            }
        )
        else -> {
            val err = (state as? ServerCleanupState.Error)?.message
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(R.string.server_clean_confirm_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.server_clean_confirm_desc))
                        err?.let {
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                            onConfirm()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(if (err != null) R.string.server_delete_retry else R.string.server_clean_confirm_btn))
                    }
                },
                dismissButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onClose) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
internal fun RenameServerDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newName by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_server_title)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.server_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    onSave(newName)
                },
                enabled = newName.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
