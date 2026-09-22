@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.setup

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.config.HostPort
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.server.ServerBackend
import com.freeturn.app.data.server.ServerMethod
import com.freeturn.app.ui.util.HapticUtil
import com.freeturn.app.ui.components.ChoiceOption
import com.freeturn.app.ui.components.ConnectedChoiceRow
import com.freeturn.app.ui.components.OptionDropdown
import com.freeturn.app.ui.components.obfProfileLabel
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsControlLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import com.freeturn.app.ui.components.UdpTcpSegmented
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.server.SetupConfigDraft
import com.freeturn.app.ui.theme.Spacing

@Composable
fun SetupConfigStep(
    draft: SetupConfigDraft,
    reinstall: Boolean,
    duplicateHost: Boolean,
    portsClash: Boolean,
    showErrors: Boolean,
    onDraftChange: (SetupConfigDraft) -> Unit,
    onRollListenPort: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val resizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val cardModifier =
        if (reducedMotion) Modifier else Modifier.animateContentSize(resizeSpec)

    when {
        reinstall -> InfoPanel(stringResource(R.string.setup_reinstall))
        duplicateHost -> InfoPanel(stringResource(R.string.setup_duplicate_host))
    }

    SectionLabel(stringResource(R.string.server_name_label))
    SettingsCard {
        SettingsFieldSlot {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = { Text(stringResource(R.string.server_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                supportingText = { Text(stringResource(R.string.setup_name_hint)) }
            )
        }
    }

    SectionLabel(stringResource(R.string.setup_backend_section))
    SettingsCard(modifier = cardModifier) {
        SettingsFieldSlot {
            SettingsControlLabel(stringResource(R.string.setup_backend_label))
            ConnectedChoiceRow(
                options = listOf(
                    ChoiceOption(ServerBackend.NEW, stringResource(R.string.setup_backend_new)),
                    ChoiceOption(ServerBackend.EXTERNAL, stringResource(R.string.setup_backend_external))
                ),
                selected = draft.backend,
                onSelect = { onDraftChange(draft.copy(backend = it)) }
            )
            Hint(stringResource(
                if (draft.ownWg) R.string.setup_backend_new_desc else R.string.setup_backend_external_desc
            ))
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            if (draft.ownWg) {
                val netInvalid = showErrors && !ServerBackend.isValidNet(draft.wgNet)
                OutlinedTextField(
                    value = draft.wgNet,
                    onValueChange = { v ->
                        onDraftChange(draft.copy(wgNet = v.filter { it.isDigit() || it == '.' || it == '/' }))
                    },
                    label = { Text(stringResource(R.string.setup_wg_net_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = netInvalid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    supportingText = {
                        Text(stringResource(
                            if (netInvalid) R.string.setup_wg_net_invalid else R.string.setup_wg_net_desc
                        ))
                    }
                )
                PortField(
                    value = draft.wgPort,
                    onValueChange = { onDraftChange(draft.copy(wgPort = it)) },
                    label = stringResource(R.string.setup_wg_port_label),
                    error = when {
                        portsClash -> stringResource(R.string.setup_ports_clash)
                        showErrors && !portOk(draft.wgPort) -> stringResource(R.string.setup_port_invalid)
                        else -> null
                    }
                )
            } else {
                val connectInvalid = showErrors && !HostPort.isValid(draft.connect.trim())
                OutlinedTextField(
                    value = draft.connect,
                    onValueChange = { onDraftChange(draft.copy(connect = it.trim())) },
                    label = { Text(stringResource(R.string.setup_connect_label)) },
                    placeholder = { Text(stringResource(R.string.setup_connect_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = connectInvalid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    supportingText = {
                        Text(stringResource(
                            if (connectInvalid) R.string.setup_connect_invalid else R.string.setup_connect_desc
                        ))
                    }
                )
                UdpTcpSegmented(
                    tcp = draft.backendTcp,
                    onTcp = { onDraftChange(draft.copy(backendTcp = it)) },
                    label = stringResource(R.string.setup_backend_protocol_label)
                )
                Hint(stringResource(R.string.setup_backend_protocol_desc))
            }
        }
    }

    SectionLabel(stringResource(R.string.setup_server_section))
    SettingsCard {
        SettingsFieldSlot {
            ConnectedChoiceRow(
                options = listOf(
                    ChoiceOption(ServerMethod.DOCKER, stringResource(R.string.setup_method_docker)),
                    ChoiceOption(ServerMethod.SYSTEMD, stringResource(R.string.setup_method_systemd))
                ),
                selected = draft.method,
                onSelect = { onDraftChange(draft.copy(method = it)) }
            )
            Hint(stringResource(
                if (draft.method == ServerMethod.DOCKER) R.string.setup_method_docker_desc
                else R.string.setup_method_systemd_desc
            ))
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            PortField(
                value = draft.listenPort,
                onValueChange = { onDraftChange(draft.copy(listenPort = it)) },
                label = stringResource(R.string.listen_port),
                supporting = stringResource(R.string.setup_listen_port_desc),
                onRoll = onRollListenPort,
                error = when {
                    portsClash -> stringResource(R.string.setup_ports_clash)
                    showErrors && !portOk(draft.listenPort) -> stringResource(R.string.setup_port_invalid)
                    else -> null
                }
            )
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            OptionDropdown(
                label = stringResource(R.string.obf_profile_title),
                options = ObfProfile.VALUES.map { ChoiceOption(it, obfProfileLabel(it)) },
                selected = draft.obfProfile,
                onSelect = { onDraftChange(draft.copy(obfProfile = it)) }
            )
            Hint(stringResource(
                if (draft.obfProfile == ObfProfile.NONE) R.string.setup_obf_hint_none
                else R.string.setup_obf_hint_rtpopus
            ))
        }
    }

    SectionLabel(stringResource(R.string.provider_relay))
    SettingsCard {
        SettingsFieldSlot {
            OutlinedTextField(
                value = draft.callLink,
                onValueChange = { onDraftChange(draft.copy(callLink = it)) },
                label = { Text(stringResource(R.string.call_link_label)) },
                placeholder = { Text(stringResource(R.string.call_link_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                supportingText = { Text(stringResource(R.string.setup_call_link_hint)) }
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun portOk(p: String): Boolean = p.toIntOrNull()?.let { it in 1..65535 } == true

@Composable
private fun InfoPanel(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                painterResource(R.drawable.info_24px),
                contentDescription = null,
                tint = MaterialTheme.extendedColorScheme.warning,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PortField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onRoll: (() -> Unit)? = null,
    supporting: String? = null,
    error: String? = null
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        trailingIcon = onRoll?.let { roll ->
            {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    roll()
                }) {
                    Icon(
                        painterResource(R.drawable.refresh_24px),
                        contentDescription = stringResource(R.string.setup_port_roll)
                    )
                }
            }
        }
    )
}
