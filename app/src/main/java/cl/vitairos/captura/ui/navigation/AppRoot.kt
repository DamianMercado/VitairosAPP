package cl.vitairos.captura.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.R
import cl.vitairos.captura.SessionState
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.UserRole
import cl.vitairos.captura.sync.MessageKind
import cl.vitairos.captura.sync.UiMessage
import cl.vitairos.captura.ui.components.LocalOnline
import cl.vitairos.captura.ui.components.LocalSyncing
import cl.vitairos.captura.ui.detail.DetailScreen
import cl.vitairos.captura.ui.editor.EditorScreen
import cl.vitairos.captura.ui.history.HistoryScreen
import cl.vitairos.captura.ui.home.HomeScreen
import cl.vitairos.captura.ui.login.LoginScreen
import cl.vitairos.captura.ui.queue.QueueScreen
import cl.vitairos.captura.ui.settings.SettingsScreen
import cl.vitairos.captura.ui.supervisor.SupervisorScreen
import cl.vitairos.captura.ui.theme.Vitairos
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val QUEUE = "queue"
    const val HISTORY = "history?status={status}"
    const val SETTINGS = "settings"
    const val REVIEW = "review"
    const val EDITOR = "editor?reportId={reportId}"
    const val DETAIL = "detail/{reportId}"

    fun history(status: String? = null) = if (status == null) "history" else "history?status=$status"
    fun editor(reportId: String? = null) = if (reportId == null) "editor" else "editor?reportId=$reportId"
    fun detail(reportId: String) = "detail/$reportId"
}

private data class Tab(val route: String, val navRoute: String, val label: String, val icon: ImageVector)

private val operatorTabs = listOf(
    Tab(Routes.HOME, Routes.HOME, "Inicio", Icons.Outlined.Home),
    Tab(Routes.QUEUE, Routes.QUEUE, "Cola", Icons.Outlined.CloudQueue),
    Tab(Routes.HISTORY, Routes.history(), "Historial", Icons.Outlined.History),
    Tab(Routes.SETTINGS, Routes.SETTINGS, "Ajustes", Icons.Outlined.Settings),
)

private val supervisorTabs = listOf(
    Tab(Routes.REVIEW, Routes.REVIEW, "Revisión", Icons.Outlined.RateReview),
    Tab(Routes.HISTORY, Routes.history(), "Historial", Icons.Outlined.History),
    Tab(Routes.SETTINGS, Routes.SETTINGS, "Ajustes", Icons.Outlined.Settings),
)

private class AppSnackbarVisuals(val ui: UiMessage) : SnackbarVisuals {
    override val message: String get() = ui.text
    override val actionLabel: String? get() = null
    override val withDismissAction: Boolean get() = true
    override val duration: SnackbarDuration get() = SnackbarDuration.Short
}

@Composable
fun AppRoot(container: AppContainer) {
    val session by container.session.collectAsStateWithLifecycle()
    val online by container.online.collectAsStateWithLifecycle()
    val syncing by container.syncManager.syncing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Alertas in-app: siempre se muestra la más reciente.
    LaunchedEffect(Unit) {
        var job: Job? = null
        container.events.messages.collect { msg ->
            job?.cancel()
            job = launch { snackbar.showSnackbar(AppSnackbarVisuals(msg)) }
        }
    }

    CompositionLocalProvider(LocalOnline provides online, LocalSyncing provides syncing) {
        // Pantalla de carga visible al menos 1,5 s y fundido al pasar a login o a la app.
        var splashDone by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(1_500)
            splashDone = true
        }
        val state = if (!splashDone) SessionState.Loading else session
        Crossfade(targetState = state, animationSpec = tween(400), label = "sesion") { s ->
            when (s) {
                SessionState.Loading -> Splash()
                SessionState.LoggedOut -> LoginScreen()
                is SessionState.LoggedIn -> key(s.user.id) { MainNavigation(s.user, container, snackbar) }
            }
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Vitairos.VerdeNoche), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.vitairos_isotipo), contentDescription = "VITAIROS", modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(16.dp))
            Text("VITAIROS", color = Vitairos.Hueso, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = Vitairos.Cobre)
            Spacer(Modifier.height(12.dp))
            Text("Cargando…", color = Vitairos.Niebla, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MainNavigation(user: DemoUser, container: AppContainer, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tabs = if (user.role == UserRole.OPERADOR) operatorTabs else supervisorTabs
    val showBottomBar = tabs.any { it.route == currentRoute }

    val pendingCount by remember(user.id) {
        container.repository.observeAll().map { list ->
            list.count { it.report.createdBy == user.id && it.report.status.isPending }
        }
    }.collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Vitairos.Hueso,
        snackbarHost = { SnackbarHost(snackbar) { data -> AppSnackbar(data) } },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Vitairos.Blanco) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigateTab(tab.navRoute) },
                            icon = {
                                BadgedBox(badge = {
                                    if (tab.route == Routes.QUEUE && pendingCount > 0) {
                                        Badge(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche) {
                                            Text("$pendingCount")
                                        }
                                    }
                                }) { Icon(tab.icon, contentDescription = null) }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Vitairos.VerdeNoche,
                                selectedTextColor = Vitairos.VerdeNoche,
                                indicatorColor = Vitairos.Niebla,
                                unselectedIconColor = Vitairos.VerdeMineral,
                                unselectedTextColor = Vitairos.VerdeMineral,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (user.role == UserRole.OPERADOR) Routes.HOME else Routes.REVIEW,
            modifier = Modifier.padding(padding),
            // Animación al cambiar de pantalla: al avanzar entra desde la derecha, al volver desde la izquierda.
            enterTransition = { slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    user = user,
                    onNewReport = { nav.navigate(Routes.editor()) },
                    onOpenReport = { nav.navigate(Routes.detail(it)) },
                    onOpenHistory = { status -> nav.navigateTab(Routes.history(status)) },
                    onOpenQueue = { nav.navigateTab(Routes.QUEUE) },
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen(
                    user = user,
                    onOpenReport = { nav.navigate(Routes.detail(it)) },
                    onEdit = { nav.navigate(Routes.editor(it)) },
                )
            }
            composable(
                Routes.HISTORY,
                arguments = listOf(navArgument("status") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) {
                HistoryScreen(user = user, onOpenReport = { nav.navigate(Routes.detail(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen(user = user) }
            composable(Routes.REVIEW) {
                SupervisorScreen(user = user, onOpenReport = { nav.navigate(Routes.detail(it)) })
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("reportId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) {
                EditorScreen(
                    user = user,
                    onBack = { nav.popBackStack() },
                    onDone = { id ->
                        nav.navigate(Routes.detail(id)) { popUpTo(Routes.EDITOR) { inclusive = true } }
                    },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            ) {
                DetailScreen(
                    user = user,
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate(Routes.editor(id)) },
                    onOpenReport = { id -> nav.navigate(Routes.detail(id)) },
                )
            }
        }
    }
}

/**
 * Navegación de la barra inferior.
 * - La pestaña de inicio (destino inicial) SIEMPRE vuelve a la pantalla principal: se desapila todo
 *   lo que haya encima. No se usa saveState/restoreState, que restauraba pilas antiguas
 *   (bug: «Inicio» desde Cola o Ajustes abría Historial, y desde Historial no hacía nada).
 * - Las demás pestañas se apilan una sola vez sobre el inicio.
 */
private fun NavHostController.navigateTab(route: String) {
    val startId = graph.findStartDestination().id
    val startRoute = graph.findStartDestination().route
    if (route == startRoute) {
        if (!popBackStack(startId, inclusive = false)) {
            navigate(route) { launchSingleTop = true }
        }
        return
    }
    navigate(route) {
        popUpTo(startId) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
private fun AppSnackbar(data: SnackbarData) {
    val kind = (data.visuals as? AppSnackbarVisuals)?.ui?.kind ?: MessageKind.INFO
    val (container, content, icon) = when (kind) {
        MessageKind.SUCCESS -> Triple(Vitairos.OkVerde, Vitairos.Blanco, Icons.Outlined.CheckCircle)
        MessageKind.WARNING -> Triple(Vitairos.Oro, Vitairos.VerdeNoche, Icons.Outlined.WarningAmber)
        MessageKind.ERROR -> Triple(Vitairos.ErrorRojo, Vitairos.Blanco, Icons.Outlined.ErrorOutline)
        MessageKind.INFO -> Triple(Vitairos.VerdeProfundo, Vitairos.Hueso, Icons.Outlined.Info)
    }
    Snackbar(
        modifier = Modifier.padding(12.dp),
        containerColor = container,
        contentColor = content,
        dismissAction = null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content)
            Spacer(Modifier.width(10.dp))
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
