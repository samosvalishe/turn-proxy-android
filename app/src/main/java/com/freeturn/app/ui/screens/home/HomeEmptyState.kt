@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.theme.LocalReducedMotion

/**
 * Главный экран без единого сервера: вместо кнопки запуска и листа серверов —
 * герой-приглашение с CTA «Добавить сервер». Запускать без сервера нечего,
 * поэтому тоггл и sheet не показываются вовсе.
 */
@Composable
internal fun HomeEmptyState(
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    // Медленное вращение фигуры (оборот в минуту). При reduced-motion — статично.
    val rotation = if (reducedMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "empty_breathe")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
            label = "empty_rotation"
        )
        value
    }

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(128.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painterResource(R.drawable.database_outlined_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    // Контр-вращение: фигура крутится, иконка стоит на месте.
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer { rotationZ = -rotation }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddServer,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(
                painterResource(R.drawable.add_24px),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.home_add_server))
        }
    }
}
