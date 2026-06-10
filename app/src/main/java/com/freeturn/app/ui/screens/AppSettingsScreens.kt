@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsBackButton
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.SettingsGroupItem
import com.freeturn.app.ui.components.SettingsRowIcon
import com.freeturn.app.ui.components.SettingsSwitchRow
import com.freeturn.app.ui.util.hapticClickable
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.UpdateState

// Экраны «Приложение» и «О проекте» — бывшее содержимое инфо-шита главного экрана,
// разложенное по разделам настроек на общих settings-блоках.

/** Версия приложения из PackageManager; "—" если недоступна. */
@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: Exception) {
            "—"
        }
    }
}

/** «Приложение»: интерфейсные тоггл-настройки, обновление и сброс. */
@Suppress("AssignedValueIsNeverRead") // showResetDialog пишется в лямбдах диалога
@Composable
fun AppScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val privacyMode by settingsViewModel.privacyMode.collectAsStateWithLifecycle()
    val dynamicTheme by settingsViewModel.dynamicTheme.collectAsStateWithLifecycle()
    val updateState by settingsViewModel.updateState.collectAsStateWithLifecycle()
    val appVersion = rememberAppVersion()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_app)) },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionLabel(stringResource(R.string.app_section_interface))
                SettingsGroup {
                    SettingsGroupItem(0, 2) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.privacy_mode_title),
                            subtitle = stringResource(R.string.privacy_mode_desc),
                            iconRes = R.drawable.visibility_off_24px,
                            checked = privacyMode,
                            onCheckedChange = { settingsViewModel.setPrivacyMode(it) }
                        )
                    }
                    SettingsGroupItem(1, 2) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.dynamic_theme_title),
                            subtitle = stringResource(R.string.dynamic_theme_desc),
                            iconRes = R.drawable.palette_24px,
                            checked = dynamicTheme,
                            onCheckedChange = { settingsViewModel.setDynamicTheme(it) }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.app_section_updates))
                UpdateCard(
                    state = updateState,
                    appVersion = appVersion,
                    onCheck = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        settingsViewModel.checkForUpdate()
                    },
                    onDownload = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        settingsViewModel.downloadUpdate()
                    },
                    onInstall = {
                        HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                        settingsViewModel.installUpdate()
                    }
                )

                SectionLabel(stringResource(R.string.app_section_reset))
                SettingsCard {
                    ResetRow(onClick = { showResetDialog = true })
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_all_settings_title)) },
            text = { Text(stringResource(R.string.reset_all_settings_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        settingsViewModel.resetAllSettings()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Карточка обновления. Двухэтажная (как SshLogCard): шапка — иконка, заголовок и статус
 * (анимированная смена), под ней действие во всю ширину — кнопка по состоянию либо
 * wavy-прогресс загрузки. В одну строку всё это не влезает на узких экранах.
 */
@Composable
private fun UpdateCard(
    state: UpdateState,
    appVersion: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val statusText = when (state) {
        is UpdateState.Idle -> stringResource(R.string.update_current_version, "v$appVersion")
        is UpdateState.Checking -> stringResource(R.string.update_checking)
        is UpdateState.Available -> stringResource(R.string.update_available, state.version)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.progress)
        is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready_desc_short)
        is UpdateState.NoUpdate -> stringResource(R.string.update_no_update)
        is UpdateState.Error -> stringResource(R.string.update_error, state.message)
    }

    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsRowIcon(R.drawable.cloud_download_24px)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_title), style = MaterialTheme.typography.bodyLarge)
                    AnimatedContent(
                        targetState = statusText,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "update_status"
                    ) { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state is UpdateState.Error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state is UpdateState.Downloading) {
                LinearWavyProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val action = when (state) {
                    is UpdateState.Available -> onDownload
                    is UpdateState.ReadyToInstall -> onInstall
                    else -> onCheck
                }
                FilledTonalButton(
                    onClick = action,
                    enabled = state !is UpdateState.Checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state is UpdateState.Checking) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            when (state) {
                                is UpdateState.Available -> stringResource(R.string.update_download)
                                is UpdateState.ReadyToInstall -> stringResource(R.string.update_install)
                                else -> stringResource(R.string.update_check)
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Опасная строка сброса: error-тинт иконки и заголовка, без trailing-шеврона. */
@Composable
private fun ResetRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hapticClickable(HapticUtil.Pattern.CLICK, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsRowIcon(
            R.drawable.delete_24px,
            container = MaterialTheme.colorScheme.errorContainer,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.reset_settings),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                stringResource(R.string.app_reset_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** «О проекте»: hero с лого в expressive-форме, версия, описание и ссылки. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appVersion = rememberAppVersion()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun open(url: String) {
        HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
        uriHandler.openUri(url)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = { SettingsBackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AboutHero(appVersion)

                SectionLabel(stringResource(R.string.about_links))
                SettingsGroup {
                    SettingsGroupItem(0, 3) {
                        SettingsEntryRow(
                            iconRes = R.drawable.code_24px,
                            title = stringResource(R.string.android_client),
                            subtitle = "samosvalishe/turn-proxy-android",
                            trailingRes = R.drawable.open_in_new_24px,
                            trailingTint = MaterialTheme.colorScheme.primary,
                            onClick = { open("https://github.com/samosvalishe/turn-proxy-android") }
                        )
                    }
                    SettingsGroupItem(1, 3) {
                        SettingsEntryRow(
                            iconRes = R.drawable.terminal_24px,
                            title = stringResource(R.string.proxy_core),
                            subtitle = "samosvalishe/free-turn-proxy",
                            trailingRes = R.drawable.open_in_new_24px,
                            trailingTint = MaterialTheme.colorScheme.primary,
                            onClick = { open("https://github.com/samosvalishe/free-turn-proxy") }
                        )
                    }
                    SettingsGroupItem(2, 3) {
                        SettingsEntryRow(
                            iconRes = R.drawable.public_24px,
                            title = stringResource(R.string.tg_channel),
                            trailingRes = R.drawable.open_in_new_24px,
                            trailingTint = MaterialTheme.colorScheme.primary,
                            onClick = { open("https://t.me/+53nh4UNiSv5lNTgy") }
                        )
                    }
                }
            }
        }
    }
}

/** Hero «О проекте»: ромб-лого в 9-гранной cookie (как пустой список серверов), имя, версия-пилюля, описание. */
@Composable
private fun AboutHero(appVersion: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(112.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painterResource(R.drawable.logo_diamond_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.turn_proxy_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "v$appVersion",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
