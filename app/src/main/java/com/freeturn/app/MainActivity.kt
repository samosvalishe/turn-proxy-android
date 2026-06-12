package com.freeturn.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.freeturn.app.domain.LinkImportBus
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.navigation.AppNavigation
import com.freeturn.app.ui.theme.FreeTurnTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModel()
    private val linkImportBus: LinkImportBus by inject()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !settingsViewModel.isInitialized.value }

        // При recreation (поворот, смена темы) интент уже обработан в первом onCreate.
        if (savedInstanceState == null) handleLinkIntent(intent)

        // На Android 13+ без POST_NOTIFICATIONS нотификация foreground-сервиса
        // (статус прокси + кнопка Stop) не показывается. Запрашиваем при старте.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        HapticUtil.perform(this, HapticUtil.Pattern.LAUNCH)
        enableEdgeToEdge()
        setContent {
            val dynamicTheme by settingsViewModel.dynamicTheme.collectAsStateWithLifecycle()
            FreeTurnTheme(dynamicColor = dynamicTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    // singleTask: freeturn://-ссылка при живой задаче приходит сюда, не в onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Иначе getIntent() после recreation вернёт исходный интент запуска.
        setIntent(intent)
        handleLinkIntent(intent)
    }

    private fun handleLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.toString()?.let(linkImportBus::offer)
    }
}
