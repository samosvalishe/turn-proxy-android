@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.serverdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.freeturn.app.R
import com.freeturn.app.ui.components.BusyProgressIndicator
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.extendedColorScheme
import com.freeturn.app.viewmodel.server.ServerHubState
import com.freeturn.app.ui.theme.Spacing

@Composable
internal fun ServerStatusCard(
    status: ServerHubState,
    syncOn: Boolean,
    onActivate: () -> Unit,
    onRetry: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    // Sync OFF - live-фазы ядра (online/connecting/working/failed) нерелевантны: клиент с
    // сервером не общается. Схлопываем их в нейтральную заглушку. Actionable-состояния
    // (Offline/NotPaired) остаются - это setup, а не live-статус.
    val effective = if (!syncOn && status.isLivePhase()) ServerHubState.SyncOff else status
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = effective,
            contentKey = { it.phaseKey() },
            transitionSpec = {
                if (reducedMotion) {
                    (fadeIn(snap()) togetherWith fadeOut(snap()))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                } else {
                    (fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec))
                        .using(SizeTransform(clip = false) { _, _ -> spatialSpec })
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
                    is ServerHubState.Online -> OnlineContent(s)
                    ServerHubState.Connecting -> BusyContent(stringResource(R.string.pill_connecting))
                    is ServerHubState.Working -> BusyContent(s.action)
                    ServerHubState.Failed -> FailedContent(onRetry)
                    ServerHubState.Offline -> OfflineContent(onActivate)
                    ServerHubState.NotPaired -> NotPairedContent()
                    ServerHubState.SyncOff -> StatusHero(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.hub_sync_off)
                    )
                }
            }
        }
    }
}

private fun ServerHubState.phaseKey(): Int = when (this) {
    ServerHubState.Offline -> 0
    ServerHubState.NotPaired -> 1
    ServerHubState.Connecting -> 2
    is ServerHubState.Working -> 3
    is ServerHubState.Online -> 4
    ServerHubState.Failed -> 5
    ServerHubState.SyncOff -> 6
}

private fun ServerHubState.isLivePhase(): Boolean = when (this) {
    is ServerHubState.Online, ServerHubState.Connecting,
    is ServerHubState.Working, ServerHubState.Failed -> true
    else -> false
}

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

@Composable
private fun BusyContent(title: String) {
    StatusHero(
        color = MaterialTheme.colorScheme.secondary,
        title = title,
        pulsing = true
    )
    BusyProgressIndicator()
}

@Composable
private fun OnlineContent(status: ServerHubState.Online) {
    val ext = MaterialTheme.extendedColorScheme
    val (color, title) = when {
        !status.installed -> ext.warning to stringResource(R.string.hub_not_installed)
        status.running -> ext.success to stringResource(R.string.hub_server_running)
        else -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.hub_server_stopped)
    }
    StatusHero(color = color, title = title)
}

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

@Composable
private fun NotPairedContent() {
    StatusHero(
        color = MaterialTheme.extendedColorScheme.warning,
        title = stringResource(R.string.pill_not_paired),
        subtitle = stringResource(R.string.not_paired_hint)
    )
}
