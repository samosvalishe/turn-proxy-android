@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.HapticUtil
import com.freeturn.app.domain.UpdateState
import com.freeturn.app.ui.components.BusyProgressIndicator

/**
 * Диалоги цикла обновления приложения: доступно -> качается -> готово к установке.
 * Чистый компонент: состояние и действия приходят снаружи.
 */
@Composable
internal fun UpdateDialogs(
    updateState: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    // Версия, для которой диалог "Доступно обновление" отклонён:
    // следующая версия покажет диалог заново.
    var dismissedVersion by rememberSaveable { mutableStateOf<String?>(null) }

    when (val state = updateState) {
        is UpdateState.Available -> if (dismissedVersion != state.version) {
            AlertDialog(
                onDismissRequest = { dismissedVersion = state.version },
                title = { Text(stringResource(R.string.update_available_title)) },
                text = { Text(stringResource(R.string.update_available, state.version)) },
                confirmButton = {
                    TextButton(onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        dismissedVersion = state.version
                        onDownload()
                    }) { Text(stringResource(R.string.update_download)) }
                },
                dismissButton = {
                    TextButton(onClick = { dismissedVersion = state.version }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.update_downloading, state.progress))
                        Spacer(Modifier.height(12.dp))
                        BusyProgressIndicator(progress = { state.progress / 100f })
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onReset,
                title = { Text(stringResource(R.string.update_ready_title)) },
                text = { Text(stringResource(R.string.update_ready_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        onInstall()
                    }) { Text(stringResource(R.string.update_install)) }
                },
                dismissButton = {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        else -> {}
    }
}
