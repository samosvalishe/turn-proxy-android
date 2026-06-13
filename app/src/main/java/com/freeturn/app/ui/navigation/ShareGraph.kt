package com.freeturn.app.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.freeturn.app.ui.screens.share.ShareScreen

/** Вкладка «Поделиться»: выдача доступа по ссылке (пир на сервере по SSH). */
internal fun NavGraphBuilder.shareGraph(navController: NavHostController) {
    navigation<ShareGraph>(startDestination = Share) {
        composable<Share> { entry ->
            // Начальную загрузку share-info стартуем после конца enter-перехода: morph-индикатор
            // и slide одновременно роняют кадры. Производное от реального состояния анимации,
            // не таймер (reduced-motion → сразу).
            val settled = transition.currentState == transition.targetState
            ShareScreen(
                screenSettled = settled,
                // CTA пустого состояния — вкладка добавления (tab-переход, как у Home).
                onAddServer = { if (entry.isResumed()) navController.navigateToTab(AddGraph) }
            )
        }
    }
}
