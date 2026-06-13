@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.share.FreeturnLink
import com.freeturn.app.domain.LinkImportBus
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.SettingsGroupItem
import com.freeturn.app.ui.components.SettingsRowIcon
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.freeturn.app.ui.theme.Spacing

/**
 * Экран «Добавить сервер» (вкладка «+»). Self-hosted уводит в мастер установки
 * (SSH → опросник → установка) — сервер создаётся только после его успешного
 * завершения. Ручная настройка создаёт пустой сервер по имени — дальше пользователь
 * настраивает его сам в хабе. Импорт — по freeturn://-ссылке (вставка из буфера,
 * QR-сканер); из файла ещё не реализован.
 */
@Composable
fun AddServerScreen(
    onSelfHosted: () -> Unit,
    onManualCreate: (String) -> Unit,
    onScanQr: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val linkBus = koinInject<LinkImportBus>()
    val snackbarHostState = remember { SnackbarHostState() }
    val notLinkMessage = stringResource(R.string.add_paste_not_link)
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.add_server_title)) },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                PasteLinkField(
                    onPaste = {
                        val clip = context.getSystemService(ClipboardManager::class.java)
                            ?.primaryClip?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (FreeturnLink.looksLikeLink(clip)) {
                            linkBus.offer(clip)
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(notLinkMessage) }
                        }
                    }
                )

                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.add_methods_section))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    SettingsGroupItem(0, 4) {
                        SettingsEntryRow(
                            iconRes = R.drawable.host_24px,
                            title = stringResource(R.string.add_self_hosted_title),
                            subtitle = stringResource(R.string.add_self_hosted_desc),
                            onClick = onSelfHosted
                        )
                    }
                    SettingsGroupItem(1, 4) {
                        SettingsEntryRow(
                            iconRes = R.drawable.tune_24px,
                            title = stringResource(R.string.add_manual_title),
                            subtitle = stringResource(R.string.add_manual_desc),
                            onClick = { showManualDialog = true }
                        )
                    }
                    SettingsGroupItem(2, 4) {
                        SettingsEntryRow(
                            iconRes = R.drawable.qr_code_scanner_24px,
                            title = stringResource(R.string.add_from_qr_title),
                            subtitle = stringResource(R.string.add_from_qr_desc),
                            onClick = onScanQr
                        )
                    }
                    SettingsGroupItem(3, 4) {
                        SoonMethodRow(
                            iconRes = R.drawable.description_24px,
                            title = stringResource(R.string.add_from_file_title),
                            subtitle = stringResource(R.string.add_from_file_desc)
                        )
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualNameDialog(
            onCreate = { name ->
                showManualDialog = false
                onManualCreate(name)
            },
            onDismiss = { showManualDialog = false }
        )
    }
}

/** Диалог ручной настройки: только имя будущего сервера, остальное — потом в хабе. */
@Composable
private fun ManualNameDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_manual_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.server_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    onCreate(name)
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.add_manual_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Поле импорта по ссылке: кнопка читает буфер обмена и отдаёт его в LinkImportBus. */
@Composable
private fun PasteLinkField(onPaste: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                painterResource(R.drawable.link_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.add_paste_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                onPaste()
            }) {
                Text(stringResource(R.string.add_paste_button))
            }
        }
    }
}

/** Нереализованный способ добавления: строка на MD3 disabled 0.38 + бейдж «Скоро». */
@Composable
private fun SoonMethodRow(
    iconRes: Int,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        SettingsRowIcon(iconRes, enabled = false)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
        SoonBadge()
    }
}

@Composable
private fun SoonBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            stringResource(R.string.add_soon_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
        )
    }
}
