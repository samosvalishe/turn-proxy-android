package com.freeturn.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.ui.HapticUtil

/**
 * Универсальный диалог ввода имени профиля (создание/переименование).
 *
 * @param takenNames множество уже используемых имён (case-insensitive). Имя `initial`
 *                   автоматически исключается из проверки — пользователь может оставить
 *                   текущее имя при переименовании.
 */
@Composable
fun ProfileNameDialog(
    title: String,
    initial: String,
    takenNames: Collection<String> = emptyList(),
    confirmLabel: String = stringResource(R.string.profile_save),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial) }

    val takenSet = remember(takenNames, initial) {
        takenNames.map { it.trim().lowercase() }.toSet() - initial.trim().lowercase()
    }
    val trimmed = name.trim()
    val isDuplicate = trimmed.isNotEmpty() && trimmed.lowercase() in takenSet
    val isEmpty = trimmed.isEmpty()
    val canConfirm = !isEmpty && !isDuplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_name_label)) },
                placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text(stringResource(R.string.profile_name_taken)) }
                } else null
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.SUCCESS)
                    onConfirm(trimmed)
                },
                enabled = canConfirm
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
