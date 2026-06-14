@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.theme.Spacing
import com.freeturn.app.data.HapticUtil
import com.freeturn.app.ui.util.hapticClickable

/**
 * Строка-свитч в стиле [SettingsEntryRow]: тональная иконка (опц.), заголовок/подзаголовок,
 * trailing Switch с галкой в thumb (M3 expressive). Семантику несёт вся строка -
 * toggleable + [Role.Switch], TalkBack озвучивает как переключатель с состоянием.
 * Haptic встроен; [onCheckedChange] - чистое действие.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconRes: Int? = null,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    // MD3 disabled content = 0.38 альфы; заголовок и подзаголовок гаснут синхронно.
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val subtitleColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { v ->
                    HapticUtil.perform(
                        context,
                        if (v) HapticUtil.Pattern.TOGGLE_ON else HapticUtil.Pattern.TOGGLE_OFF
                    )
                    onCheckedChange(v)
                }
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        if (iconRes != null) SettingsRowIcon(iconRes, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
            }
        }
        // null = display-only: клики и семантику несёт строка (один haptic, один фокус).
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Тональная иконка-кружок (Sunny) строки настроек. Один источник формы/размера.
 * Цвета параметризованы под особые строки (danger -> errorContainer).
 */
@Composable
fun SettingsRowIcon(
    iconRes: Int,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    // Disabled: гасим контейнер и тинт вместе с заголовком - строка не выглядит наполовину живой.
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container.copy(alpha = alpha), MaterialShapes.Sunny.toShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = tint.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Строка-вход: тональная иконка, заголовок/подзаголовок, опциональный трейлинг.
 * По умолчанию трейлинг - шеврон (навигация). Передай null, чтобы убрать, или другой
 * ресурс (напр. галочку статуса). Haptic-клик встроен; [onClick] - чистое действие.
 */
@Composable
fun SettingsEntryRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    trailingRes: Int? = R.drawable.chevron_right_24px,
    trailingTint: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val subtitleColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hapticClickable(HapticUtil.Pattern.CLICK, enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        SettingsRowIcon(iconRes, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor
                )
            }
        }
        if (trailingRes != null) {
            val baseTrailing = if (trailingTint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant
            else trailingTint
            Icon(
                painterResource(trailingRes),
                contentDescription = null,
                tint = if (enabled) baseTrailing else baseTrailing.copy(alpha = 0.38f)
            )
        }
    }
}
