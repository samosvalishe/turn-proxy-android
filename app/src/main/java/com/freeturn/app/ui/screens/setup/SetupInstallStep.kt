@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.ui.util.HapticUtil
import com.freeturn.app.ui.components.InlineErrorCard
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.SetupInstallState
import com.freeturn.app.viewmodel.SetupSummary
import com.freeturn.app.viewmodel.SetupTaskKind
import com.freeturn.app.ui.theme.Spacing

/**
 * Шаг 3 мастера: чек-лист установки -> итог. Ошибка показывает карточку с текстом
 * и действиями "Повторить" / "К настройкам".
 */
@Composable
fun SetupInstallStep(
    install: SetupInstallState?,
    onRetry: () -> Unit,
    onBackToConfig: () -> Unit
) {
    install ?: return
    val context = LocalContext.current

    if (install.done) {
        install.summary?.let { SetupDoneCard(it) }
        return
    }

    TaskChecklistCard(install)

    if (install.error != null) {
        InlineErrorCard(install.error)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    onRetry()
                },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large
            ) { Text(stringResource(R.string.setup_retry)) }
            TextButton(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                    onBackToConfig()
                },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large
            ) { Text(stringResource(R.string.setup_back_to_config)) }
        }
    }
}

/** Чек-лист задач установки: выполнено -> активная -> ожидание, wavy-индикатор сверху. */
@Composable
private fun TaskChecklistCard(install: SetupInstallState) {
    val failed = install.error != null
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            if (!failed) BusyProgressIndicator()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                install.tasks.forEachIndexed { index, task ->
                    TaskRow(
                        label = stringResource(task.labelRes()),
                        isDone = index < install.current,
                        isActive = index == install.current,
                        isFailed = failed && index == install.current
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(label: String, isDone: Boolean, isActive: Boolean, isFailed: Boolean) {
    val reducedMotion = LocalReducedMotion.current
    val state = when {
        isFailed -> TaskVisual.Failed
        isDone -> TaskVisual.Done
        isActive -> TaskVisual.Active
        else -> TaskVisual.Pending
    }
    val stateDesc = stringResource(state.descRes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Иконка и цвет недоступны TalkBack - статус отдаём stateDescription.
        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = stateDesc }
    ) {
        val popSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                if (reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (scaleIn(popSpec) + fadeIn()) togetherWith fadeOut()
            },
            label = "task_icon"
        ) { s ->
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (s == TaskVisual.Active && !reducedMotion) {
                    // Живой expressive-индикатор на выполняемой задаче.
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        painter = painterResource(when (s) {
                            TaskVisual.Failed -> R.drawable.error_24px
                            TaskVisual.Done -> R.drawable.check_circle_24px
                            TaskVisual.Active -> R.drawable.radio_button_checked_24px
                            TaskVisual.Pending -> R.drawable.radio_button_unchecked_24px
                        }),
                        contentDescription = null,
                        tint = when (s) {
                            TaskVisual.Failed -> MaterialTheme.colorScheme.error
                            TaskVisual.Done -> MaterialTheme.colorScheme.primary
                            TaskVisual.Active -> MaterialTheme.colorScheme.secondary
                            TaskVisual.Pending -> MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            // Ожидающие задачи - onSurfaceVariant: это контент, не disabled-состояние.
            color = if (isDone || isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class TaskVisual(val descRes: Int) {
    Done(R.string.setup_task_state_done),
    Active(R.string.setup_task_state_active),
    Failed(R.string.setup_task_state_failed),
    Pending(R.string.setup_task_state_pending)
}

/** Итог установки: тональный success-бейдж + сводка (адрес/режим/обфускация) + WG-подсказка. */
@Composable
private fun SetupDoneCard(summary: SetupSummary) {
    val reducedMotion = LocalReducedMotion.current
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
            // Бейдж в форме Sunny - та же форма, что у иконок строк настроек.
            // Появляется expressive-пружиной (момент успеха).
            val badgeVisible = remember {
                MutableTransitionState(reducedMotion).apply { targetState = true }
            }
            AnimatedVisibility(
                visibleState = badgeVisible,
                enter = scaleIn(MaterialTheme.motionScheme.slowSpatialSpec()) + fadeIn(),
                exit = ExitTransition.None
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.extendedColorScheme.successContainer,
                            MaterialShapes.Sunny.toShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.check_circle_24px),
                        contentDescription = null,
                        tint = MaterialTheme.extendedColorScheme.onSuccessContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.setup_done_hero),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.setup_done_active_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                SummaryRow(
                    stringResource(R.string.setup_summary_name),
                    summary.serverName
                )
                SummaryRow(
                    stringResource(R.string.setup_summary_address),
                    summary.serverAddress,
                    mono = true
                )
                SummaryRow(
                    stringResource(R.string.setup_summary_mode),
                    stringResource(if (summary.vpnMode) R.string.mode_vpn else R.string.mode_proxy)
                )
                SummaryRow(
                    stringResource(R.string.setup_summary_obf),
                    if (summary.obfProfile == ObfProfile.NONE) stringResource(R.string.setup_obf_off)
                    else summary.obfProfile
                )
            }

            // VPN-режим: либо конфиг уже подставлен, либо нужен ручной импорт .conf.
            when {
                summary.wgConfImported -> SetupDoneHint(stringResource(R.string.setup_done_wg_imported))
                summary.usedExistingWg -> SetupDoneHint(stringResource(R.string.setup_done_wg_manual))
            }
        }
    }
}

@Composable
private fun SetupDoneHint(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.md)
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

private fun SetupTaskKind.labelRes(): Int = when (this) {
    SetupTaskKind.InstallCore -> R.string.setup_task_install
    SetupTaskKind.WireGuard -> R.string.setup_task_wireguard
    SetupTaskKind.StartServer -> R.string.setup_task_start
    SetupTaskKind.Persist -> R.string.setup_task_persist
}
