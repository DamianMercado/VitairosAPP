package cl.vitairos.captura.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.prefs.MockApiMode
import cl.vitairos.captura.testutil.AppTestBase
import cl.vitairos.captura.testutil.TestData
import cl.vitairos.captura.testutil.crearEnEstado
import cl.vitairos.captura.testutil.waitUntil
import cl.vitairos.captura.ui.detail.DetailViewModel
import cl.vitairos.captura.ui.history.HistoryViewModel
import cl.vitairos.captura.ui.home.HomeViewModel
import cl.vitairos.captura.ui.login.LoginViewModel
import cl.vitairos.captura.ui.queue.QueueViewModel
import cl.vitairos.captura.ui.settings.SettingsViewModel
import cl.vitairos.captura.ui.supervisor.SupervisorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** ViewModels de inicio de sesión, inicio, cola, historial, supervisor y ajustes. */
@RunWith(AndroidJUnit4::class)
class ViewModelsTest : AppTestBase() {

    private val op = TestData.operador
    private val op2 = TestData.operador2

    private fun crear(usuario: cl.vitairos.captura.data.model.DemoUser, estado: ReportStatus, obs: String = "Observación de prueba") =
        runBlocking { repo.crearEnEstado(usuario, estado, TestData.campos(observaciones = obs)) }

    private fun estado(id: String) = runBlocking { repo.get(id)?.status }

    // ------------------------------------------------------------------ Login
    @Test
    fun `login rechaza credenciales incorrectas`() {
        val vm = LoginViewModel(container)
        vm.username = "operador1"
        vm.password = "mala"
        vm.login()
        assertEquals("Usuario o contraseña incorrectos.", vm.error)
        assertNull(container.settings.value.userId)
    }

    @Test
    fun `login correcto guarda solo el id del usuario y borra la contrasena`() {
        val vm = LoginViewModel(container)
        vm.showPassword = true
        vm.username = " Operador1 "
        vm.password = "demo123"
        vm.login()
        assertNull(vm.error)
        assertEquals("", vm.password)
        assertTrue(vm.showPassword)
        waitUntil { container.settings.value.userId == "u-op-01" }
        assertTrue(eventos.contiene("Sesión iniciada: Camila Rojas (Operador)"))
    }

    // ------------------------------------------------------------------ Inicio
    @Test
    fun `inicio muestra solo los reportes propios y sincronizar avisa sin conexion`() {
        crear(op, ReportStatus.BORRADOR)
        crear(op, ReportStatus.SINCRONIZADO)
        crear(op2, ReportStatus.EN_COLA)
        val vm = HomeViewModel(container, op)
        observar(vm.reports)
        waitUntil { vm.reports.value.size == 2 }
        assertTrue(vm.reports.value.all { it.report.createdBy == op.id })

        vm.syncNow()
        assertTrue(eventos.contiene("Sin conexión: los reportes permanecen en cola"))
    }

    // ------------------------------------------------------------------ Cola
    @Test
    fun `la cola lista pendientes propios en orden de creacion`() {
        val borrador = crear(op, ReportStatus.BORRADOR)
        val enCola = crear(op, ReportStatus.EN_COLA)
        val error = crear(op, ReportStatus.ERROR)
        crear(op, ReportStatus.SINCRONIZADO)
        crear(op2, ReportStatus.EN_COLA)
        val vm = QueueViewModel(container, op)
        observar(vm.reports)
        waitUntil { vm.reports.value.size == 3 }
        assertEquals(listOf(borrador.id, enCola.id, error.id), vm.reports.value.map { it.report.id })
    }

    @Test
    fun `sincronizar todo tiene antirrebote de 5 segundos`() {
        val vm = QueueViewModel(container, op)
        vm.syncNow()
        val hasta = vm.blockedUntil.value[QueueViewModel.KEY_ALL]!!
        assertTrue(hasta > System.currentTimeMillis())
        vm.syncNow()
        assertEquals("el segundo clic se ignora", hasta, vm.blockedUntil.value[QueueViewModel.KEY_ALL])
    }

    @Test
    fun `reintentar un reporte con error lo devuelve a la cola una sola vez por ventana`() {
        val r = crear(op, ReportStatus.ERROR)
        val vm = QueueViewModel(container, op)
        vm.retry(r.id)
        waitUntil { estado(r.id) == ReportStatus.EN_COLA }
        assertTrue(eventos.contiene("Reintento de envío solicitado."))
        assertTrue(vm.blockedUntil.value.containsKey(r.id))

        runBlocking { repo.markError(r.id, "otra vez", 2, 3) }
        vm.retry(r.id)
        Thread.sleep(100)
        waitUntil { true }
        assertEquals("bloqueado por el antirrebote", ReportStatus.ERROR, estado(r.id))
    }

    @Test
    fun `reintentar todos los errores`() {
        val a = crear(op, ReportStatus.ERROR)
        val b = crear(op, ReportStatus.ERROR)
        val vm = QueueViewModel(container, op)
        vm.retryAllErrors(listOf(a.id, b.id))
        waitUntil { estado(a.id) == ReportStatus.EN_COLA && estado(b.id) == ReportStatus.EN_COLA }
        assertTrue(eventos.contiene("2 reporte(s) con error volvieron a la cola."))
        assertTrue(vm.blockedUntil.value.containsKey(QueueViewModel.KEY_ERRORS))
        vm.retryAllErrors(listOf(a.id)) // ignorado por el antirrebote
    }

    @Test
    fun `eliminar desde la cola solo antes del envio`() {
        val vm = QueueViewModel(container, op)
        val borrador = crear(op, ReportStatus.BORRADOR)
        vm.delete(borrador)
        waitUntil { estado(borrador.id) == null }
        assertTrue(eventos.contiene("${borrador.folio} eliminado del dispositivo."))

        val enviando = crear(op, ReportStatus.ENVIANDO)
        vm.delete(enviando)
        assertTrue(eventos.contiene("No se pudo eliminar: el reporte ya comenzó a enviarse."))
        assertEquals(ReportStatus.ENVIANDO, estado(enviando.id))
    }

    // ------------------------------------------------------------------ Historial
    @Test
    fun `historial del operador muestra lo propio y permite buscar y filtrar`() {
        val a = crear(op, ReportStatus.SINCRONIZADO, obs = "Correa transportadora detenida")
        val b = crear(op, ReportStatus.ERROR, obs = "Sin novedades")
        crear(op2, ReportStatus.SINCRONIZADO)
        val vm = HistoryViewModel(container, op, SavedStateHandle())
        observar(vm.all)
        observar(vm.visible)
        waitUntil { vm.all.value.size == 2 && vm.visible.value.size == 2 }
        assertNull(vm.filter.value)

        vm.query.value = "  CORREA "
        waitUntil { vm.visible.value.map { it.report.id } == listOf(a.id) }
        vm.query.value = b.folio.lowercase()
        waitUntil { vm.visible.value.map { it.report.id } == listOf(b.id) }
        vm.query.value = "Extracción mina" // nombre del proceso
        waitUntil { vm.all.value.size == 2 }

        vm.query.value = ""
        vm.filter.value = ReportStatus.ERROR
        waitUntil { vm.visible.value.map { it.report.id } == listOf(b.id) }
    }

    @Test
    fun `historial del supervisor muestra todo y acepta el filtro inicial de navegacion`() {
        crear(op, ReportStatus.ERROR)
        crear(op2, ReportStatus.ERROR)
        crear(op2, ReportStatus.SINCRONIZADO)
        val vm = HistoryViewModel(container, TestData.supervisor, SavedStateHandle(mapOf("status" to "ERROR")))
        observar(vm.visible)
        observar(vm.all)
        assertEquals(ReportStatus.ERROR, vm.filter.value)
        waitUntil { vm.all.value.size == 3 && vm.visible.value.size == 2 }

        val invalido = HistoryViewModel(container, TestData.supervisor, SavedStateHandle(mapOf("status" to "NO_EXISTE")))
        assertNull(invalido.filter.value)
    }

    @Test
    fun `el supervisor ve todos los reportes`() {
        crear(op, ReportStatus.SINCRONIZADO)
        crear(op2, ReportStatus.BORRADOR)
        val vm = SupervisorViewModel(container)
        observar(vm.reports)
        waitUntil { vm.reports.value.size == 2 }
    }

    // ------------------------------------------------------------------ Ajustes
    @Test
    fun `ajustes muestran el id de instalacion y el uso de almacenamiento`() {
        val dir = File(app.filesDir, "attachments/R-X").apply { mkdirs() }
        File(dir, "a.jpg").writeBytes(ByteArray(2048))
        val vm = SettingsViewModel(container, op)
        assertEquals("…", vm.installId)
        vm.refresh()
        waitUntil { vm.installId.startsWith("INST-") && vm.storageBytes >= 2048 }
        assertEquals(container.settings.value, vm.settings.value)
    }

    @Test
    fun `ajustes cambian la simulacion sin conexion y el modo de la API`() {
        val vm = SettingsViewModel(container, op)
        vm.setOffline(false)
        waitUntil { !container.settings.value.simulateOffline }
        vm.setOffline(true)
        waitUntil { container.settings.value.simulateOffline }
        vm.setMode(MockApiMode.SIEMPRE_FALLA)
        waitUntil { container.settings.value.mockApiMode == MockApiMode.SIEMPRE_FALLA }
        assertTrue(eventos.contiene("API mock: Siempre falla"))
        vm.setMode(MockApiMode.NORMAL)
        waitUntil { container.settings.value.mockApiMode == MockApiMode.NORMAL }
    }

    @Test
    fun `generar 10 reportes sinteticos los deja en cola`() {
        val vm = SettingsViewModel(container, op)
        vm.generateSynthetic()
        assertTrue(vm.generating)
        waitUntil(timeoutMs = 30_000) { !vm.generating }
        assertEquals(10, runBlocking { repo.queuedCount() })
        assertTrue(vm.storageBytes > 0)
        assertTrue(eventos.contiene("10 reportes sintéticos creados y agregados a la cola."))
    }

    @Test
    fun `cerrar sesion conserva los reportes`() {
        runBlocking { container.settingsStore.setUser(op.id) }
        waitUntil { container.settings.value.userId == op.id }
        val r = crear(op, ReportStatus.EN_COLA)
        SettingsViewModel(container, op).logout()
        waitUntil { container.settings.value.userId == null }
        assertTrue(eventos.contiene("Sesión cerrada."))
        assertEquals(ReportStatus.EN_COLA, estado(r.id))
    }
}

/** Detalle del reporte: trazabilidad, acciones del operador y del supervisor. */
@RunWith(AndroidJUnit4::class)
class DetailViewModelTest : AppTestBase() {

    private val op = TestData.operador

    private fun vm(id: String, usuario: cl.vitairos.captura.data.model.DemoUser = op) =
        DetailViewModel(container, usuario, SavedStateHandle(mapOf("reportId" to id))).also { observar(it.ui) }

    private fun estado(id: String) = runBlocking { repo.get(id)?.status }

    @Test(expected = IllegalStateException::class)
    fun `sin id de reporte no se puede abrir el detalle`() {
        DetailViewModel(container, op, SavedStateHandle())
    }

    @Test
    fun `muestra el reporte, su bitacora mas reciente primero y sus versiones`() {
        val original = runBlocking { repo.crearEnEstado(op, ReportStatus.REQUIERE_CORRECCION) }
        val v2 = runBlocking { repo.createNewVersion(original.id, op)!! }
        runBlocking { repo.updateProcessing(original.id, "PROCESADO", """{"proceso":"Molienda","revision_humana":"pendiente"}""") }

        val det = vm(original.id)
        waitUntil { det.ui.value.loaded && det.ui.value.item != null && det.ui.value.children.isNotEmpty() && det.ui.value.extracted != null }
        val ui = det.ui.value
        assertEquals(original.id, det.reportId)
        assertEquals(listOf(v2.id), ui.children.map { it.id })
        assertNull(ui.parent)
        assertEquals("Molienda", ui.extracted!!["proceso"])
        assertTrue("más reciente primero", ui.events.first().timestamp >= ui.events.last().timestamp)

        val detV2 = vm(v2.id)
        waitUntil { detV2.ui.value.parent != null }
        assertEquals(original.id, detV2.ui.value.parent!!.id)
        assertNull(detV2.ui.value.extracted)
    }

    @Test
    fun `un resultado de procesamiento corrupto no rompe la pantalla`() {
        val r = runBlocking { repo.crearEnEstado(op, ReportStatus.SINCRONIZADO) }
        runBlocking { repo.updateProcessing(r.id, "PROCESADO", "{no-json") }
        val det = vm(r.id)
        waitUntil { det.ui.value.item?.report?.processingResultJson == "{no-json" }
        assertNull(det.ui.value.extracted)
    }

    @Test
    fun `enviar ahora encola el borrador con antirrebote`() {
        val r = runBlocking { repo.crearEnEstado(op, ReportStatus.BORRADOR) }
        val det = vm(r.id)
        det.sendNow()
        waitUntil { estado(r.id) == ReportStatus.EN_COLA }
        assertTrue(det.retryBlockedUntil > System.currentTimeMillis())
        val bloqueo = det.retryBlockedUntil
        det.sendNow()
        det.retry()
        assertEquals(bloqueo, det.retryBlockedUntil)
    }

    @Test
    fun `reintentar un reporte con error`() {
        val r = runBlocking { repo.crearEnEstado(op, ReportStatus.ERROR) }
        vm(r.id).retry()
        waitUntil { estado(r.id) == ReportStatus.EN_COLA }
        assertTrue(eventos.contiene("Reintento manual solicitado."))
    }

    @Test
    fun `eliminar desde el detalle`() {
        val r = runBlocking { repo.crearEnEstado(op, ReportStatus.EN_COLA) }
        val det = vm(r.id)
        waitUntil { det.ui.value.item != null }
        var eliminado = false
        det.delete { eliminado = true }
        waitUntil { eliminado }
        assertTrue(eventos.contiene("${r.folio} eliminado del dispositivo."))

        val sinc = runBlocking { repo.crearEnEstado(op, ReportStatus.SINCRONIZADO) }
        var llamado = false
        vm(sinc.id).delete { llamado = true }
        assertTrue(eventos.contiene("No se pudo eliminar"))
        assertFalse(llamado)
    }

    @Test
    fun `el supervisor solicita correccion y el operador crea la nueva version`() {
        val r = runBlocking { repo.crearEnEstado(op, ReportStatus.SINCRONIZADO) }
        vm(r.id, TestData.supervisor).markNeedsCorrection("  Falta la firma del jefe de turno ")
        waitUntil { estado(r.id) == ReportStatus.REQUIERE_CORRECCION }
        assertEquals("Falta la firma del jefe de turno", runBlocking { repo.get(r.id)!!.supervisorNote })
        assertTrue(eventos.contiene("Reporte marcado como «Requiere corrección»."))

        var nuevaId: String? = null
        vm(r.id).createNewVersion { nuevaId = it }
        waitUntil { nuevaId != null }
        val nueva = runBlocking { repo.get(nuevaId!!)!! }
        assertEquals(2, nueva.version)
        assertTrue(eventos.contiene("Nueva versión ${nueva.folio} creada."))

        // Un borrador no admite estas acciones
        val borrador = runBlocking { repo.crearEnEstado(op, ReportStatus.BORRADOR) }
        var otra: String? = null
        vm(borrador.id).createNewVersion { otra = it }
        vm(borrador.id, TestData.supervisor).markNeedsCorrection("x")
        Thread.sleep(200)
        waitUntil { true }
        assertNull(otra)
        assertEquals(ReportStatus.BORRADOR, estado(borrador.id))
    }

    @Test
    fun `consultar procesamiento sin conexion informa al usuario`() {
        val r = runBlocking { repo.crearEnEstado(op, ReportStatus.SINCRONIZADO) }
        val det = vm(r.id)
        det.checkProcessing()
        assertTrue(det.checking)
        waitUntil { !det.checking }
        assertTrue(eventos.contiene("Sin conexión: intenta nuevamente cuando haya señal."))
    }
}
