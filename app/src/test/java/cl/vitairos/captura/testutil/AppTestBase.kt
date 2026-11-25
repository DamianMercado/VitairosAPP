package cl.vitairos.captura.testutil

import android.net.ConnectivityManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.VitairosApp
import cl.vitairos.captura.data.prefs.MockApiMode
import cl.vitairos.captura.data.repo.ReportRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

/**
 * Base para pruebas que usan la aplicación real ([VitairosApp] + [AppContainer]) en Robolectric.
 * Cada prueba parte SIN conexión (simulación activa), con la API mock en modo Normal y sin sesión,
 * para que la sincronización automática no interfiera.
 */
abstract class AppTestBase {
    protected lateinit var app: VitairosApp
    protected lateinit var container: AppContainer
    protected lateinit var repo: ReportRepository
    protected lateinit var eventos: EventRecorder
    private val suscripciones = mutableListOf<Job>()

    @Before
    fun prepararApp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).setExecutor(SynchronousExecutor()).build(),
        )
        container = app.container
        repo = container.repository
        runBlocking {
            container.settingsStore.setSimulateOffline(true)
            container.settingsStore.setMockApiMode(MockApiMode.NORMAL)
            container.settingsStore.setUser(null)
        }
        waitUntil(descripcion = "ajustes iniciales") {
            val s = container.settings.value
            s.loaded && s.simulateOffline && s.userId == null && s.mockApiMode == MockApiMode.NORMAL
        }
        eventos = EventRecorder(container.events)
    }

    @After
    fun cerrarApp() {
        eventos.detener()
        suscripciones.forEach { it.cancel() }
    }

    /** Mantiene suscrito un StateFlow con WhileSubscribed durante la prueba. */
    protected fun observar(flow: Flow<*>) {
        suscripciones += flow.suscribir()
    }

    /** Simula que vuelve la red real y se desactiva la simulación sin conexión. */
    protected fun conectar() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).networkCallbacks.toList().forEach { it.onAvailable(ShadowNetwork.newInstance(42)) }
        runBlocking { container.settingsStore.setSimulateOffline(false) }
        waitUntil(descripcion = "conexión") { container.online.value }
    }
}
