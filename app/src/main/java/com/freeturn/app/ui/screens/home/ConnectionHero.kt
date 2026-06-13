@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.freeturn.app.R
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.domain.ProxyState
import com.freeturn.app.ui.theme.Spacing

/**
 * Герой главного экрана: кнопка-тоггл с морфом MaterialShapes по состоянию
 * (idle-печенька → вращающееся «солнце» подключения → круг работы → burst ошибки),
 * анимированная строка статуса и пилюля счётчика/uptime.
 * Чистый компонент: состояние и колбэк приходят снаружи.
 */
@Composable
internal fun ConnectionHero(
    state: ProxyState,
    uptimeText: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = state.heroKind()
    val reducedMotion = LocalReducedMotion.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeroToggleButton(
            kind = kind,
            reducedMotion = reducedMotion,
            onClick = onToggle
        )

        Spacer(Modifier.height(20.dp))

        StatusLabel(state = state, reducedMotion = reducedMotion)

        Spacer(Modifier.height(10.dp))

        StatsPill(state = state, kind = kind, uptimeText = uptimeText)
    }
}

// Состояния героя. CaptchaRequired — это Busy: прокси под капчей работает,
// текст про капчу несёт строка статуса.
private enum class HeroKind { Idle, Busy, Running, Error }

private fun ProxyState.heroKind(): HeroKind = when (this) {
    is ProxyState.Running -> HeroKind.Running
    is ProxyState.Starting, is ProxyState.Connecting,
    is ProxyState.CaptchaRequired -> HeroKind.Busy
    is ProxyState.Error -> HeroKind.Error
    is ProxyState.Idle -> HeroKind.Idle
}

// Кнопка-тоггл

@Composable
private fun HeroToggleButton(
    kind: HeroKind,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val extended = MaterialTheme.extendedColorScheme
    val buttonLabel = when (kind) {
        HeroKind.Busy -> stringResource(R.string.proxy_connecting)
        HeroKind.Running -> stringResource(R.string.proxy_active_stop)
        HeroKind.Error -> stringResource(R.string.proxy_error_restart)
        HeroKind.Idle -> stringResource(R.string.start_proxy)
    }
    val containerColor by animateColorAsState(
        targetValue = when (kind) {
            HeroKind.Running -> extended.successContainer
            HeroKind.Error -> MaterialTheme.colorScheme.errorContainer
            HeroKind.Busy -> MaterialTheme.colorScheme.secondaryContainer
            HeroKind.Idle -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(500),
        label = "btn_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when (kind) {
            HeroKind.Running -> extended.onSuccessContainer
            HeroKind.Error -> MaterialTheme.colorScheme.onErrorContainer
            HeroKind.Busy -> MaterialTheme.colorScheme.onSecondaryContainer
            HeroKind.Idle -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(500),
        label = "btn_fg"
    )
    val scale by animateFloatAsState(
        targetValue = if (kind == HeroKind.Busy) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale"
    )

    // Морф формы кнопки между состояниями.
    val heroShape = rememberMorphingShape(
        target = when (kind) {
            HeroKind.Idle -> MaterialShapes.Cookie12Sided
            HeroKind.Busy -> MaterialShapes.Sunny
            HeroKind.Running -> MaterialShapes.Circle
            HeroKind.Error -> MaterialShapes.SoftBurst
        },
        reducedMotion = reducedMotion
    )

    // Медленное вращение «солнца», пока идёт подключение.
    val rotation = if (kind == HeroKind.Busy && !reducedMotion) {
        val spin = rememberInfiniteTransition(label = "hero_spin")
        val angle by spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing)),
            label = "hero_angle"
        )
        angle
    } else 0f

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(148.dp)
            .scale(scale)
            .graphicsLayer { rotationZ = rotation }
            .semantics { contentDescription = buttonLabel },
        shape = heroShape,
        color = containerColor,
        tonalElevation = if (kind == HeroKind.Running) 3.dp else 1.dp
    ) {
        Box(
            // Контр-вращение: крутится только фигура, контент стоит на месте.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -rotation },
            contentAlignment = Alignment.Center
        ) {
            if (reducedMotion) {
                HeroIcon(kind = kind, tint = contentColor)
            } else {
                AnimatedContent(
                    targetState = kind,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.6f, animationSpec = tween(260)))
                            .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120)))
                    },
                    label = "hero_icon"
                ) { k ->
                    HeroIcon(kind = k, tint = contentColor)
                }
            }
        }
    }
}

@Composable
private fun HeroIcon(kind: HeroKind, tint: Color) {
    when (kind) {
        // Expressive shape-shifting лоадер вместо кругового индикатора.
        HeroKind.Busy -> LoadingIndicator(color = tint, modifier = Modifier.size(64.dp))
        HeroKind.Running -> Icon(
            painterResource(R.drawable.check_circle_24px), null,
            Modifier.size(52.dp), tint = tint
        )
        HeroKind.Error -> Icon(
            painterResource(R.drawable.error_24px), null,
            Modifier.size(52.dp), tint = tint
        )
        HeroKind.Idle -> Icon(
            painterResource(R.drawable.play_arrow_24px), null,
            Modifier.size(52.dp), tint = tint
        )
    }
}

// Строка статуса

@Composable
private fun StatusLabel(state: ProxyState, reducedMotion: Boolean) {
    val label = when (state) {
        is ProxyState.Running -> stringResource(R.string.proxy_active)
        is ProxyState.Starting, is ProxyState.Connecting -> stringResource(R.string.proxy_connecting)
        is ProxyState.Error -> state.message
        is ProxyState.CaptchaRequired -> stringResource(R.string.proxy_captcha_required)
        else -> stringResource(R.string.proxy_press_to_start)
    }
    val color by animateColorAsState(
        targetValue = when (state) {
            is ProxyState.Running -> MaterialTheme.extendedColorScheme.success
            is ProxyState.Error, is ProxyState.CaptchaRequired -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(400),
        label = "status_color"
    )
    val text: @Composable (String) -> Unit = { value ->
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xxxl)
        )
    }
    if (reducedMotion) {
        text(label)
    } else {
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 })
                    .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
            },
            label = "status_label"
        ) { value -> text(value) }
    }
}

// Пилюля счётчика потоков и uptime

@Composable
private fun StatsPill(state: ProxyState, kind: HeroKind, uptimeText: String?) {
    val counts = when (state) {
        is ProxyState.Running ->
            if (state.total > 0) "${state.active}/${state.total}" else "${state.active}"
        is ProxyState.Connecting ->
            if (state.total > 0) "${state.active}/${state.total}" else null
        else -> null
    }
    val pillText = listOfNotNull(counts, uptimeText)
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() && (kind == HeroKind.Running || kind == HeroKind.Busy) }

    // Слот фиксированной высоты: появление пилюли не сдвигает кнопку и статус.
    Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
        // Последний непустой текст — чтобы пилюля не пустела в кадрах exit-анимации.
        var lastText by remember { mutableStateOf("") }
        if (pillText != null) lastText = pillText
        AnimatedVisibility(
            visible = pillText != null,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.8f, animationSpec = tween(260)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120))
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = lastText,
                    // tnum: тикающий таймер и счётчик не «дышат» по ширине.
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }
        }
    }
}

// Морф-механика

/**
 * Shape, перетекающий из предыдущей фигуры в [target] спрингом slowSpatial из
 * MotionScheme.expressive. MaterialShapes нормализованы в единичный квадрат —
 * масштабируем path под размер компонента. При reduced-motion — мгновенно.
 */
@Composable
private fun rememberMorphingShape(target: RoundedPolygon, reducedMotion: Boolean): Shape {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    if (to !== target) {
        from = to
        to = target
    }
    val morph = remember(from, to) { Morph(from, to) }
    val progress = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(to) {
        if (from !== to) {
            progress.snapTo(0f)
            if (reducedMotion) progress.snapTo(1f) else progress.animateTo(1f, spec)
        }
    }
    return MorphShape(morph, progress.value)
}

private class MorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f)).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
