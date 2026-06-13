@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.server.Server
import com.freeturn.app.ui.components.EmptyServersState
import com.freeturn.app.ui.components.ServerRow
import com.freeturn.app.ui.components.SettingsBackButton
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.settingsItemShape
import com.freeturn.app.ui.util.redact
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.ui.theme.Spacing

/** Список добавленных серверов. Клик по серверу - его детальный экран. */
@Composable
fun ServersListScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenServer: (String) -> Unit
) {
    val snapshot by settingsViewModel.serversSnapshot.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_servers)) },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
        // Экран всегда внутри NavigationSuite - нижний бар сам держит навбар-инсет.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (snapshot.loaded && snapshot.list.isEmpty()) {
            EmptyServersState(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SettingsContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                // Активный сервер закрепляем сверху - быстрый доступ к нему.
                val ordered = remember(snapshot.list, snapshot.activeId) {
                    snapshot.list.sortedByDescending { it.id == snapshot.activeId }
                }
                SettingsGroup {
                    ordered.forEachIndexed { index, p ->
                        ServerListRow(
                            server = p,
                            isActive = snapshot.activeId == p.id,
                            privacyMode = privacyMode,
                            shape = settingsItemShape(index, ordered.size),
                            onClick = { onOpenServer(p.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerListRow(
    server: Server,
    isActive: Boolean,
    privacyMode: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    // Подзаголовок: адрес сервера + метка "SSH", если сопряжение настроено. Сам SSH-ip
    // в списке не показываем - достаточно факта наличия.
    val sub = listOfNotNull(
        server.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode),
        stringResource(R.string.server_has_ssh).takeIf { server.ssh.ip.isNotBlank() }
    ).joinToString(" · ").ifBlank { "-" }
    ServerRow(
        name = server.name,
        subtitle = sub,
        isActive = isActive,
        shape = shape,
        onClick = onClick,
        trailingIconRes = R.drawable.chevron_right_24px
    )
}

