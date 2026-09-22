@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.freeturn.app.ui.util.HapticUtil

/** Вариант выбора; [disabledReason] гасит только его и уходит в stateDescription. */
data class ChoiceOption<T>(
    val value: T,
    val label: String,
    @DrawableRes val iconRes: Int? = null,
    val disabledReason: String? = null
)

/**
 * Одиночный выбор - connected button group (в M3 Expressive заменяет SegmentedButton).
 * Тап по уже выбранному ничего не шлёт; хаптик внутри.
 */
@Composable
fun <T> ConnectedChoiceRow(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    // Карточки настроек - surfaceContainerLow: дефолтный surfaceContainer с ними сливается.
    val colors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { selectableGroup() },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { i, option ->
            val checked = option.value == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) {
                        HapticUtil.perform(context, HapticUtil.Pattern.TOGGLE_ON)
                        onSelect(option.value)
                    }
                },
                enabled = enabled && option.disabledReason == null,
                icon = option.iconRes?.let { res ->
                    @Composable { Icon(painterResource(res), contentDescription = null) }
                },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        option.disabledReason?.let { stateDescription = it }
                    }
            ) {
                Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
