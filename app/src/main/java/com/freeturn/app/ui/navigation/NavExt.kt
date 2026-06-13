package com.freeturn.app.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

// Контент экрана кликабелен уже во время навигационного перехода, а RESUMED его entry
// становится только по окончании анимации. Поздний второй тап успевал провалиться в
// элемент нового экрана — до RESUMED навигационные тапы глотаем.
internal fun NavBackStackEntry.isResumed() =
    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

// Переход на граф-вкладку с сохранением/восстановлением её стека.
internal fun NavHostController.navigateToTab(graphRoute: Any) {
    navigate(graphRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
