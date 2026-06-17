package com.freeturn.app.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.freeturn.app.ui.screens.logs.LogsScreen
import com.freeturn.app.viewmodel.proxy.ProxyViewModel

/** Вкладка "Логи": отдельный граф нижнего меню, виден при nerdMode + включённых логах. */
internal fun NavGraphBuilder.logsGraph(proxyViewModel: ProxyViewModel) {
    navigation<LogsGraph>(startDestination = Logs) {
        composable<Logs> {
            LogsScreen(proxyViewModel = proxyViewModel)
        }
    }
}
