package com.freeturn.app.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.ui.screens.CaptchaWebViewDialog
import com.freeturn.app.ui.screens.AboutScreen
import com.freeturn.app.ui.screens.AdvancedScreen
import com.freeturn.app.ui.screens.AppScreen
import com.freeturn.app.ui.screens.ClientSetupScreen
import com.freeturn.app.ui.screens.ConnectionModeScreen
import com.freeturn.app.ui.screens.HomeScreen
import com.freeturn.app.ui.screens.LogsScreen
import com.freeturn.app.ui.screens.NerdScreen
import com.freeturn.app.ui.screens.OnboardingScreen
import com.freeturn.app.ui.screens.ServerDetailScreen
import com.freeturn.app.ui.screens.ServerManagementScreen
import com.freeturn.app.ui.screens.ServersListScreen
import com.freeturn.app.ui.screens.SettingsScreen
import com.freeturn.app.ui.screens.SshSetupScreen
import com.freeturn.app.viewmodel.ProxyState
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.ProxyViewModel
import com.freeturn.app.viewmodel.ServerViewModel
import org.koin.androidx.compose.koinViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val SSH_SETUP = "ssh_setup"              // из настроек сервера
    const val SSH_SETUP_OB = "ssh_setup_ob"        // только в мастере онбординга
    const val SERVER_MANAGEMENT_OB = "server_management_ob" // только в мастере онбординга
    const val CLIENT_SETUP_OB = "client_setup_onboarding"

    // Графы вкладок нижнего меню. У каждой вкладки свой back-stack: бар виден на всех
    // уровнях вложенности, повторный тап по активной вкладке возвращает в её корень.
    const val HOME_GRAPH = "home_graph"
    const val SETTINGS_GRAPH = "settings_graph"

    const val HOME = "home"
    const val LOGS = "logs"

    // Settings-флоу: Настройки → Серверы → [сервер] → подключение / сервер
    const val SETTINGS = "settings"
    const val APP_SETTINGS = "app_settings"
    const val ABOUT = "about"
    const val ADVANCED = "advanced"
    const val SERVERS_LIST = "servers_list"
    const val SERVER_DETAIL = "server_detail/{profileId}"
    const val CONNECTION_MODE = "connection_mode/{profileId}"
    const val CLIENT_SETUP = "client_setup/{profileId}"
    const val SERVER_MANAGEMENT = "server_management/{profileId}"
    const val NERD_INFO = "nerd_info/{profileId}"

    fun serverDetail(id: String) = "server_detail/$id"
    fun connectionMode(id: String) = "connection_mode/$id"
    fun clientSetup(id: String) = "client_setup/$id"
    fun serverManagement(id: String) = "server_management/$id"
    fun nerdInfo(id: String) = "nerd_info/$id"
}

// MD3 emphasized-кривая + длительности навигационных переходов (единый источник).
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val NAV_SLIDE_MS = 300
private const val NAV_FADE_IN_MS = 180
private const val NAV_FADE_OUT_MS = 120

@Composable
fun AppNavigation(
    settingsViewModel: SettingsViewModel = koinViewModel(),
    proxyViewModel: ProxyViewModel = koinViewModel(),
    serverViewModel: ServerViewModel = koinViewModel()
) {
    val isInitialized by settingsViewModel.isInitialized.collectAsStateWithLifecycle()

    // Не строим NavHost пока DataStore не загружен — иначе startDestination
    // захватит дефолтный onboardingDone=false и всегда покажет онбординг
    if (!isInitialized) return

    val proxyState by proxyViewModel.proxyState.collectAsStateWithLifecycle()
    val initialOnboardingDone by settingsViewModel.initialOnboardingDone.collectAsStateWithLifecycle()
    val initialTgSubscribeShown by settingsViewModel.initialTgSubscribeShown.collectAsStateWithLifecycle()
    val startDestination = remember { if (initialOnboardingDone) Routes.HOME_GRAPH else Routes.ONBOARDING }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // Бар виден, когда текущий экран принадлежит одному из графов-вкладок (т.е. не
    // онбординг). hierarchy включает родительский граф на любом уровне вложенности.
    val showNavSuite = navItems.any { item ->
        destination?.hierarchy?.any { it.route == item.graphRoute } == true
    }

    var showTgDialog by rememberSaveable { mutableStateOf(!initialTgSubscribeShown && initialOnboardingDone) }

    val adaptiveType = NavigationSuiteScaffoldDefaults
        .calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val layoutType = if (showNavSuite) adaptiveType else NavigationSuiteType.None

    val context = LocalContext.current

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            navItems.forEach { item ->
                val selected = destination?.hierarchy?.any { it.route == item.graphRoute } == true
                item(
                    selected = selected,
                    onClick = {
                        if (selected) {
                            // Повторный тап по активной вкладке — назад в её корень.
                            navController.popBackStack(item.startRoute, inclusive = false)
                        } else {
                            HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                            navController.navigate(item.graphRoute) {
                                // Сохраняем стек текущей вкладки, восстанавливаем целевую.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Crossfade(targetState = selected, label = "nav_icon_${item.graphRoute}") { isSelected ->
                            Icon(
                                painter = painterResource(
                                    if (isSelected) item.selectedIconRes else item.unselectedIconRes
                                ),
                                contentDescription = stringResource(item.labelResId)
                            )
                        }
                    },
                    label = { Text(stringResource(item.labelResId)) }
                )
            }
        }
    ) {
        AppNavHost(
            navController = navController,
            settingsViewModel = settingsViewModel,
            proxyViewModel = proxyViewModel,
            serverViewModel = serverViewModel,
            startDestination = startDestination
        )
    }

    // Диалог капчи поверх любого экрана. Оборачиваем в key(sessionId), чтобы для
    // каждой новой капча-сессии Compose пересоздавал диалог и WebView грузил URL заново
    // (бинарник цикличит креды и для каждой выдаёт новую капчу с тем же localhost-URL).
    val captchaState = proxyState as? ProxyState.CaptchaRequired
    if (captchaState != null) {
        androidx.compose.runtime.key(captchaState.sessionId) {
            CaptchaWebViewDialog(
                captchaUrl = captchaState.url,
                onDismiss = { proxyViewModel.dismissCaptcha() }
            )
        }
    }

    if (showTgDialog) {
        val uriHandler = LocalUriHandler.current
        TelegramSubscribeDialog(
            onSubscribe = {
                uriHandler.openUri("https://t.me/+53nh4UNiSv5lNTgy")
                settingsViewModel.setTgSubscribeShown()
                showTgDialog = false
            },
            onDismiss = {
                settingsViewModel.setTgSubscribeShown()
                showTgDialog = false
            }
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    proxyViewModel: ProxyViewModel,
    serverViewModel: ServerViewModel,
    startDestination: String
) {
    // Reduced-motion (системная «Убрать анимации»): мгновенные переходы без слайда.
    // MotionScheme.expressive() в теме рулит моушеном самих m3-компонентов; здесь —
    // только навигационные shared-axis X переходы, единый источник длительностей выше.
    val reducedMotion = LocalReducedMotion.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        // Снаппи emphasized shared-axis X вместо дефолтного медленного 700ms-fade.
        enterTransition = {
            if (reducedMotion) EnterTransition.None
            else fadeIn(tween(NAV_FADE_IN_MS, easing = EmphasizedEasing)) +
                slideInHorizontally(tween(NAV_SLIDE_MS, easing = EmphasizedEasing)) { it / 5 }
        },
        exitTransition = {
            if (reducedMotion) ExitTransition.None
            else fadeOut(tween(NAV_FADE_OUT_MS, easing = EmphasizedEasing)) +
                slideOutHorizontally(tween(NAV_SLIDE_MS, easing = EmphasizedEasing)) { -it / 12 }
        },
        popEnterTransition = {
            if (reducedMotion) EnterTransition.None
            else fadeIn(tween(NAV_FADE_IN_MS, easing = EmphasizedEasing)) +
                slideInHorizontally(tween(NAV_SLIDE_MS, easing = EmphasizedEasing)) { -it / 5 }
        },
        popExitTransition = {
            if (reducedMotion) ExitTransition.None
            else fadeOut(tween(NAV_FADE_OUT_MS, easing = EmphasizedEasing)) +
                slideOutHorizontally(tween(NAV_SLIDE_MS, easing = EmphasizedEasing)) { it / 12 }
        }
    ) {
        // Онбординг-мастер (вне графов-вкладок → нижнее меню скрыто)
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onSetupServer = { navController.navigate(Routes.SSH_SETUP_OB) },
                onSkip = {
                    settingsViewModel.setOnboardingDone()
                    navController.navigate(Routes.HOME_GRAPH) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.SSH_SETUP_OB) {
            SshSetupScreen(
                serverViewModel = serverViewModel,
                settingsViewModel = settingsViewModel,
                onConnected = {
                    navController.navigate(Routes.SERVER_MANAGEMENT_OB) {
                        popUpTo(Routes.SSH_SETUP_OB) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SERVER_MANAGEMENT_OB) {
            ServerManagementScreen(
                serverViewModel = serverViewModel,
                settingsViewModel = settingsViewModel,
                onContinue = {
                    navController.navigate(Routes.CLIENT_SETUP_OB) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.CLIENT_SETUP_OB) {
            ClientSetupScreen(
                settingsViewModel = settingsViewModel,
                serverViewModel = serverViewModel,
                showFinishButton = true,
                onFinish = {
                    settingsViewModel.setOnboardingDone()
                    navController.navigate(Routes.HOME_GRAPH) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Вкладка «Главная». Вход в экран логов живёт в шапке Home (кнопка видна при
        // включённом «Показывать логи») и открывается в стеке этой же вкладки.
        navigation(startDestination = Routes.HOME, route = Routes.HOME_GRAPH) {
            composable(Routes.HOME) {
                HomeScreen(
                    settingsViewModel = settingsViewModel,
                    proxyViewModel = proxyViewModel,
                    onOpenLogs = { navController.navigate(Routes.LOGS) },
                    // Хаб сервера живёт в графе настроек — нижний бар подсветит «Настройки»,
                    // системный «назад» вернёт на главную.
                    onOpenServerSettings = { id -> navController.navigate(Routes.serverDetail(id)) }
                )
            }
            composable(Routes.LOGS) {
                LogsScreen(proxyViewModel = proxyViewModel)
            }
        }

        // Вкладка «Настройки»: Настройки → Серверы → [сервер] → подключение/режим/сервер → SSH
        navigation(startDestination = Routes.SETTINGS, route = Routes.SETTINGS_GRAPH) {
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenServers = { navController.navigate(Routes.SERVERS_LIST) },
                    onOpenApp = { navController.navigate(Routes.APP_SETTINGS) },
                    onOpenAdvanced = { navController.navigate(Routes.ADVANCED) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) }
                )
            }

            composable(Routes.APP_SETTINGS) {
                AppScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ADVANCED) {
                AdvancedScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SERVERS_LIST) {
                ServersListScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenServer = { id -> navController.navigate(Routes.serverDetail(id)) }
                )
            }

            composable(Routes.SERVER_DETAIL) { entry ->
                val id = entry.arguments?.getString("profileId").orEmpty()
                ServerDetailScreen(
                    profileId = id,
                    settingsViewModel = settingsViewModel,
                    serverViewModel = serverViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenConnection = { navController.navigate(Routes.clientSetup(id)) },
                    onOpenConnectionMode = { navController.navigate(Routes.connectionMode(id)) },
                    onOpenServerSettings = { navController.navigate(Routes.serverManagement(id)) },
                    onOpenNerdInfo = { navController.navigate(Routes.nerdInfo(id)) },
                    onConfigureSsh = { navController.navigate(Routes.SSH_SETUP) }
                )
            }

            composable(Routes.NERD_INFO) { entry ->
                val id = entry.arguments?.getString("profileId").orEmpty()
                NerdScreen(
                    profileId = id,
                    settingsViewModel = settingsViewModel,
                    serverViewModel = serverViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CONNECTION_MODE) { entry ->
                val id = entry.arguments?.getString("profileId").orEmpty()
                ConnectionModeScreen(
                    settingsViewModel = settingsViewModel,
                    proxyViewModel = proxyViewModel,
                    profileId = id,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SERVER_MANAGEMENT) { entry ->
                val id = entry.arguments?.getString("profileId").orEmpty()
                ServerManagementScreen(
                    serverViewModel = serverViewModel,
                    settingsViewModel = settingsViewModel,
                    profileId = id,
                    onBack = { navController.popBackStack() },
                    onEditConnection = { navController.navigate(Routes.SSH_SETUP) }
                )
            }

            composable(Routes.CLIENT_SETUP) { entry ->
                val id = entry.arguments?.getString("profileId").orEmpty()
                ClientSetupScreen(
                    settingsViewModel = settingsViewModel,
                    serverViewModel = serverViewModel,
                    profileId = id,
                    onBack = { navController.popBackStack() },
                    showFinishButton = false
                )
            }

            composable(Routes.SSH_SETUP) {
                SshSetupScreen(
                    serverViewModel = serverViewModel,
                    settingsViewModel = settingsViewModel,
                    // Форма поверх настроек сервера — после успеха возвращаемся назад.
                    onConnected = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    // Профиль мог быть удалён, пока экран висел в стеке вкладки — выходим назад.
                    popWhenNoProfiles = true
                )
            }
        }
    }
}

private data class NavItem(
    val graphRoute: String,   // граф-вкладка (цель навигации, проверка selected)
    val startRoute: String,   // корневой экран вкладки (для re-tap → корень)
    val labelResId: Int,
    val selectedIconRes: Int,
    val unselectedIconRes: Int
)

@Composable
private fun TelegramSubscribeDialog(onSubscribe: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tg_subscribe_title)) },
        text = { Text(stringResource(R.string.tg_subscribe_desc)) },
        confirmButton = {
            TextButton(
                onClick = onSubscribe,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) { Text(stringResource(R.string.tg_subscribe_btn)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tg_not_now)) }
        }
    )
}

private val navItems = listOf(
    NavItem(Routes.HOME_GRAPH, Routes.HOME, R.string.nav_home, R.drawable.home_24px, R.drawable.home_outlined_24px),
    NavItem(Routes.SETTINGS_GRAPH, Routes.SETTINGS, R.string.nav_settings, R.drawable.settings_24px, R.drawable.settings_outlined_24px)
)
