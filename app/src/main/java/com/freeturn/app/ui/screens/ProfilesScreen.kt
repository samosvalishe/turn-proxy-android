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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.Profile
import com.freeturn.app.data.ProfilesSnapshot
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.ProfileNameDialog
import com.freeturn.app.ui.util.hapticClickable
import com.freeturn.app.viewmodel.MainViewModel

@Composable
fun ProfilesSheetContent(
    viewModel: MainViewModel,
    snapshot: ProfilesSnapshot,
    containerColor: Color,
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit
) {
    val context = LocalContext.current
    val updatedMsg = stringResource(R.string.profile_updated_toast)

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var menuTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val listColors = ListItemDefaults.colors(containerColor = containerColor)
    val takenNames = remember(snapshot.list) { snapshot.list.map { it.name } }
    val hasActive = snapshot.activeId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        Text(
            stringResource(R.string.profiles_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        )

        if (hasActive) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_update_current)) },
                supportingContent = { Text(stringResource(R.string.profile_update_current_hint)) },
                leadingContent = { Icon(painterResource(R.drawable.cached_24px), null) },
                colors = listColors,
                modifier = Modifier.hapticClickable(HapticUtil.Pattern.SUCCESS) {
                    viewModel.updateActiveProfileFromCurrent()
                    android.widget.Toast.makeText(context, updatedMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.profile_save_current)) },
            supportingContent = { Text(stringResource(R.string.profile_save_current_hint)) },
            leadingContent = { Icon(painterResource(R.drawable.save_24px), null) },
            colors = listColors,
            modifier = Modifier.hapticClickable(HapticUtil.Pattern.CLICK) {
                showSaveDialog = true
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (snapshot.list.isEmpty()) {
            ProfilesEmptyState(
                onSave = { showSaveDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Text(
                stringResource(R.string.profile_section_saved),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(snapshot.list, key = { it.id }) { p ->
                    ProfileCard(
                        profile = p,
                        isActive = snapshot.activeId == p.id,
                        menuExpanded = menuTarget == p.id,
                        onApply = {
                            if (snapshot.activeId != p.id) viewModel.applyProfile(p.id)
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
                viewModel.saveCurrentAsProfile(name)
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
                    viewModel.renameProfile(id, name)
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
                            viewModel.deleteProfile(id)
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

@Composable
private fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    menuExpanded: Boolean,
    onApply: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val containerColor = if (isActive)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerLow
    val onContainer = if (isActive)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurface
    val onContainerVariant = if (isActive)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = {
            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
            onApply()
        },
        enabled = !isActive,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isActive },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isActive) {
                Icon(
                    painterResource(R.drawable.check_circle_24px),
                    contentDescription = stringResource(R.string.profile_active),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                val sub = listOfNotNull(
                    profile.client.serverAddress.takeIf { it.isNotBlank() },
                    profile.ssh.ip.takeIf { it.isNotBlank() }?.let { "SSH $it" }
                ).joinToString(" · ").ifBlank { "—" }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainerVariant
                )
            }
            Box {
                IconButton(onClick = onMenuToggle) {
                    Icon(
                        painterResource(R.drawable.more_vert_24px),
                        contentDescription = stringResource(R.string.profile_more),
                        tint = onContainer
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
