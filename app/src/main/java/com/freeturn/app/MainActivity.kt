package com.freeturn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.navigation.AppNavigation
import com.freeturn.app.ui.theme.FreeTurnTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !settingsViewModel.isInitialized.value }

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
}
