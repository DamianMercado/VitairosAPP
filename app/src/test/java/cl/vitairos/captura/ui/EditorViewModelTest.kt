package cl.vitairos.captura.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.testutil.AppTestBase
import cl.vitairos.captura.testutil.TestData
import cl.vitairos.captura.testutil.TestFilesProvider
import cl.vitairos.captura.testutil.assumeRutasUnix
import cl.vitairos.captura.testutil.crearEnEstado
import cl.vitairos.captura.testutil.limpiar
import cl.vitairos.captura.testutil.waitUntil
import cl.vitairos.captura.ui.editor.EditorUi
import cl.vitairos.captura.ui.editor.EditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Flujo del formulario: contexto → evidencia → revisión → cola, con autoguardado y edición. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorViewModelTest : AppTestBase() {

    private val op = TestData.operador
    private lateinit var origen: File

    @Before
    fun carpetaOrigen() {
        origen = File(app.cacheDir, "origen").apply { mkdirs() }
    }

    private fun nuevo(): EditorViewModel =
        EditorViewModel(container, op, SavedStateHandle()).also { vm -> waitUntil { !vm.ui.value.loading } }

    private fun editar(id: String): EditorViewModel =
        EditorViewModel(container, op, SavedStateHandle(mapOf("reportId" to id))).also { vm -> waitUntil { !vm.ui.value.loading } }

    private val EditorViewModel.s: EditorUi get() = ui.value

    private fun jpg(nombre: String, semilla: Int = 1, gris: Int? = null): Uri =
        TestFilesProvider.registrar(app, TestData.imagen(origen, nombre, semilla = semilla, gris = gris), "image/jpeg")

    private fun esperarImportacion(vm: EditorViewModel, adjuntos: Int) =
        waitUntil(timeoutMs = 15_000, descripcion = "importación") { !vm.s.busy && vm.s.attachments.size == adjuntos }

    private fun completarContexto(vm: EditorViewModel) {
        vm.setType("MANT")
        vm.setProcess("MAN")
        vm.setArea(Catalog.areas[2])
        vm.setShift(Catalog.shifts[1])
        vm.setExtra("CAEX-114")
        vm.setObservations("Cambio de correa")
    }

    private fun unicoReporte() = runBlocking { repo.observeAll().first().single().report }

    @Test
    fun `un reporte nuevo parte en el paso de contexto con datos automaticos`() {
        val vm = nuevo()
        assertTrue(vm.s.isNew)
        assertEquals(0, vm.s.step)
        assertEquals("Camila Rojas", vm.s.userName)
        assertEquals("Operador", vm.s.userRole)
        assertTrue(vm.s.deviceId.startsWith("INST-"))
        assertEquals("Se asigna al guardar", vm.s.folio)
        assertFalse(vm.s.hasInput)
        assertNull(vm.typeName())
    }

    @Test
    fun `los campos limpian sus errores y respetan los limites`() {
        val vm = nuevo()
        vm.next()
        assertEquals(setOf("type", "process"), vm.s.errors.keys)
        vm.setType("INC")
        vm.setExtra("x".repeat(80))
        assertEquals(60, vm.s.extra.length)
        vm.setType("INC") // mismo tipo: no borra el campo condicional
        assertEquals(60, vm.s.extra.length)
        vm.setType("MANT") // otro tipo: lo reinicia
        assertEquals("", vm.s.extra)
        vm.setProcess("MAN")
        assertFalse(vm.s.errors.containsKey("process"))
        vm.setObservations("y".repeat(600))
        assertEquals(500, vm.s.observations.length)
        assertEquals("Orden de mantención", vm.typeName())
        assertTrue(vm.s.hasInput)
    }

    @Test
    fun `avanzar de paso valida y guarda el borrador automaticamente`() {
        val vm = nuevo()
        completarContexto(vm)
        vm.goToStep(1) // avanzar con el indicador equivale a "Siguiente"
        waitUntil { vm.s.step == 1 }
        assertTrue(vm.s.folio.startsWith("VTR-"))
        val r = unicoReporte()
        assertEquals("MANT", r.reportTypeCode)
        assertEquals("CAEX-114", r.extraField)
        assertEquals(ReportStatus.BORRADOR, r.status)

        vm.next()
        assertEquals("Adjunta al menos un documento.", vm.s.errors["attachments"])
        vm.goToStep(0)
        assertEquals(0, vm.s.step)
        vm.back()
        assertEquals(0, vm.s.step)
        vm.goToStep(0)
        assertEquals(0, vm.s.step)
    }

    @Test
    fun `flujo completo hasta dejar el reporte en cola sin conexion`() {
        val vm = nuevo()
        completarContexto(vm)
        vm.next()
        waitUntil { vm.s.step == 1 }
        vm.onPicked(jpg("faena.jpg"), AttachmentSource.GALERIA)
        esperarImportacion(vm, 1)
        val att = vm.s.attachments.single()
        assertEquals(AttachmentSource.GALERIA, att.source)

        // La confirmación solo se habilita tras abrir cada adjunto
        vm.setQualityConfirmed(true)
        assertFalse(vm.s.qualityConfirmed)
        vm.next()
        waitUntil { vm.s.step == 2 }
        vm.submit()
        assertEquals(2, vm.s.step)
        assertTrue(vm.s.errors["quality"]!!.contains("(0/1 revisados)"))

        vm.markViewed(att.id)
        assertTrue(vm.s.allViewed)
        assertEquals(1, vm.s.viewedCount)
        vm.setQualityConfirmed(true)
        assertTrue(vm.s.effectiveConfirmed)
        vm.submit()
        waitUntil { vm.s.doneReportId != null }
        assertFalse(vm.s.busy)
        val r = unicoReporte()
        assertEquals(ReportStatus.EN_COLA, r.status)
        assertTrue(r.qualityConfirmed)
        assertTrue(eventos.contiene("Sin conexión: el reporte quedó guardado en cola"))
        // Al salir después de terminar no se vuelve a guardar
        vm.limpiar()
    }

    @Test
    fun `enviar con conexion informa que se esta enviando`() {
        conectar()
        val vm = nuevo()
        completarContexto(vm)
        vm.onPicked(jpg("conectado.jpg"), AttachmentSource.GALERIA)
        esperarImportacion(vm, 1)
        vm.markViewed(vm.s.attachments.single().id)
        vm.setQualityConfirmed(true)
        vm.submit()
        waitUntil { vm.s.doneReportId != null }
        assertTrue(eventos.contiene("Guardado localmente y en cola: enviando…"))
    }

    @Test
    fun `enviar con errores lleva al paso que corresponde`() {
        val vm = nuevo()
        vm.submit()
        assertEquals(0, vm.s.step)
        assertTrue(vm.s.errors.containsKey("type"))

        completarContexto(vm)
        vm.submit()
        assertEquals(1, vm.s.step)
        assertTrue(vm.s.errors.containsKey("attachments"))
    }

    @Test
    fun `adjuntos duplicados, rechazados y multiples`() {
        val vm = nuevo()
        val foto = jpg("duplicada.jpg")
        vm.onPicked(foto, AttachmentSource.GALERIA)
        esperarImportacion(vm, 1)
        vm.onPicked(foto, AttachmentSource.GALERIA)
        waitUntil { !vm.s.busy && vm.s.attachmentError != null }
        assertEquals("Ese archivo ya está adjunto en este reporte (duplicado).", vm.s.attachmentError)
        vm.dismissAttachmentError()
        assertNull(vm.s.attachmentError)

        val txt = TestFilesProvider.registrar(app, TestData.texto(origen, "notas.txt"), "text/plain")
        vm.onPicked(txt, AttachmentSource.GALERIA)
        waitUntil { !vm.s.busy && vm.s.attachmentError != null }
        assertTrue(vm.s.attachmentError!!.startsWith("Formato no permitido (text/plain)"))

        vm.onPickedMultiple(listOf(jpg("otra.jpg", semilla = 2), txt))
        waitUntil(timeoutMs = 15_000) { !vm.s.busy && vm.s.attachments.size == 2 }
        assertTrue(vm.s.attachmentError!!.startsWith("Se agregaron 1 de 2 archivos."))
        assertTrue(eventos.contiene("1 archivo(s) agregado(s) desde la galería."))

        vm.onPicked(null, AttachmentSource.PDF) // el usuario canceló el selector
        vm.onPickedMultiple(emptyList())
        assertEquals(2, vm.s.attachments.size)
    }

    @Test
    fun `advierte si el mismo archivo ya fue adjuntado en otro reporte`() {
        val archivo = TestData.imagen(origen, "repetida.jpg", semilla = 9)
        val primero = nuevo()
        primero.onPicked(TestFilesProvider.registrar(app, archivo, "image/jpeg"), AttachmentSource.GALERIA)
        esperarImportacion(primero, 1)

        val segundo = nuevo()
        segundo.onPicked(TestFilesProvider.registrar(app, archivo, "image/jpeg"), AttachmentSource.GALERIA)
        esperarImportacion(segundo, 1)
        assertTrue(eventos.contiene("ya fue adjuntado en"))
    }

    @Test
    fun `foto oscura muestra el aviso de calidad`() {
        val vm = nuevo()
        vm.onPicked(jpg("oscura.jpg", gris = 8), AttachmentSource.GALERIA)
        esperarImportacion(vm, 1)
        assertNotNull(vm.s.qualityPrompt)
        vm.dismissQualityPrompt()
        assertNull(vm.s.qualityPrompt)
    }

    @Test
    fun `reemplazar, rotar y quitar un adjunto`() {
        val vm = nuevo()
        vm.onPicked(jpg("original.jpg"), AttachmentSource.GALERIA)
        esperarImportacion(vm, 1)
        val original = vm.s.attachments.single()
        vm.markViewed(original.id)
        vm.setQualityConfirmed(true)

        vm.setReplaceTarget(original.id)
        vm.onPicked(jpg("nueva.jpg", semilla = 3), AttachmentSource.GALERIA)
        waitUntil(timeoutMs = 15_000) { !vm.s.busy && vm.s.attachments.singleOrNull()?.id != original.id }
        val nueva = vm.s.attachments.single()
        assertFalse("un adjunto nuevo exige revisar otra vez", vm.s.qualityConfirmed)
        assertFalse(File(original.localPath!!).exists())

        var rotada: AttachmentEntity? = null
        vm.rotate(nueva) { rotada = it }
        waitUntil { rotada != null && !vm.s.busy }
        assertNotEquals(nueva.localPath, vm.s.attachments.single().localPath)

        vm.removeAttachment(vm.s.attachments.single())
        waitUntil { vm.s.attachments.isEmpty() }
        assertTrue(runBlocking { repo.attachmentsFor(unicoReporte().id) }.isEmpty())
    }

    @Test
    fun `camara cancelada y selectores no disponibles`() {
        val vm = nuevo()
        vm.onCameraResult(false) // el usuario canceló la cámara
        assertTrue(vm.s.attachments.isEmpty())
        vm.onCameraResult(true) // sin un destino preparado no se importa nada
        assertTrue(vm.s.attachments.isEmpty())

        vm.onCameraUnavailable()
        assertTrue(vm.s.attachmentError!!.contains("cámara"))
        vm.onPickerUnavailable()
        assertEquals("No se encontró un selector de archivos en el dispositivo.", vm.s.attachmentError)
    }

    @Test
    fun `una foto tomada con la camara se agrega como adjunto`() {
        assumeRutasUnix()
        val vm = nuevo()
        val destino = vm.prepareCamera(null)
        val foto = TestData.imagen(File(app.cacheDir, "camera"), destino.lastPathSegment!!)
        shadowOf(app.contentResolver).registerInputStreamSupplier(destino) { foto.inputStream() }
        vm.onCameraResult(true)
        esperarImportacion(vm, 1)
        assertEquals(AttachmentSource.CAMARA, vm.s.attachments.single().source)
    }

    @Test
    fun `guardar borrador`() {
        val vacio = nuevo()
        vacio.saveDraft()
        waitUntil { vacio.s.closeRequested }

        val vm = nuevo()
        vm.setType("PROD")
        vm.saveDraft()
        waitUntil { vm.s.doneReportId != null }
        assertEquals(ReportStatus.BORRADOR, unicoReporte().status)
        assertTrue(eventos.contiene("Borrador guardado en el dispositivo."))
    }

    @Test
    fun `al salir sin guardar se conserva lo escrito como borrador`() {
        val vm = nuevo()
        vm.setObservations("Texto que no debe perderse")
        vm.limpiar()
        waitUntil { runBlocking { repo.observeAll().first().size } == 1 }
        assertEquals("Texto que no debe perderse", unicoReporte().observations)
        assertTrue(eventos.contiene("Se guardó un borrador con lo ingresado."))

        // Sin nada escrito no se crea nada
        nuevo().limpiar()
        Thread.sleep(200)
        assertEquals(1, runBlocking { repo.observeAll().first().size })
    }

    @Test
    fun `editar un reporte en cola lo retira y al salir vuelve a la cola`() {
        val dir = File(app.filesDir, "origen2")
        val r = runBlocking {
            val base = repo.crearEnEstado(op, ReportStatus.BORRADOR, archivo = TestData.imagen(dir, "e.jpg"))
            repo.enqueue(base.id)
            repo.get(base.id)!!
        }
        val vm = editar(r.id)
        assertFalse(vm.s.isNew)
        assertTrue(vm.s.wasQueued)
        assertEquals(r.folio, vm.s.folio)
        assertEquals(2, vm.s.step)
        assertTrue("revisión previa vigente", vm.s.allViewed && vm.s.effectiveConfirmed)
        assertEquals(ReportStatus.BORRADOR, runBlocking { repo.get(r.id)!!.status })

        vm.setObservations("Editado")
        vm.limpiar()
        waitUntil { runBlocking { repo.get(r.id)!!.status } == ReportStatus.EN_COLA }
        assertEquals("Editado", runBlocking { repo.get(r.id)!!.observations })
    }

    @Test
    fun `editar un borrador incompleto y salir guarda los cambios`() {
        val r = runBlocking { repo.createDraft(op, TestData.campos().copy(typeCode = null, processCode = null, qualityConfirmed = false)) }
        val vm = editar(r.id)
        assertEquals(0, vm.s.step)
        assertNull(vm.s.typeCode)
        assertTrue(vm.s.viewedIds.isEmpty())
        vm.setProcess("FLO")
        vm.limpiar()
        waitUntil { runBlocking { repo.get(r.id)!!.processCode } == "FLO" }
        assertTrue(eventos.contiene("Cambios guardados en el dispositivo."))
    }

    @Test
    fun `un reporte sincronizado no se puede editar`() {
        val r = runBlocking { repo.crearEnEstado(op, ReportStatus.SINCRONIZADO) }
        val vm = editar(r.id)
        assertTrue(vm.s.closeRequested)
        assertTrue(eventos.contiene("Este reporte ya no puede editarse"))
        vm.saveDraft()
        assertTrue(eventos.contiene("No se pudo guardar: el reporte ya no es editable."))
        vm.limpiar() // al cerrar no intenta guardar nada
        assertEquals(ReportStatus.SINCRONIZADO, runBlocking { repo.get(r.id)!!.status })
    }

    @Test
    fun `no se puede encolar si el reporte dejo de ser editable`() {
        val vm = nuevo()
        completarContexto(vm)
        vm.onPicked(jpg("x.jpg"), AttachmentSource.GALERIA)
        esperarImportacion(vm, 1)
        vm.markViewed(vm.s.attachments.single().id)
        vm.setQualityConfirmed(true)
        // Mientras tanto la sincronización tomó el reporte
        val id = unicoReporte().id
        runBlocking {
            repo.enqueue(id)
            repo.markSending(id, 1)
        }
        vm.submit()
        assertTrue(eventos.contiene("No se pudo agregar a la cola: el reporte ya no es editable."))
        assertFalse(vm.s.busy)
        assertNull(vm.s.doneReportId)
    }
}
