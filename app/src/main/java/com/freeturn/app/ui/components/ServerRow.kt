@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.util.hapticClickable

/**
 * Общая строка-сервер сегментированной группы: аватар, имя/подзаголовок, опциональный
 * trailing. Используется списком «Настройки → Серверы» и нижним листом на главном.
 * Выбранность несёт тон контейнера (secondaryContainer, как у selected-элементов M3)
 * + насыщенный аватар; для TalkBack — selected + бейдж «Активный» в contentDescription.
 */
@Composable
fun ServerRow(
    name: String,
    subtitle: String,
    isActive: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    // Лист на главном сам лежит на surfaceContainerLow — ему нужен контейнер потемнее.
    inactiveContainer: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    /** Простой иконко-трейлинг (шеврон навигации) — тинт согласован с подзаголовком. */
    trailingIconRes: Int? = null,
    /** Интерактивный трейлинг (IconButton) — у него свой тач-таргет, край прижимается. */
    trailing: (@Composable () -> Unit)? = null
) {
    val container = if (isActive) MaterialTheme.colorScheme.secondaryContainer
    else inactiveContainer
    val titleColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    val subColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    val iconContainer = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val iconTint = if (isActive) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    val activeBadge = stringResource(R.string.profile_active_badge)
    val rowDesc = if (isActive) "$name, $activeBadge" else name

    Surface(shape = shape, color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hapticClickable(HapticUtil.Pattern.CLICK, onClick = onClick)
                .semantics {
                    selected = isActive
                    contentDescription = rowDesc
                }
                .padding(
                    start = 16.dp,
                    // У trailing-кнопки собственный тач-таргет — прижимаем её к краю.
                    end = if (trailing != null) 4.dp else 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.database_24px),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingIconRes != null) {
                Icon(
                    painterResource(trailingIconRes),
                    contentDescription = null,
                    tint = subColor
                )
            }
            if (trailing != null) trailing()
        }
    }
}

/** Пустой список серверов: Cookie9Sided-плашка + общая строка. Один вид на всё приложение. */
@Composable
fun EmptyServersState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(104.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painterResource(R.drawable.database_outlined_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_empty_servers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
