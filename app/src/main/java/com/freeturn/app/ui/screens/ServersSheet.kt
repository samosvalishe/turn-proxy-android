@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.ProfilesSnapshot
import com.freeturn.app.data.Provider
import com.freeturn.app.ui.components.EmptyServersState
import com.freeturn.app.ui.components.ServerRow
import com.freeturn.app.ui.components.settingsItemShape
import com.freeturn.app.viewmodel.SettingsViewModel

/**
 * Нижний лист серверов на главном: шапка активного сервера, бейдж провайдера и
 * сегментированный список (тот же expressive-стиль, что и «Настройки → Серверы»).
 * Лист только переключает и показывает: управление сервером — в его хабе (кнопка
 * настроек в строке, [onOpenServerSettings]), добавление — отдельным экраном.
 */
@Composable
fun ServersSheetContent(
    settingsViewModel: SettingsViewModel,
    snapshot: ProfilesSnapshot,
    privacyMode: Boolean = false,
    onCollapse: () -> Unit = {},
    onOpenServerSettings: (String) -> Unit = {}
) {
    val active = snapshot.active

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        // Шапка: имя активного сервера + адрес.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val profileName = when {
                active != null -> active.name
                snapshot.loaded -> stringResource(R.string.profile_unsaved_label)
                else -> ""
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(profileName) } },
                state = rememberTooltipState(),
                enableUserInput = active != null
            ) {
                Text(
                    profileName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Только адрес: провайдера несёт чип-бейдж ниже, дублировать его тут — шум.
            val sub = active?.let {
                (it.client.serverAddress.takeIf { a -> a.isNotBlank() }
                    ?: it.ssh.ip.takeIf { a -> a.isNotBlank() })?.redact(privacyMode)
            }
            // Подзаголовок рендерим всегда: пустой Text держит высоту строки, иначе без
            // профиля контент подъезжает и бейдж провайдера выглядывает из peek-зоны.
            Spacer(Modifier.height(4.dp))
            Text(
                sub.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(16.dp))

        // Чип-бейдж провайдера. Провайдер один (VK) — выбора нет, только индикация.
        ProviderChip(
            current = active?.client?.provider ?: Provider.VK,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(20.dp))

        if (!snapshot.loaded) {
            // Снимок ещё грузится - пустой вес-плейсхолдер, без мигания empty-state.
            Spacer(Modifier.weight(1f))
        } else if (snapshot.list.isEmpty()) {
            EmptyServersState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Text(
                stringResource(R.string.profiles_servers_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(snapshot.list, key = { _, p -> p.id }) { index, p ->
                    val isActive = snapshot.activeId == p.id
                    val sub = listOfNotNull(
                        p.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode),
                        p.ssh.ip.takeIf { it.isNotBlank() }?.let { "SSH ${it.redact(privacyMode)}" }
                    ).joinToString(" · ").ifBlank { "—" }
                    ServerRow(
                        name = p.name,
                        subtitle = sub,
                        isActive = isActive,
                        shape = settingsItemShape(index, snapshot.list.size),
                        // Лист сам на surfaceContainerLow — строкам нужен контраст повыше.
                        inactiveContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            if (!isActive) {
                                settingsViewModel.applyProfile(p.id)
                                onCollapse()
                            }
                        },
                        trailing = {
                            IconButton(onClick = { onOpenServerSettings(p.id) }) {
                                Icon(
                                    painterResource(R.drawable.settings_outlined_24px),
                                    contentDescription = stringResource(R.string.nav_settings),
                                    tint = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Чип-бейдж провайдера TURN-creds: Sunny-бейдж на primary + имя. Статичный —
 * провайдер пока один, дропдаун только изображал выбор и шумел стрелкой.
 */
@Composable
private fun ProviderChip(
    current: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialShapes.Sunny.toShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.nearby_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                providerLabel(current),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun providerLabel(value: String): String = when (value) {
    Provider.VK -> stringResource(R.string.provider_vk)
    else -> value
}

