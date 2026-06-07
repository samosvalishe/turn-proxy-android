@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.SplitTunnelMode
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.AppChoice
import com.freeturn.app.ui.components.AppIcon
import com.freeturn.app.ui.components.installedInternetApps
import com.freeturn.app.ui.components.toPackageSet
import com.freeturn.app.viewmodel.SettingsViewModel

/**
 * Общий модальный лист split-tunneling. Один источник для главного экрана и экрана
 * «Режим подключения» — обёртка [ModalBottomSheet] вокруг [SplitTunnelSheetContent].
 * Лист открывается сразу на полную высоту (skipPartiallyExpanded) и держит инсеты сам
 * (contentWindowInsets = 0), чтобы не «вырастать» над клавиатурой при фокусе поиска.
 */
@Composable
fun SplitTunnelModal(
    settingsViewModel: SettingsViewModel,
    mode: String,
    apps: String,
    locked: Boolean,
    onDismiss: () -> Unit,
    containerColor: Color = BottomSheetDefaults.ContainerColor
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        SplitTunnelSheetContent(
            settingsViewModel = settingsViewModel,
            mode = mode,
            apps = apps,
            locked = locked
        )
    }
}

/**
 * Лист split-tunneling с главного экрана. Один общий sheet: свитч вкл/выкл,
 * выбор режима (include/exclude), поиск и список приложений с иконками/чекбоксами.
 * Всё пишется сразу в активный профиль через ViewModel — без буфера и «сохранить».
 *
 * Свитч выкл == режим [SplitTunnelMode.ALL] (весь трафик в туннель). Пока прокси
 * активен (`locked`), любые изменения запрещены — контролы disabled.
 */
@Composable
fun SplitTunnelSheetContent(
    settingsViewModel: SettingsViewModel,
    mode: String,
    apps: String,
    locked: Boolean
) {
    val context = LocalContext.current
    val enabled = mode != SplitTunnelMode.ALL
    // Последний выбранный «рабочий» режим, чтобы свитч вкл возвращал его, а не дефолт.
    var modeChoice by remember {
        mutableStateOf(if (mode != SplitTunnelMode.ALL) mode else SplitTunnelMode.EXCLUDE)
    }
    var query by remember { mutableStateOf("") }
    val selected = remember(apps) { apps.toPackageSet() }
    val installed by produceState<List<AppChoice>?>(initialValue = null) {
        value = context.installedInternetApps()
    }

    // Высота контента стабильна (список фиксированной высоты), поэтому sheet
    // открывается за одну анимацию и не «дёргается» при загрузке/поиске. Лист
    // оставляем content-sized (без fillMaxSize) — так нет оверштута до пика.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.split_tunnel_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Switch(
                checked = enabled,
                enabled = !locked,
                onCheckedChange = { on ->
                    HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                    settingsViewModel.setSplitTunnelMode(
                        if (on) modeChoice else SplitTunnelMode.ALL
                    )
                }
            )
        }

        if (locked) {
            LockedBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HorizontalPadding)
            )
        }

        if (enabled) {
            ModeDropdown(
                mode = modeChoice,
                enabled = !locked,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HorizontalPadding),
                onSelect = { value ->
                    modeChoice = value
                    settingsViewModel.setSplitTunnelMode(value)
                }
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                enabled = !locked,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HorizontalPadding),
                label = { Text(stringResource(R.string.split_tunnel_search)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.search_24px), null)
                }
            )

            AppList(
                modifier = Modifier
                    .height(ListHeight)
                    .fillMaxWidth(),
                installed = installed,
                selected = selected,
                query = query,
                enabled = !locked,
                onToggle = { pkg ->
                    HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                    val next = if (pkg in selected) selected - pkg else selected + pkg
                    settingsViewModel.setSplitTunnelApps(next.sorted().joinToString("\n"))
                }
            )
        }
    }
}

/** Горизонтальный отступ контента листа. Совпадает с внутренним паддингом ListItem (M3). */
private val HorizontalPadding = 16.dp

@Composable
private fun LockedBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.info_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                stringResource(R.string.split_tunnel_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ModeDropdown(
    mode: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        SplitTunnelMode.INCLUDE to stringResource(R.string.split_tunnel_mode_include),
        SplitTunnelMode.EXCLUDE to stringResource(R.string.split_tunnel_mode_exclude)
    )
    val current = options.firstOrNull { it.first == mode }?.second.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            minLines = 2,
            label = { Text(stringResource(R.string.split_tunnel_mode_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun AppList(
    installed: List<AppChoice>?,
    selected: Set<String>,
    query: String,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (installed == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator()
        }
        return
    }

    // Выбранные пункты поднимаем наверх. Набор фиксируем на момент загрузки списка
    // (а не на живой `selected`), иначе пункт прыгал бы при каждом тыке чекбокса.
    // Внутри групп алфавитный порядок сохраняется — installedInternetApps уже
    // отсортирован, а sortedBy стабильна.
    val pinned = remember(installed) { selected }

    val filtered = remember(installed, query, pinned) {
        val base = if (query.isBlank()) installed
        else installed.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        base.sortedBy { it.packageName !in pinned }
    }

    if (filtered.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.split_tunnel_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(filtered, key = { it.packageName }) { app ->
            val checked = app.packageName in selected
            ListItem(
                headlineContent = { Text(app.label) },
                supportingContent = {
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                },
                leadingContent = { AppIcon(app.packageName) },
                trailingContent = {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = enabled
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onToggle(app.packageName) }
                )
            )
        }
    }
}

/** Фиксированная высота области списка — стабильна при загрузке/поиске (нет ресайза листа). */
private val ListHeight = 360.dp
