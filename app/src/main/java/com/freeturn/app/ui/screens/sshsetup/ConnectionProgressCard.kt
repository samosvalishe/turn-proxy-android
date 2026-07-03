package com.freeturn.app.ui.screens.sshsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.components.BusyProgressIndicator
import com.freeturn.app.ui.theme.Spacing

/**
 * Прогресс сопряжения: wavy-индикатор + чек-лист шагов (подключение -> авторизация ->
 * проверка SSH). Тональная карточка в едином стиле настроек.
 */
@Composable
internal fun ConnectionProgressCard(step: String) {
    val steps = listOf(
        stringResource(R.string.step_connecting),
        stringResource(R.string.step_auth),
        stringResource(R.string.step_ssh_check)
    )
    val currentIndex = steps.indexOf(step).coerceAtLeast(0)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            BusyProgressIndicator()

            Text(
                text = step,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                steps.forEachIndexed { index, label ->
                    val isDone = index < currentIndex
                    val isActive = index == currentIndex
                    // Статус шага иконкой и цветом TalkBack не видит - отдаём stateDescription.
                    val stateDesc = stringResource(when {
                        isDone -> R.string.setup_task_state_done
                        isActive -> R.string.setup_task_state_active
                        else -> R.string.setup_task_state_pending
                    })
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            stateDescription = stateDesc
                        }
                    ) {
                        Icon(
                            painter = painterResource(when {
                                isDone -> R.drawable.check_circle_24px
                                isActive -> R.drawable.radio_button_checked_24px
                                else -> R.drawable.radio_button_unchecked_24px
                            }),
                            contentDescription = null,
                            tint = when {
                                isDone -> MaterialTheme.colorScheme.primary
                                isActive -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            // Будущие шаги - onSurfaceVariant: контент, не disabled.
                            color = if (isActive || isDone) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
