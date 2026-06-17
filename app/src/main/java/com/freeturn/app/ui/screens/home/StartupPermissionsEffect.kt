package com.freeturn.app.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.freeturn.app.viewmodel.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Пауза перед системными диалогами: первый кадр экрана успевает отрисоваться.
private const val PERMISSION_PROMPT_DELAY_MS = 400L

/**
 * Стартовые разрешения главного экрана: уведомления (Android 13+), затем исключение
 * из оптимизации батареи. Цепочка через callback - системные окна не наслаиваются.
 * Диалог батареи - один раз за установку (см. batteryPromptShownFlow).
 */
@SuppressLint("BatteryLife")
@Composable
internal fun RequestStartupPermissions(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* пользователь закрыл диалог батареи - результат нас не интересует */ }

    suspend fun maybeRequestBatteryExemption() {
        if (settingsViewModel.batteryPromptShownOnce()) return
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
        settingsViewModel.setBatteryPromptShown()
        batteryOptLauncher.launch(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // После диалога уведомлений - запрашиваем исключение из оптимизации батареи
        scope.launch { maybeRequestBatteryExemption() }
    }

    LaunchedEffect(Unit) {
        delay(PERMISSION_PROMPT_DELAY_MS)
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED

        if (needsNotification) {
            // Запрашиваем нотификации; батарею запросим в callback выше
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Нотификации уже есть - сразу проверяем батарею
            maybeRequestBatteryExemption()
        }
    }
}
