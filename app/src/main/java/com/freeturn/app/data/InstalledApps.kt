package com.freeturn.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Установленное приложение - кандидат для split-tunnel. */
data class AppChoice(val label: String, val packageName: String)

/** Строку package-имён (запятая/пробел/перенос/`;`) в множество, без пустых. */
fun String.toPackageSet(): Set<String> =
    split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/** Установлен ли пакет. getPackageInfo кидает NameNotFoundException на отсутствующем -> false. */
fun Context.isPackageInstalled(pkg: String): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(pkg, 0)
    }
}.isSuccess

/**
 * Установленные приложения с INTERNET-пермом, кроме самого FreeTurn.
 * PackageManager-вызовы тяжёлые (диск/IPC) - гоним на IO-потоке.
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
