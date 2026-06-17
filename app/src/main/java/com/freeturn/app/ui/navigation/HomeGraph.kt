package com.freeturn.app.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.freeturn.app.ui.screens.home.HomeScreen
import com.freeturn.app.viewmodel.proxy.ProxyViewModel
import com.freeturn.app.viewmodel.settings.SettingsViewModel

/** Вкладка "Главная". Логи - отдельная вкладка нижнего меню (см. [logsGraph]). */
internal fun NavGraphBuilder.homeGraph(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    proxyViewModel: ProxyViewModel
) {
    navigation<HomeGraph>(startDestination = Home) {
        composable<Home> { entry ->
            HomeScreen(
                settingsViewModel = settingsViewModel,
                proxyViewModel = proxyViewModel,
                // Хаб живёт в графе настроек. Прямой navigate отсюда пушил бы settings-экраны
                // в стек вкладки "Главная" - save/restore вкладок портится. Поэтому сперва
                // честно переключаем вкладку, затем пушим хаб в её стек.
                onOpenServerSettings = { id ->
                    if (entry.isResumed()) {
                        navController.navigateToTab(SettingsGraph)
                        // singleTop: восстановленный стек настроек мог уже держать этот хаб
                        // сверху - без него destination задваивается.
                        navController.navigate(ServerDetail(id)) { launchSingleTop = true }
                    }
                },
                // CTA пустого состояния - вкладка добавления сервера (tab-переход, не push:
                // иначе бар перестаёт возвращать на главную).
                onAddServer = { if (entry.isResumed()) navController.navigateToTab(AddGraph) }
            )
        }
    }
}
