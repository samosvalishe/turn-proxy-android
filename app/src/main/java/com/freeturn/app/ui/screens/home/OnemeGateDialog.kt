@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.freeturn.app.R
import com.freeturn.app.data.config.ONEME_PACKAGE
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.Spacing

/**
 * Полноэкранный VPN-гейт: пока [ONEME_PACKAGE] стоит и не добавлен в раздельное
 * туннелирование, VPN не поднимается. Единственный выход - добавить его в исключения.
 */
@Composable
fun OnemeGateDialog(
    onAddToSplit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember {
        runCatching {
            context.packageManager.getApplicationIcon(ONEME_PACKAGE).toBitmap().asImageBitmap()
        }.getOrNull()
    }

    val reducedMotion = LocalReducedMotion.current
    val rotation = if (reducedMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "oneme_gate")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
            label = "oneme_rotation"
        )
        value
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.xxxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = MaterialShapes.SoftBurst.toShape(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer { rotationZ = rotation }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        // Контр-вращение: фигура крутится, иконка стоит.
                        val iconModifier = Modifier
                            .size(72.dp)
                            .graphicsLayer { rotationZ = -rotation }
                        if (appIcon != null) {
                            Image(appIcon, contentDescription = null, modifier = iconModifier)
                        } else {
                            Icon(
                                painterResource(R.drawable.group_off_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = iconModifier
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xxl))
                Text(
                    stringResource(R.string.oneme_gate_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(R.string.oneme_gate_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Spacing.xxl))
                Button(
                    onClick = onAddToSplit,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painterResource(R.drawable.apps_24px),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.oneme_gate_add))
                }
                Spacer(Modifier.height(Spacing.sm))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
