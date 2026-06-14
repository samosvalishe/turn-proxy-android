package com.freeturn.app.ui.screens.splittunnel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.freeturn.app.R
import com.freeturn.app.data.AppChoice
import com.freeturn.app.ui.components.AppIcon
import com.freeturn.app.ui.components.EmptyState

/** Список приложений с иконками/чекбоксами: загрузка, поиск, пустое состояние. */
@Composable
internal fun AppList(
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
    // Внутри групп алфавитный порядок сохраняется - installedInternetApps уже
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
        EmptyState(
            iconRes = R.drawable.search_24px,
            desc = stringResource(R.string.split_tunnel_empty),
            modifier = modifier
        )
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
