@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.sshsetup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.domain.SshConnectionState
import com.freeturn.app.ui.util.HapticUtil
import com.freeturn.app.ui.components.InlineErrorCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SshFormFields
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.theme.Spacing
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel

@Composable
fun SshSetupScreen(
    serverViewModel: ServerViewModel,
    settingsViewModel: SettingsViewModel,
    onConnected: () -> Unit,
    onBack: () -> Unit
) {
    // Экран не сервер-скоупный (правит активный SSH). Все серверы удалены, пока экран
    // висел в стеке вкладки - выходим назад: иначе форма пишет в осиротевший конфиг.
    val snapshot by settingsViewModel.serversSnapshot.collectAsStateWithLifecycle()
    if (snapshot.loaded && snapshot.list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val sshState by serverViewModel.sshState.collectAsStateWithLifecycle()
    val savedConfig by serverViewModel.sshConfig.collectAsStateWithLifecycle()

    var ip by rememberSaveable(savedConfig.ip) { mutableStateOf(savedConfig.ip) }
    var port by rememberSaveable(savedConfig.port) { mutableStateOf(savedConfig.port.toString()) }
    var username by rememberSaveable(savedConfig.username) { mutableStateOf(savedConfig.username) }
    var password by remember { mutableStateOf(savedConfig.password) }
    var authType by rememberSaveable(savedConfig.authType) { mutableStateOf(savedConfig.authType) }
    var sshKey by rememberSaveable(savedConfig.sshKey) { mutableStateOf(savedConfig.sshKey) }

    // Переходим только если подключение было установлено ПОСЛЕ открытия экрана.
    // Если sshState уже Connected при входе (пользователь хочет изменить настройки) -
    // не перенаправляем автоматически, ждём явного нажатия "Подключиться".
    var sawNonConnected by remember { mutableStateOf(sshState !is SshConnectionState.Connected) }
    LaunchedEffect(sshState) {
        if (sshState !is SshConnectionState.Connected) sawNonConnected = true
        if (sawNonConnected && sshState is SshConnectionState.Connected) onConnected()
    }

    val isConnecting = sshState is SshConnectionState.Connecting
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Подсветка незаполненных полей - включается тапом по FAB на невалидной форме.
    var highlightErrors by rememberSaveable { mutableStateOf(false) }
    val formValid = ip.isNotBlank() &&
        port.toIntOrNull()?.let { it in 1..65535 } == true &&
        when (authType) {
            SshConfig.AUTH_SSH_KEY -> sshKey.isNotBlank()
            else -> password.isNotBlank()
        }
    val showConnectFab = !isConnecting

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.connect_to_server)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                        onBack()
                    }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showConnectFab,
                enter = if (reducedMotion) EnterTransition.None else scaleIn() + fadeIn(),
                exit = if (reducedMotion) ExitTransition.None else scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Невалидная форма не прячет кнопку: тап подсвечивает поля.
                        if (!formValid) {
                            HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                            highlightErrors = true
                        } else {
                            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                            highlightErrors = false
                            serverViewModel.connectSsh(
                                SshConfig(
                                    ip = ip.trim(),
                                    port = port.toIntOrNull() ?: 22,
                                    username = username.trim(),
                                    password = password,
                                    authType = authType,
                                    sshKey = sshKey
                                )
                            )
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.host_24px), contentDescription = null) },
                    text = { Text(stringResource(R.string.connect_btn)) }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
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
                if (!isConnecting) {
                    SshFormFields(
                        ip = ip, onIpChange = { ip = it },
                        port = port, onPortChange = { port = it },
                        username = username, onUsernameChange = { username = it },
                        password = password, onPasswordChange = { password = it },
                        authType = authType, onAuthTypeChange = { authType = it },
                        sshKey = sshKey, onSshKeyChange = { sshKey = it },
                        showErrors = highlightErrors
                    )
                    // Ошибка подключения - тональная карточка в тон ошибки.
                    (sshState as? SshConnectionState.Error)?.let { InlineErrorCard(it.message) }
                } else {
                    ConnectionProgressCard(step = stringResource(R.string.ssh_connecting))
                }

                // Клиренс под плавающую кнопку, чтобы FAB не перекрывал нижнее поле.
                val clearance by animateDpAsState(
                    targetValue = if (showConnectFab) 88.dp else 24.dp,
                    animationSpec = if (reducedMotion) snap() else spring(),
                    label = "fab_clearance"
                )
                Spacer(Modifier.height(clearance))
            }
        }
    }
}
