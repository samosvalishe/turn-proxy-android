@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.CoreArgs
import com.freeturn.app.data.ObfProfile
import com.freeturn.app.data.Server
import com.freeturn.app.domain.server.ServerCommand
import com.freeturn.app.domain.server.ServerOptions
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SettingsBackButton
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.SettingsGroupItem
import com.freeturn.app.ui.components.SettingsSwitchRow
import com.freeturn.app.ui.util.redact
import com.freeturn.app.viewmodel.ServerHubStatus
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.ui.theme.Spacing

/** Тег релиза приходит и как "1.0.3", и как "v1.0.3" — нормализуем без "vv". */
private fun versionLabel(version: String): String = "v${version.removePrefix("v")}"

/**
 * «Отладочная информация» — отдельный экран (вход из хаба, гейт по nerdMode): отладочные
 * per-server флаги (подробные логи, показ логов) + состояние ядра + журнал сервера
 * и SSH-лог. Потоки логов собираем только здесь — хаб на них не подписан.
 */
@Composable
fun NerdScreen(
    serverId: String,
    settingsViewModel: SettingsViewModel,
    serverViewModel: ServerViewModel,
    onBack: () -> Unit
) {
    val snapshot by settingsViewModel.serversSnapshot.collectAsStateWithLifecycle()
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val coreStatus by serverViewModel.hubStatus.collectAsStateWithLifecycle()
    val server = snapshot.list.firstOrNull { it.id == serverId }
    val isActive = snapshot.activeId == serverId

    // Сервер удалён — выходим назад (как в хабе).
    if (snapshot.loaded && server == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // hubStatus принадлежит активному серверу — для неактивного живого статуса нет.
    val online = if (isActive) coreStatus as? ServerHubStatus.Online else null

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nerd_section_title)) },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
        // Экран всегда внутри NavigationSuite — нижний бар сам держит навбар-инсет.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
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
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                if (server != null) {
                    NerdContent(
                        server = server,
                        online = online,
                        privacyMode = privacyMode,
                        settingsViewModel = settingsViewModel,
                        serverViewModel = serverViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun NerdContent(
    server: Server,
    online: ServerHubStatus.Online?,
    privacyMode: Boolean,
    settingsViewModel: SettingsViewModel,
    serverViewModel: ServerViewModel
) {
    val context = LocalContext.current
    val serverId = server.id
    val client = server.client
    val sshLog by serverViewModel.sshLog.collectAsStateWithLifecycle()
    val journalLoading by serverViewModel.journalLoading.collectAsStateWithLifecycle()

    // Per-server отладочные флаги. updateServerClient разводит active/inactive и
    // применяет logsEnabled живьём — отдельные VM-сеттеры не нужны.
    SettingsGroup {
        SettingsGroupItem(0, 2) {
            SettingsSwitchRow(
                title = stringResource(R.string.debug_mode),
                subtitle = stringResource(R.string.debug_mode_desc),
                checked = client.debugMode,
                onCheckedChange = { v ->
                    settingsViewModel.updateServerClient(serverId) { it.copy(debugMode = v) }
                }
            )
        }
        SettingsGroupItem(1, 2) {
            SettingsSwitchRow(
                title = stringResource(R.string.logs_enabled),
                subtitle = stringResource(R.string.logs_enabled_desc),
                checked = client.logsEnabled,
                onCheckedChange = { v ->
                    settingsViewModel.updateServerClient(serverId) { it.copy(logsEnabled = v) }
                }
            )
        }
    }

    // Живое состояние ядра — только при живом SSH (online != null).
    if (online != null) CoreStateCard(online, privacyMode)

    // Параметры запуска реконструируются из конфига — видны всегда, даже оффлайн.
    LaunchParamsCard(server, privacyMode)

    // Единый SSH-лог: копит весь вывод команд (включая ошибки сопряжения и server.log,
    // который тянется кнопкой ниже). Показываем при живом SSH (нужна кнопка журнала)
    // либо если в логе уже что-то есть.
    if (online != null || sshLog.isNotEmpty()) {
        SshLogCard(
            lines = sshLog,
            canFetchJournal = online != null,
            journalLoading = journalLoading,
            onFetchJournal = {
                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                serverViewModel.fetchServerLogs()
            },
            onClear = {
                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                serverViewModel.clearSshLog()
            }
        )
    }
}

/** Сырое состояние ядра: подписанные строки «ключ — значение» вместо безымянных чипов. */
@Composable
private fun CoreStateCard(online: ServerHubStatus.Online, privacyMode: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(R.string.nerd_core_state), style = MaterialTheme.typography.titleMedium)
            // Одно логичное состояние: «работает» уже подразумевает «установлено».
            // Не установлено → остановлен → работает.
            val stateRes = when {
                !online.installed -> R.string.nerd_state_not_installed
                !online.running -> R.string.nerd_state_stopped
                else -> R.string.nerd_state_running
            }
            NerdStateRow(stringResource(R.string.nerd_state_label), stringResource(stateRes))
            online.version?.takeIf { it.isNotBlank() }?.let {
                NerdStateRow(stringResource(R.string.nerd_version_label), versionLabel(it), mono = true)
            }
            NerdStateRow(stringResource(R.string.server_has_ssh), online.sshIp.redact(privacyMode), mono = true)
        }
    }
}

@Composable
private fun NerdStateRow(label: String, value: String, mono: Boolean = false) {
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

/**
 * Параметры запуска ядра — реальные argv, которыми стартуют серверный и клиентский
 * бинарники. Реконструируются из конфига (тот же [CoreArgs.client], что и в движке;
 * серверный — через [ServerCommand]), поэтому видны всегда, даже без живого SSH.
 * Секреты (obf-ключ, vk-ссылка, адрес пира/TURN) маскируются под privacyMode.
 */
@Composable
private fun LaunchParamsCard(server: Server, privacyMode: Boolean) {
    val serverCmd = remember(server, privacyMode) { serverCommandLine(server, privacyMode) }
    val clientCmd = remember(server, privacyMode) { clientCommandLine(server, privacyMode) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text(stringResource(R.string.nerd_launch_params), style = MaterialTheme.typography.titleMedium)
            LaunchParamBlock(stringResource(R.string.nerd_launch_server), serverCmd)
            LaunchParamBlock(stringResource(R.string.nerd_launch_client), clientCmd)
        }
    }
}

@Composable
private fun LaunchParamBlock(label: String, commandLine: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LogPane(commandLine)
    }
}

/** Флаги клиента (-flag value), значение которых прячем под privacyMode. */
private val CLIENT_SECRET_FLAGS = setOf("-peer", "-link", "-obf-key", "-turn", "-client-id")

/** Командная строка клиентского ядра (как в движке) с маской секретов. */
private fun clientCommandLine(server: Server, privacy: Boolean): String {
    val argv = CoreArgs.client(server.client, server.opts)
    val sb = StringBuilder("freeturn")
    var i = 0
    while (i < argv.size) {
        val tok = argv[i]
        sb.append(' ').append(tok)
        if (tok in CLIENT_SECRET_FLAGS && i + 1 < argv.size) {
            sb.append(' ').append(argv[i + 1].redact(privacy))
            i += 2
        } else {
            i += 1
        }
    }
    return sb.toString()
}

/** Командная строка серверного ядра (как уходит по SSH в free-turn-control.sh). */
private fun serverCommandLine(server: Server, privacy: Boolean): String {
    val opts = ServerOptions(
        listen = server.proxyListen,
        connect = server.proxyConnect,
        tcpMode = server.client.tcpForward,
        obfProfile = if (server.opts.obfEnabled) server.opts.obfProfile else ObfProfile.NONE,
        obfKey = if (server.opts.obfEnabled) server.opts.obfKey else ""
    )
    // Серверные флаги в форме --flag=value: маскируем хвост после '=' у секретов.
    val shown = ServerCommand.Start(opts).toArgv().joinToString(" ") { tok ->
        val eq = tok.indexOf('=')
        if (eq > 0 && tok.substring(0, eq) == "--obf-key")
            tok.substring(0, eq + 1) + tok.substring(eq + 1).redact(privacy)
        else tok
    }
    return "free-turn-control.sh $shown"
}

/** Тёмная моноширинная вставка лога внутри карточек nerd-секции. */
@Composable
private fun LogPane(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, autoScroll: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        val scroll = rememberScrollState()
        if (autoScroll) {
            LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(scroll)
                .padding(Spacing.md)
        )
    }
}
