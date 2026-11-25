package cl.vitairos.captura

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.remote.OfflineException
import cl.vitairos.captura.sync.ConnectivityMonitor
import cl.vitairos.captura.sync.SyncWorker
import cl.vitairos.captura.testutil.AppTestBase
import cl.vitairos.captura.testutil.TestData
import cl.vitairos.captura.testutil.crearEnEstado
import cl.vitairos.captura.testutil.waitUntil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** Contenedor de dependencias, sesión, conectividad efectiva y respaldo con WorkManager. */
@RunWith(AndroidJUnit4::class)
class AppContainerTest : AppTestBase() {

    @Test
    fun `la sesion se deriva del usuario guardado en DataStore`() {
        waitUntil { container.session.value == SessionState.LoggedOut }
        assertNull(container.currentUser())

        runBlocking { container.settingsStore.setUser("u-sup-01") }
        waitUntil { container.currentUser()?.id == "u-sup-01" }
        assertEquals("Valentina Soto", (container.session.value as SessionState.LoggedIn).user.displayName)

        runBlocking { container.settingsStore.setUser("usuario-inexistente") }
        waitUntil { container.session.value == SessionState.LoggedOut }
    }

    @Test
    fun `la conexion efectiva exige red real y que no se simule el modo offline`() {
        assertFalse(container.online.value)
        val cm = app.getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).networkCallbacks.toList().forEach { it.onAvailable(ShadowNetwork.newInstance(7)) }
        waitUntil { container.connectivity.isNetworkAvailable.value }
        assertFalse("con la simulación activa sigue sin conexión", container.online.value)

        conectar()
        assertTrue(container.online.value)
        // Al recuperar la conexión se programa el respaldo de WorkManager
        waitUntil {
            WorkManager.getInstance(app).getWorkInfosForUniqueWork("vitairos-sync-queue").get().isNotEmpty()
        }
    }

    @Test
    fun `la API del contenedor usa el mock y respeta la simulacion sin conexion`() {
        try {
            runBlocking { container.api.processing("R-1") }
            fail("se esperaba OfflineException")
        } catch (e: OfflineException) {
            assertEquals("Sin conexión a Internet", e.message)
        }
    }

    @Test
    fun `con conexion el mock recibe el reporte de punta a punta`() {
        val dir = java.io.File(app.filesDir, "origen")
        val r = runBlocking {
            repo.crearEnEstado(TestData.operador, ReportStatus.EN_COLA, archivo = TestData.texto(dir, "evidencia.jpg", "jpg"))
        }
        conectar()
        waitUntil(timeoutMs = 15_000, descripcion = "sincronización") {
            runBlocking { repo.get(r.id)!!.status } == ReportStatus.SINCRONIZADO
        }
        val s = runBlocking { repo.get(r.id)!! }
        assertTrue(s.serverReceiptId!!.startsWith("VIA-"))
        assertTrue(eventos.contiene("${s.folio} enviado correctamente"))
        // Consulta del procesamiento simulado a través del interceptor real
        val msg = runBlocking { container.syncManager.checkProcessing(r.id) }
        assertEquals("En cola de procesamiento (simulación).", msg)
    }

    @Test
    fun `el trabajo de respaldo es unico y procesa la cola`() {
        SyncWorker.schedule(app)
        SyncWorker.schedule(app)
        val infos = WorkManager.getInstance(app).getWorkInfosForUniqueWork("vitairos-sync-queue").get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)

        val sinAdjuntos = runBlocking { repo.crearEnEstado(TestData.operador, ReportStatus.EN_COLA) }
        conectar()
        val worker = TestListenableWorkerBuilder<SyncWorker>(app).build()
        val resultado = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals(ReportStatus.ERROR, runBlocking { repo.get(sinAdjuntos.id)!!.status })
    }
}

/** Monitor de conectividad real del dispositivo (callbacks del sistema). */
@RunWith(AndroidJUnit4::class)
class ConnectivityMonitorTest : AppTestBase() {

    @Test
    fun `refleja las redes disponibles y perdidas`() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val antes = shadowOf(cm).networkCallbacks.toSet()
        val monitor = ConnectivityMonitor(app)
        val propios = shadowOf(cm).networkCallbacks.toSet() - antes
        assertEquals(1, propios.size)
        val wifi = ShadowNetwork.newInstance(1)
        val datos = ShadowNetwork.newInstance(2)

        propios.forEach { it.onAvailable(wifi) }
        assertTrue(monitor.isNetworkAvailable.value)
        propios.forEach { it.onAvailable(datos) }
        propios.forEach { it.onLost(wifi) }
        assertTrue("queda una red disponible", monitor.isNetworkAvailable.value)
        propios.forEach { it.onLost(datos) }
        assertFalse(monitor.isNetworkAvailable.value)
    }

    @Test
    fun `el estado inicial depende de la capacidad de Internet de la red activa`() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val conInternet = ShadowNetworkCapabilities.newInstance()
        shadowOf(conInternet).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, conInternet)
        assertTrue(ConnectivityMonitor(app).isNetworkAvailable.value)

        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, ShadowNetworkCapabilities.newInstance())
        assertFalse(ConnectivityMonitor(app).isNetworkAvailable.value)
    }
}
