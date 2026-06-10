@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.ProxyState

/**
 * Герой главного экрана: большая кнопка-тоггл прокси + строка статуса
 * (состояние, счётчик потоков N/M, uptime). Чистый компонент: состояние
 * и колбэк приходят снаружи.
 */
@Composable
internal fun ConnectionHero(
    state: ProxyState,
    uptimeText: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProxyToggleButton(state = state, onClick = onToggle)

        Spacer(Modifier.height(24.dp))

        Text(
            text = statusLine(state, uptimeText),
            // "tnum" — tabular numbers: все цифры одинаковой ширины. Без него
            // тикающий таймер и меняющийся счётчик N/M сдвигают остальной текст.
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = when (state) {
                is ProxyState.Running -> MaterialTheme.extendedColorScheme.success
                is ProxyState.Error -> MaterialTheme.colorScheme.error
                is ProxyState.CaptchaRequired -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun statusLine(state: ProxyState, uptimeText: String?): String = when (state) {
    is ProxyState.Running -> {
        val base = stringResource(R.string.proxy_active)
        val counts = if (state.total > 0) "${state.active}/${state.total}" else "${state.active}"
        if (uptimeText != null) "$base — $counts · $uptimeText"
        else "$base — $counts"
    }
    is ProxyState.Connecting -> {
        val base = stringResource(R.string.proxy_connecting)
        val counts = if (state.total > 0) " — ${state.active}/${state.total}" else ""
        // Таймер всё ещё показываем: сессия не завершилась, просто
        // потоки временно отвалились и ядро переподключается.
        if (uptimeText != null) "$base$counts · $uptimeText" else "$base$counts"
    }
    is ProxyState.Starting -> stringResource(R.string.proxy_connecting)
    is ProxyState.Error -> state.message
    is ProxyState.CaptchaRequired -> stringResource(R.string.proxy_captcha_required)
    else -> stringResource(R.string.proxy_press_to_start)
}

@Composable
private fun ProxyToggleButton(state: ProxyState, onClick: () -> Unit) {
    val extended = MaterialTheme.extendedColorScheme
    val buttonLabel = when (state) {
        is ProxyState.Starting, is ProxyState.Connecting -> stringResource(R.string.proxy_connecting)
        is ProxyState.Running -> stringResource(R.string.proxy_active_stop)
        is ProxyState.Error -> stringResource(R.string.proxy_error_restart)
        else -> stringResource(R.string.start_proxy)
    }
    val containerColor by animateColorAsState(
        targetValue = when (state) {
            is ProxyState.Running -> extended.successContainer
            is ProxyState.Error -> MaterialTheme.colorScheme.errorContainer
            is ProxyState.Starting, is ProxyState.Connecting ->
                MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(500),
        label = "btn_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when (state) {
            is ProxyState.Running -> extended.onSuccessContainer
            is ProxyState.Error -> MaterialTheme.colorScheme.onErrorContainer
            is ProxyState.Starting, is ProxyState.Connecting ->
                MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(500),
        label = "btn_fg"
    )
    val scale by animateFloatAsState(
        targetValue = if (state is ProxyState.Starting || state is ProxyState.Connecting) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(148.dp)
            .scale(scale)
            .clip(CircleShape)
            .semantics { contentDescription = buttonLabel },
        shape = CircleShape,
        color = containerColor,
        tonalElevation = if (state is ProxyState.Running) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is ProxyState.Starting, is ProxyState.Connecting ->
                    CircularWavyProgressIndicator(color = contentColor)
                is ProxyState.Running -> Icon(
                    painterResource(R.drawable.check_circle_24px), stringResource(R.string.proxy_active_stop),
                    Modifier.size(52.dp), tint = contentColor
                )
                is ProxyState.Error -> Icon(
                    painterResource(R.drawable.error_24px), stringResource(R.string.proxy_error_restart),
                    Modifier.size(52.dp), tint = contentColor
                )
                else -> Icon(
                    painterResource(R.drawable.play_arrow_24px), stringResource(R.string.start_proxy),
                    Modifier.size(52.dp), tint = contentColor
                )
            }
        }
    }
}
