package com.freeturn.app.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.freeturn.app.ui.screens.addserver.AddServerScreen
import com.freeturn.app.ui.screens.setup.ServerSetupScreen
import com.freeturn.app.ui.screens.share.QrScannerScreen
import com.freeturn.app.viewmodel.SettingsViewModel

/**
 * Вкладка «+»: мастер self-hosted живёт целиком в этом графе. Кросс-графовый push
 * (хаб из вкладки «+») ломал restoreState при переключении вкладок — сервер создаётся
 * мастером, в конце переходим на главную.
 */
internal fun NavGraphBuilder.addGraph(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel
) {
    navigation<AddGraph>(startDestination = AddServer) {
        composable<AddServer> { entry ->
            AddServerScreen(
                onSelfHosted = { if (entry.isResumed()) navController.navigate(SelfHostedSetup) },
                // Ручная настройка: создаём пустой сервер и уводим в его хаб. Хаб живёт в
                // графе настроек — тот же tab-switch + singleTop-push, что у HomeScreen
                // (кросс-графовый push ломает restoreState вкладок).
                onManualCreate = { name ->
                    settingsViewModel.addManualServer(name) { id ->
                        navController.navigateToTab(SettingsGraph)
                        navController.navigate(ServerDetail(id)) { launchSingleTop = true }
                    }
                },
                onScanQr = { if (entry.isResumed()) navController.navigate(QrScanner) }
            )
        }
        composable<QrScanner> {
            QrScannerScreen(onBack = { navController.popBackStack() })
        }
        composable<SelfHostedSetup> {
            ServerSetupScreen(
                onClose = { navController.popBackStack() },
                onFinished = {
                    // Один переход сразу на главную (двухшаговый pop+navigate мигал экраном
                    // добавления). Стек «+» сносим без сохранения — иначе вкладка восстановила
                    // бы устаревший done-экран мастера.
                    navController.navigate(HomeGraph) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
