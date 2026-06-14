@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.HapticUtil

/**
 * Поля SSH-формы: адрес/порт + аутентификация (логин, способ входа, секрет).
 * Вставляется в Column с вертикальным spacedBy - собственной обёртки нет,
 * секции и карточки ложатся в общий ритм экрана. [showErrors] подсвечивает
 * незаполненные обязательные поля (включается по тапу на невалидный submit).
 */
@Composable
fun SshFormFields(
    ip: String, onIpChange: (String) -> Unit,
    port: String, onPortChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    authType: String, onAuthTypeChange: (String) -> Unit,
    sshKey: String, onSshKeyChange: (String) -> Unit,
    showErrors: Boolean = false
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    val portInvalid = port.toIntOrNull()?.let { it in 1..65535 } != true

    // --- Сервер: адрес и порт ---
    SectionLabel(stringResource(R.string.server_data))
    SettingsCard {
        SettingsFieldSlot {
            LabeledTextField(
                value = ip,
                onValueChange = onIpChange,
                labelRes = R.string.server_ip_label,
                placeholderRes = R.string.server_ip_placeholder,
                isError = showErrors && ip.isBlank(),
                errorRes = R.string.setup_field_required,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                )
            )
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            LabeledTextField(
                value = port,
                onValueChange = { onPortChange(it.filter { c -> c.isDigit() }) },
                labelRes = R.string.ssh_port,
                isError = showErrors && portInvalid,
                errorRes = R.string.setup_port_invalid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
        }
    }

    // --- Аутентификация: логин, способ входа, секрет ---
    SectionLabel(stringResource(R.string.authentication))
    SettingsCard {
        SettingsFieldSlot {
            LabeledTextField(
                value = username,
                onValueChange = onUsernameChange,
                labelRes = R.string.username,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = authType == SshConfig.AUTH_PASSWORD,
                    onClick = {
                        if (authType != SshConfig.AUTH_PASSWORD) {
                            HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                            onAuthTypeChange(SshConfig.AUTH_PASSWORD)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.password)) }
                SegmentedButton(
                    selected = authType == SshConfig.AUTH_SSH_KEY,
                    onClick = {
                        if (authType != SshConfig.AUTH_SSH_KEY) {
                            HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                            onAuthTypeChange(SshConfig.AUTH_SSH_KEY)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.private_key)) }
            }
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            if (authType == SshConfig.AUTH_PASSWORD) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showErrors && password.isBlank(),
                    supportingText = if (showErrors && password.isBlank()) {
                        { Text(stringResource(R.string.setup_field_required)) }
                    } else null,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                            showPassword = !showPassword
                        }) {
                            Icon(
                                painterResource(if (showPassword) R.drawable.visibility_off_24px else R.drawable.visibility_24px),
                                contentDescription = if (showPassword) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
            } else {
                OutlinedTextField(
                    value = sshKey,
                    onValueChange = onSshKeyChange,
                    label = { Text(stringResource(R.string.private_key_pem)) },
                    placeholder = { Text(stringResource(R.string.private_key_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    isError = showErrors && sshKey.isBlank(),
                    supportingText = if (showErrors && sshKey.isBlank()) {
                        { Text(stringResource(R.string.setup_field_required)) }
                    } else null,
                    maxLines = 10
                )
            }
        }
    }
}
