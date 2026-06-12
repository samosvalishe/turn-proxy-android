@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.share

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freeturn.app.R

/** Подтверждение отзыва доступа: пир/cid удаляется с сервера, доступ получателя умрёт. */
@Composable
fun RevokeDialog(
    userName: String,
    revoking: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = userName.ifEmpty { stringResource(R.string.share_peer_unnamed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_revoke_title)) },
        text = { Text(stringResource(R.string.share_revoke_desc, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !revoking) {
                if (revoking) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        stringResource(R.string.share_revoke_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !revoking) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
