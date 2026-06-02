package com.freeturn.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Установленное приложение — кандидат для split-tunnel. */
data class AppChoice(val label: String, val packageName: String)

/** Строку package-имён (запятая/пробел/перенос/`;`) в множество, без пустых. */
fun String.toPackageSet(): Set<String> =
    split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/**
 * Установленные приложения с INTERNET-пермом, кроме самого FreeTurn.
 * PackageManager-вызовы тяжёлые (диск/IPC) — гоним на IO-потоке.
 */
suspend fun Context.installedInternetApps(): List<AppChoice> = withContext(Dispatchers.IO) {
    val pm = packageManager
    val flags = PackageManager.GET_PERMISSIONS
    val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledPackages(flags)
    }
    packages.asSequence()
        .filter { info ->
            info.packageName != packageName &&
                info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
        }
        .map { info ->
            val appInfo = info.applicationInfo
            AppChoice(
                label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.packageName,
                packageName = info.packageName
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<AppChoice> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()
}

/**
 * Диалог выбора приложений для split-tunnel. Список грузится асинхронно (IO),
 * до готовности — спиннер. Никакой работы PackageManager на main-потоке.
 */
@Composable
fun AppPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var selectedApps by remember(selected) { mutableStateOf(selected) }
    val apps by produceState<List<AppChoice>?>(initialValue = null) {
        value = context.installedInternetApps()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_tunnel_pick_title)) },
        text = {
            val list = apps
            if (list == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(list) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = app.packageName in selectedApps,
                                onCheckedChange = { checked ->
                                    selectedApps = if (checked) selectedApps + app.packageName
                                    else selectedApps - app.packageName
                                }
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedApps) }) {
                Text(stringResource(R.string.split_tunnel_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
