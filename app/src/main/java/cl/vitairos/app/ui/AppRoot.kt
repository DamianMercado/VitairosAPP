package cl.vitairos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Navegación simple del prototipo básico (sin librerías extra):
 * Login → Inicio → Nuevo reporte / Detalle, con transiciones animadas.
 */
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val avisos = remember { SnackbarHostState() }

    // Muestra siempre el aviso más reciente.
    LaunchedEffect(Unit) {
        var job: Job? = null
        vm.mensajes.collect { texto ->
            job?.cancel()
            job = launch { avisos.showSnackbar(texto, withDismissAction = true) }
        }
    }

    // "inicio", "nuevo" o el id del reporte abierto en Detalle.
    var pantalla by rememberSaveable { mutableStateOf(INICIO) }
    LaunchedEffect(vm.usuario) { if (vm.usuario == null) pantalla = INICIO }

    // Pantalla de carga al abrir la app (2 segundos).
    var cargandoInicio by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2_000)
        cargandoInicio = false
    }

    // Pantalla que se debe mostrar ahora: carga, login o la pantalla actual.
    val destino = when {
        cargandoInicio -> CARGA
        vm.usuario == null -> LOGIN
        else -> pantalla
    }
    BackHandler(enabled = vm.usuario != null && pantalla != INICIO) { pantalla = INICIO }

    CompositionLocalProvider(LocalAvisos provides avisos) {
        // Animación al cambiar de pantalla: al avanzar entra desde la derecha,
        // al volver entra desde la izquierda; el login aparece/desaparece con fundido.
        AnimatedContent(
            targetState = destino,
            transitionSpec = {
                val desde = nivel(initialState)
                val hacia = nivel(targetState)
                val duracion = tween<IntOffset>(300)
                when {
                    desde == 0 || hacia == 0 -> fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    hacia > desde -> (slideInHorizontally(duracion) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(duracion) { -it / 3 } + fadeOut())
                    else -> (slideInHorizontally(duracion) { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally(duracion) { it } + fadeOut())
                }
            },
            label = "cambioPantalla",
        ) { p ->
            when (p) {
                CARGA -> CargaScreen()
                LOGIN -> LoginScreen(error = vm.errorLogin, alIngresar = vm::login)
                INICIO -> InicioScreen(vm, alNuevo = { pantalla = NUEVO }, alAbrir = { id -> pantalla = id })
                NUEVO -> NuevoReporteScreen(vm, alVolver = { pantalla = INICIO }, alGuardar = { id -> pantalla = id })
                else -> DetalleScreen(vm, id = p, alVolver = { pantalla = INICIO })
            }
        }
    }
}

/** Orden de las pantallas para saber si se avanza o se vuelve. */
private fun nivel(pantalla: String): Int = when (pantalla) {
    CARGA -> 0
    LOGIN -> 0
    INICIO -> 1
    NUEVO -> 2
    else -> 3 // Detalle
}

private const val CARGA = "carga"
private const val LOGIN = "login"
private const val INICIO = "inicio"
private const val NUEVO = "nuevo"
