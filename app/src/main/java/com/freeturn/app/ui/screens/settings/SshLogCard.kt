@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.Spacing

/**
 * Единый SSH-лог: весь вывод команд сопряжения/управления + server.log (тянется кнопкой
 * «Журнал сервера») — всё идёт сюда через runCmd.
 *
 * Раскладка трёхэтажная, чтобы ничего не толкалось в одной строке: шапка (заголовок +
 * счётчик строк), терминал, ряд действий во всю ширину. Терминал на inverseSurface —
 * настоящая тёмная консоль в светлой теме, с приглашением «❯» и мигающим курсором.
 */
@Composable
internal fun SshLogCard(
    lines: List<String>,
    canFetchJournal: Boolean,
    journalLoading: Boolean,
    onFetchJournal: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.ssh_log_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (lines.isNotEmpty()) {
                    AnimatedContent(
                        targetState = lines.size,
                        transitionSpec = {
                            (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false))
                        },
                        label = "ssh_log_count"
                    ) { count ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                            )
                        }
                    }
                }
            }

            SshTerminalPane(lines)

            // Действия отдельным рядом во всю ширину — в шапке им тесно на узких экранах.
            if (canFetchJournal || lines.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (canFetchJournal) {
                        FilledTonalButton(
                            onClick = onFetchJournal,
                            enabled = !journalLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (journalLoading) {
                                LoadingIndicator(modifier = Modifier.size(22.dp))
                            } else {
                                Icon(
                                    painterResource(R.drawable.cloud_download_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ssh_log_fetch_journal), maxLines = 1)
                        }
                    }
                    if (lines.isNotEmpty()) {
                        if (canFetchJournal) {
                            FilledTonalIconButton(onClick = onClear) {
                                Icon(
                                    painterResource(R.drawable.delete_24px),
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        } else {
                            // Оффлайн: журнал не потянуть, очистка — единственное действие.
                            FilledTonalButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                                Icon(
                                    painterResource(R.drawable.delete_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Консоль SSH-лога: тёмная (inverseSurface) моноширинная панель с приглашением «❯» и
 * мигающим курсором в конце — пустой лог выглядит как ждущий терминал, а не дырка.
 * Автопрокрутка к свежим строкам.
 */
@Composable
private fun SshTerminalPane(lines: List<String>) {
    // Тональные роли M3 — как у LogPane соседних карточек: панель живёт в теме
    // (light/dark/dynamic color), консольность несут моно-шрифт, промпт и курсор.
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) { scroll.scrollTo(scroll.maxValue) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp, max = 400.dp)
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.md, vertical = Spacing.md)
        ) {
            val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            if (lines.isEmpty()) {
                Text(
                    "# ${stringResource(R.string.ssh_log_empty)}",
                    style = mono,
                    color = fg.copy(alpha = 0.55f)
                )
            } else {
                // Склейка дорогая (кап 500 строк) — кэшируем по содержимому лога.
                val text = remember(lines) { lines.joinToString("\n") }
                Text(text, style = mono, color = fg)
            }
            Row {
                Text("❯ ", style = mono, color = accent)
                if (LocalReducedMotion.current) {
                    Text("▍", style = mono, color = accent)
                } else {
                    // Альфа в draw-фазе через graphicsLayer: чтение анимации в композиции
                    // рекомпозило бы строку на каждом кадре мигания.
                    val blink = rememberInfiniteTransition(label = "ssh_cursor")
                        .animateFloat(
                            initialValue = 1f,
                            targetValue = 0.1f,
                            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                            label = "ssh_cursor_alpha"
                        )
                    Text(
                        "▍",
                        style = mono,
                        color = accent,
                        modifier = Modifier.graphicsLayer { alpha = blink.value }
                    )
                }
            }
        }
    }
}
