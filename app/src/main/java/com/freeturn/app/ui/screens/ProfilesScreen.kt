@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.Profile
import com.freeturn.app.data.ProfilesSnapshot
import com.freeturn.app.data.Provider
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.ProfileNameDialog
import com.freeturn.app.ui.util.hapticClickable
import com.freeturn.app.viewmodel.SettingsViewModel

@Composable
fun ProfilesSheetContent(
    settingsViewModel: SettingsViewModel,
    snapshot: ProfilesSnapshot,
    privacyMode: Boolean = false,
    onCollapse: () -> Unit = {}
) {
    val context = LocalContext.current
    val updatedMsg = stringResource(R.string.profile_updated_toast)

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var menuTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val takenNames = remember(snapshot.list) { snapshot.list.map { it.name } }
    val hasActive = snapshot.activeId != null

    val active = snapshot.active

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        // Шапка: имя активного профиля + «провайдер | адрес» — как в референсе.
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
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
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
            val sub = active?.let {
                val addr = (it.client.serverAddress.takeIf { a -> a.isNotBlank() }
                    ?: it.ssh.ip.takeIf { a -> a.isNotBlank() })?.redact(privacyMode)
                listOfNotNull(providerLabel(it.client.provider), addr).joinToString("  |  ")
            }
            if (!sub.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Селектор провайдера (чип с дропдауном). Пока один пункт — задел на будущее.
        ProviderChip(
            current = active?.client?.provider ?: Provider.VK,
            onSelect = { settingsViewModel.setProvider(it) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(20.dp))

        if (!snapshot.loaded) {
            // Снимок ещё грузится - пустой вес-плейсхолдер, без мигания empty-state.
            Spacer(Modifier.weight(1f))
        } else if (snapshot.list.isEmpty()) {
            ProfilesEmptyState(
                onSave = { showSaveDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.profiles_servers_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    if (hasActive) {
                        IconButton(onClick = {
                            settingsViewModel.updateActiveProfileFromCurrent()
                            android.widget.Toast.makeText(context, updatedMsg, android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                painterResource(R.drawable.cached_24px),
                                contentDescription = stringResource(R.string.profile_update_current)
                            )
                        }
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            painterResource(R.drawable.save_24px),
                            contentDescription = stringResource(R.string.profile_save_current)
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(snapshot.list, key = { it.id }) { p ->
                    ProfileRow(
                        profile = p,
                        isActive = snapshot.activeId == p.id,
                        privacyMode = privacyMode,
                        menuExpanded = menuTarget == p.id,
                        onApply = {
                            if (snapshot.activeId != p.id) {
                                settingsViewModel.applyProfile(p.id)
                                onCollapse()
                            }
                        },
                        onMenuToggle = { menuTarget = if (menuTarget == p.id) null else p.id },
                        onMenuDismiss = { menuTarget = null },
                        onRename = {
                            menuTarget = null
                            renameTarget = p.id
                        },
                        onDelete = {
                            menuTarget = null
                            deleteTarget = p.id
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (showSaveDialog) {
        ProfileNameDialog(
            title = stringResource(R.string.profile_save_dialog_title),
            initial = "",
            takenNames = takenNames,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                settingsViewModel.saveCurrentAsProfile(name)
                showSaveDialog = false
            }
        )
    }

    renameTarget?.let { id ->
        val profile = snapshot.list.firstOrNull { it.id == id }
        if (profile == null) {
            renameTarget = null
        } else {
            ProfileNameDialog(
                title = stringResource(R.string.profile_rename),
                initial = profile.name,
                takenNames = takenNames,
                onDismiss = { renameTarget = null },
                onConfirm = { name ->
                    settingsViewModel.renameProfile(id, name)
                    renameTarget = null
                }
            )
        }
    }

    deleteTarget?.let { id ->
        val profile = snapshot.list.firstOrNull { it.id == id }
        if (profile == null) {
            deleteTarget = null
        } else {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.profile_delete_title)) },
                text = { Text(stringResource(R.string.profile_delete_desc, profile.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                            settingsViewModel.deleteProfile(id)
                            deleteTarget = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.profile_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

/** Строка-сервер в стиле референса: radio + имя/адрес + меню (rename/delete). */
@Composable
private fun ProfileRow(
    profile: Profile,
    isActive: Boolean,
    privacyMode: Boolean,
    menuExpanded: Boolean,
    onApply: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isActive }
            .hapticClickable(HapticUtil.Pattern.CLICK) { if (!isActive) onApply() }
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = isActive,
            onClick = { if (!isActive) onApply() }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            val sub = listOfNotNull(
                profile.client.serverAddress.takeIf { it.isNotBlank() }?.redact(privacyMode),
                profile.ssh.ip.takeIf { it.isNotBlank() }?.let { "SSH ${it.redact(privacyMode)}" }
            ).joinToString(" · ").ifBlank { "—" }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = onMenuToggle) {
                Icon(
                    painterResource(R.drawable.more_vert_24px),
                    contentDescription = stringResource(R.string.profile_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_rename)) },
                    leadingIcon = { Icon(painterResource(R.drawable.edit_24px), null) },
                    onClick = onRename
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.profile_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = onDelete
                )
            }
        }
    }
}

/** Чип выбора провайдера TURN-creds (-provider). Дропдаун из Provider.ALL. */
@Composable
private fun ProviderChip(
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
            expanded = it
        },
        modifier = modifier
    ) {
        FilterChip(
            selected = true,
            onClick = {},
            label = { Text(providerLabel(current)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Provider.ALL.forEach { value ->
                DropdownMenuItem(
                    text = { Text(providerLabel(value)) },
                    trailingIcon = if (value == current) ({
                        Icon(painterResource(R.drawable.check_circle_24px), null)
                    }) else null,
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun providerLabel(value: String): String = when (value) {
    Provider.VK -> stringResource(R.string.provider_vk)
    else -> value
}

@Composable
private fun ProfilesEmptyState(
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painterResource(R.drawable.manage_accounts_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.profiles_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.profiles_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = {
            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
            onSave()
        }) {
            Icon(painterResource(R.drawable.save_24px), null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.profile_save_current))
        }
    }
}
