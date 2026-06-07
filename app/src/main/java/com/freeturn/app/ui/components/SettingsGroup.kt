@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.util.hapticClickable

// Общие строительные блоки экранов настроек (Settings-флоу и «Режим подключения»):
// заголовок секции, карточка-группа, строка-вход, inset-разделитель. Один источник —
// чтобы экраны выглядели одинаково и не дублировали верстку.

/** Макс. ширина контента — читаемая колонка на планшетах/foldable (MD3 large+). */
val SettingsContentMaxWidth = 840.dp

// Левый отступ inset-разделителя = иконка(40) + отступ слева(16) + зазор(16).
private val RowDividerIndent = 72.dp

/** Заголовок секции — мелкий акцентный лейбл над карточкой-группой. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        // heading() — TalkBack прыгает между секциями.
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { heading() }
    )
}

/** Карточка-группа: тональный контейнер со скруглением, строки внутри. */
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

/** Тональная иконка-кружок (Sunny) строки настроек. Один источник формы/размера. */
@Composable
fun SettingsRowIcon(iconRes: Int, enabled: Boolean = true) {
    // Disabled: гасим контейнер и тинт вместе с заголовком — строка не выглядит наполовину живой.
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha),
                MaterialShapes.Sunny.toShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Строка-вход: тональная иконка, заголовок/подзаголовок, опциональный трейлинг.
 * По умолчанию трейлинг — шеврон (навигация). Передай null, чтобы убрать, или другой
 * ресурс (напр. галочку статуса). Haptic-клик встроен; [onClick] — чистое действие.
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
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

/** Inset-разделитель между строками карточки (отступ под иконку). */
@Composable
fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = RowDividerIndent),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
