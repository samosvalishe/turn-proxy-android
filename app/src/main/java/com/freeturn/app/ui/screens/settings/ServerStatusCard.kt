@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.freeturn.app.R
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.ServerHubStatus
import com.freeturn.app.ui.theme.Spacing

/**
 * Карточка статуса сервера в хабе. Один источник истины — [ServerHubStatus] (собран в VM).
 * Дизайн: hero-строка (живой индикатор + заголовок фазы) несёт статус ОДИН раз — без
 * дублирующих тегов (детали ядра живут в «Отладочной информации»). Тело меняется одним
 * [AnimatedContent] (size-spring, M3 expressive); фазы холодного захода
 * (disconnected/connecting/checking) свёрнуты в Connecting — один переход в Online.
 */
@Composable
internal fun ServerStatusCard(
    status: ServerHubStatus,
    syncOn: Boolean,
    onActivate: () -> Unit,
    onRetry: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    // Sync OFF — live-фазы ядра (online/connecting/working/failed) нерелевантны: клиент с
    // сервером не общается. Схлопываем их в нейтральную заглушку. Actionable-состояния
    // (Offline/NotPaired) остаются — это setup, а не live-статус.
    val effective = if (!syncOn && status.isLivePhase()) ServerHubStatus.SyncOff else status
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = effective,
            // Переход только при смене ФАЗЫ; правки данных внутри Online обновляют тело на месте.
            contentKey = { it.phaseKey() },
            transitionSpec = {
                if (reducedMotion) {
                    (fadeIn(snap()) togetherWith fadeOut(snap()))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                } else {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(120)))
                        .using(
                            SizeTransform(clip = false) { _, _ ->
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            }
                        )
                }
            },
            label = "hub_status",
            modifier = Modifier.fillMaxWidth()
        ) { s ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                when (s) {
                    is ServerHubStatus.Online -> OnlineContent(s)
                    ServerHubStatus.Connecting -> BusyContent(stringResource(R.string.pill_connecting))
                    is ServerHubStatus.Working -> BusyContent(s.action)
                    ServerHubStatus.Failed -> FailedContent(onRetry)
                    ServerHubStatus.Offline -> OfflineContent(onActivate)
                    ServerHubStatus.NotPaired -> NotPairedContent()
                    ServerHubStatus.SyncOff -> StatusHero(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.hub_sync_off)
                    )
                }
            }
        }
    }
}

/** Стабильный ключ фазы — AnimatedContent анимирует только при его смене. */
private fun ServerHubStatus.phaseKey(): Int = when (this) {
    ServerHubStatus.Offline -> 0
    ServerHubStatus.NotPaired -> 1
    ServerHubStatus.Connecting -> 2
    is ServerHubStatus.Working -> 3
    is ServerHubStatus.Online -> 4
    ServerHubStatus.Failed -> 5
    ServerHubStatus.SyncOff -> 6
}

/** Live-фазы зависят от живого SSH/ядра — при sync OFF схлопываются в [ServerHubStatus.SyncOff]. */
private fun ServerHubStatus.isLivePhase(): Boolean = when (this) {
    is ServerHubStatus.Online, ServerHubStatus.Connecting,
    is ServerHubStatus.Working, ServerHubStatus.Failed -> true
    else -> false
}

/**
 * Hero-строка статуса: живой индикатор-«ореол» слева + заголовок фазы и опц. подзаголовок.
 * Статус озвучивается один раз тут (никакого дублирующего чипа). [liveRegion] — TalkBack
 * объявляет смену фазы. Заголовок onSurface (читаемость), цвет несёт индикатор.
 */
@Composable
private fun StatusHero(
    color: Color,
    title: String,
    subtitle: String? = null,
    pulsing: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        StatusIndicator(color, pulsing)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Цветная точка-«ореол» статуса. В busy-фазах ореол мягко пульсирует (reduced-motion → статично). */
@Composable
private fun StatusIndicator(color: Color, pulsing: Boolean) {
    val reducedMotion = LocalReducedMotion.current
    val active = pulsing && !reducedMotion
    val t = rememberInfiniteTransition(label = "halo")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "halo_phase"
    )
    val haloAlpha = if (active) lerp(0.06f, 0.30f, phase) else 0.16f
    val haloScale = if (active) lerp(0.80f, 1.18f, phase) else 1f
    val animatedColor by animateColorAsState(color, label = "indicator_color")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale }
                .background(animatedColor.copy(alpha = haloAlpha), CircleShape)
        )
        Box(modifier = Modifier.size(12.dp).background(animatedColor, CircleShape))
    }
}

/** Busy-фаза (подключение/серверное действие): hero с пульсом + тонкий wavy-индикатор. */
@Composable
private fun BusyContent(title: String) {
    StatusHero(
        color = MaterialTheme.colorScheme.secondary,
        title = title,
        pulsing = true
    )
    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
}

/**
 * Живое ядро: hero несёт главный статус ОДИН раз (работает/остановлен/не установлено).
 * Детальные теги (SSH, режим, обфускация, версия) тут не дублируем — они живут в
 * «Отладочной информации» (NerdScreen → «Состояние ядра»).
 */
@Composable
private fun OnlineContent(status: ServerHubStatus.Online) {
    val ext = MaterialTheme.extendedColorScheme
    val (color, title) = when {
        !status.installed -> ext.warning to stringResource(R.string.hub_not_installed)
        status.running -> ext.success to stringResource(R.string.hub_server_running)
        else -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.hub_server_stopped)
    }
    StatusHero(color = color, title = title)
}

/**
 * Ошибка подключения/команды — hero + «Переподключиться». Конкретную причину НЕ показываем:
 * это внутренняя java/SSH-ошибка, а не серверная — юзеру бесполезна.
 */
@Composable
private fun FailedContent(onRetry: () -> Unit) {
    StatusHero(
        color = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.hub_connect_failed)
    )
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.reconnect))
    }
}

/** Неактивный сервер — hero + «Сделать активным». Адреса не дублируем: они в шапке хаба. */
@Composable
private fun OfflineContent(onActivate: () -> Unit) {
    StatusHero(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(R.string.pill_offline),
        subtitle = stringResource(R.string.server_inactive_desc)
    )
    Button(onClick = onActivate, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.make_active))
    }
}

/**
 * SSH не настроен — только hero-статус. Штатное состояние ручной настройки
 * (вкладка «+» → «Ручная настройка»): клиентские настройки доступны, серверное
 * управление — нет. SSH задаётся только мастером, дозавести его нельзя.
 */
@Composable
private fun NotPairedContent() {
    StatusHero(
        color = MaterialTheme.extendedColorScheme.warning,
        title = stringResource(R.string.pill_not_paired),
        subtitle = stringResource(R.string.not_paired_hint)
    )
}
