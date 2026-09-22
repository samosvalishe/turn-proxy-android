@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDropdownMenuItem
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
import com.freeturn.app.R
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.ui.util.HapticUtil

@Composable
fun <T> OptionDropdown(
    label: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minLines: Int = 1
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.value == selected }?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = minLines == 1,
            minLines = minLines,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MenuDefaults.groupStandardContainerColor,
            shape = MenuDefaults.standaloneGroupShape
        ) {
            options.forEachIndexed { i, option ->
                SelectableDropdownMenuItem(
                    selected = option.value == selected,
                    onClick = {
                        expanded = false
                        if (option.value != selected) {
                            HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                            onSelect(option.value)
                        }
                    },
                    text = { Text(option.label, style = MaterialTheme.typography.bodyLarge) },
                    shapes = MenuDefaults.itemShape(i, options.size),
                    enabled = option.disabledReason == null,
                    selectedLeadingIcon = {
                        Icon(
                            painterResource(R.drawable.check_24px),
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize)
                        )
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun obfProfileLabel(value: String): String = when (value) {
    ObfProfile.NONE -> stringResource(R.string.obf_none)
    ObfProfile.RTPOPUS -> stringResource(R.string.obf_rtpopus)
    ObfProfile.RTPOPUS2 -> stringResource(R.string.obf_rtpopus2)
    ObfProfile.RTPOPUS3 -> stringResource(R.string.obf_rtpopus3)
    else -> value
}
