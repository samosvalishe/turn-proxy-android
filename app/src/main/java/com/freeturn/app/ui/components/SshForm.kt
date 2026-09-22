@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.freeturn.app.ui.theme.Spacing
import com.freeturn.app.ui.util.HapticUtil

@Composable
fun SshFormFields(
    ip: String, onIpChange: (String) -> Unit,
    port: String, onPortChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    authType: String, onAuthTypeChange: (String) -> Unit,
    sshKey: String, onSshKeyChange: (String) -> Unit,
    showErrors: Boolean = false,
    // sudo-пароль для key-auth (password-auth переиспользует логин-пароль).
    // Показывается только при showSudoPassword && key-auth.
    sudoPassword: String = "", onSudoPasswordChange: (String) -> Unit = {},
    showSudoPassword: Boolean = false
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var showSudoPw by remember { mutableStateOf(false) }
    val portInvalid = port.toIntOrNull()?.let { it in 1..65535 } != true

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
            AuthMethodChoice(
                authType = authType,
                onAuthTypeChange = onAuthTypeChange
            )
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
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
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
        if (showSudoPassword && authType == SshConfig.AUTH_SSH_KEY) {
            SettingsRowDivider()
            SettingsFieldSlot {
                OutlinedTextField(
                    value = sudoPassword,
                    onValueChange = onSudoPasswordChange,
                    label = { Text(stringResource(R.string.sudo_password)) },
                    supportingText = { Text(stringResource(R.string.sudo_password_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showSudoPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                            showSudoPw = !showSudoPw
                        }) {
                            Icon(
                                painterResource(if (showSudoPw) R.drawable.visibility_off_24px else R.drawable.visibility_24px),
                                contentDescription = if (showSudoPw) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
            }
        }
    }
}

@Composable
private fun AuthMethodChoice(
    authType: String,
    onAuthTypeChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SettingsControlLabel(stringResource(R.string.auth_method_label))
        ConnectedChoiceRow(
            options = listOf(
                ChoiceOption(SshConfig.AUTH_PASSWORD, stringResource(R.string.password)),
                ChoiceOption(SshConfig.AUTH_SSH_KEY, stringResource(R.string.private_key))
            ),
            selected = authType,
            onSelect = onAuthTypeChange
        )
    }
}
