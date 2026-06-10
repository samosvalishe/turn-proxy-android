@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.components.SettingsEntryRow
import com.freeturn.app.ui.components.SettingsGroup
import com.freeturn.app.ui.components.SettingsGroupItem
import com.freeturn.app.ui.components.SettingsRowIcon
import com.freeturn.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Экран «Добавить сервер» (вкладка «+»). Работает только self-hosted сценарий:
 * создаёт пустой сервер и уводит в его хаб. Импорт по ссылке, из файла и по QR
 * ещё не реализованы — показаны выключенными с бейджем «Скоро».
 */
@Composable
fun AddServerScreen(
    settingsViewModel: SettingsViewModel,
    onServerCreated: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val defaultName = stringResource(R.string.add_default_server_name)
    val scope = rememberCoroutineScope()
    // Защита от даблтапа: пока создание в полёте, строка выключена.
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.add_server_title)) },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SectionLabel(stringResource(R.string.add_paste_section))
                Spacer(Modifier.height(8.dp))
                PasteLinkField()

                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.add_methods_section))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    SettingsGroupItem(0, 3) {
                        SettingsEntryRow(
                            iconRes = R.drawable.host_24px,
                            title = stringResource(R.string.add_self_hosted_title),
                            subtitle = stringResource(R.string.add_self_hosted_desc),
                            enabled = !creating,
                            onClick = {
                                creating = true
                                scope.launch {
                                    try {
                                        onServerCreated(settingsViewModel.addServer(defaultName))
                                    } finally {
                                        creating = false
                                    }
                                }
                            }
                        )
                    }
                    SettingsGroupItem(1, 3) {
                        SoonMethodRow(
                            iconRes = R.drawable.description_24px,
                            title = stringResource(R.string.add_from_file_title),
                            subtitle = stringResource(R.string.add_from_file_desc)
                        )
                    }
                    SettingsGroupItem(2, 3) {
                        SoonMethodRow(
                            iconRes = R.drawable.qr_code_scanner_24px,
                            title = stringResource(R.string.add_from_qr_title),
                            subtitle = stringResource(R.string.add_from_qr_desc)
                        )
                    }
                }
            }
        }
    }
}

/** Поле импорта по ссылке. Неактивно (контент на MD3 disabled 0.38): парсера ссылок ещё нет. */
@Composable
private fun PasteLinkField() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painterResource(R.drawable.link_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
            Text(
                stringResource(R.string.add_paste_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {}, enabled = false) {
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
