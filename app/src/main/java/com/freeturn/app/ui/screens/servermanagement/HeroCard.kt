package com.freeturn.app.ui.screens.servermanagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.freeturn.app.ui.theme.Spacing

/**
 * Hero-карточка состояния (неактивный сервер / потеря связи): иконка, заголовок,
 * пояснение и основная кнопка. Общий стиль для пустых/аварийных состояний экрана.
 */
@Composable
internal fun HeroCard(
    iconRes: Int,
    title: String,
    desc: String,
    actionLabel: String,
    onAction: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    descTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Icon(painterResource(iconRes), contentDescription = null, tint = iconTint)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = descTint,
                textAlign = TextAlign.Center
            )
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            ) { Text(actionLabel) }
        }
    }
}
