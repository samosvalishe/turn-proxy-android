package com.freeturn.app.ui.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R

@Composable
internal fun V5SetupNoticeDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.v5_setup_notice_title)) },
        text = { Text(stringResource(R.string.v5_setup_notice_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.v5_setup_notice_confirm))
            }
        }
    )
}
