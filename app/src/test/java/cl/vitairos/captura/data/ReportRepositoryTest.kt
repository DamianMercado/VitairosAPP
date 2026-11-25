package cl.vitairos.captura.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.vitairos.captura.data.files.AttachmentStorage
import cl.vitairos.captura.data.local.AppDatabase
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.prefs.SettingsStore
import cl.vitairos.captura.data.repo.ReportRepository
import cl.vitairos.captura.testutil.TestData
import cl.vitairos.captura.testutil.crearEnEstado
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Repositorio sobre una base Room en memoria: creación, edición, cola, trazabilidad y versiones. */
@RunWith(AndroidJUnit4::class)
class ReportRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var storage: AttachmentStorage
    private lateinit var repo: ReportRepository
    private val op = TestData.operador

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        storage = AttachmentStorage(context)
        repo = ReportRepository(db, storage, SettingsStore(context))
    }

    @After
    fun tearDown() = db.close()

    private fun archivoDeReporte(reportId: String, nombre: String = "evidencia.jpg"): File =
        File(context.filesDir, "attachments/$reportId").apply { mkdirs() }.let { dir ->
            File(dir, nombre).apply { writeBytes(ByteArray(256) { it.toByte() }) }
        }

    private fun eventos(id: String) = runBlocking { repo.observeEvents(id).first() }

    @Test
    fun `crear borrador asigna folio, usuario, dispositivo y registra el evento`() = runBlocking {
        val r = repo.createDraft(op, TestData.campos(tipo = "MANT", proceso = "MAN", extra = "CAEX-114"))
        assertTrue(r.folio.matches(Regex("VTR-\\d{6}-[0-9A-F]{4}")))
        assertEquals(ReportStatus.BORRADOR, r.status)
        assertEquals("v2.0", r.reportTypeVersion)
        assertEquals("u-op-01", r.createdBy)
        assertEquals("Camila Rojas", r.createdByName)
        assertEquals("Operador", r.createdByRole)
        assertTrue(r.deviceInstallId.startsWith("INST-"))
        assertEquals(1, r.version)
        assertNull(r.parentReportId)
        assertEquals(r, repo.get(r.id))
        assertEquals("Borrador creado por Camila Rojas", eventos(r.id).single().message)

        // El identificador de instalación es estable
        val otro = repo.createDraft(op, TestData.campos())
        assertEquals(r.deviceInstallId, otro.deviceInstallId)
    }

    @Test
    fun `borrador incompleto guarda codigos vacios`() = runBlocking {
        val r = repo.createDraft(op, TestData.campos().copy(typeCode = null, processCode = null))
        assertEquals("", r.reportTypeCode)
        assertEquals("", r.reportTypeVersion)
        assertEquals("", r.processCode)
    }

    @Test
    fun `guardar campos solo mientras el estado es editable`() = runBlocking {
        val r = repo.createDraft(op, TestData.campos())
        val nuevos = TestData.campos(tipo = "INC", proceso = "SSO", extra = "  ", observaciones = "Editado")
        assertTrue(repo.saveFields(r.id, nuevos))
        val editado = repo.get(r.id)!!
        assertEquals("INC", editado.reportTypeCode)
        assertEquals("v1.0", editado.reportTypeVersion)
        assertNull("un campo extra en blanco se guarda como null", editado.extraField)
        assertEquals("Editado", editado.observations)

        val sinc = repo.crearEnEstado(op, ReportStatus.SINCRONIZADO)
        assertFalse(repo.saveFields(sinc.id, nuevos))
    }

    @Test
    fun `tomar para edicion retira de la cola y respeta estados no editables`() = runBlocking {
        val enCola = repo.crearEnEstado(op, ReportStatus.EN_COLA)
        assertTrue(repo.takeForEditing(enCola.id))
        assertEquals(ReportStatus.BORRADOR, repo.get(enCola.id)!!.status)
        assertTrue(eventos(enCola.id).any { it.message == "Retirado de la cola para edición" })

        assertTrue("un borrador sigue siendo editable", repo.takeForEditing(enCola.id))
        assertTrue(repo.takeForEditing(repo.crearEnEstado(op, ReportStatus.ERROR).id))
        assertFalse(repo.takeForEditing(repo.crearEnEstado(op, ReportStatus.ENVIANDO).id))
        assertFalse(repo.takeForEditing("no-existe"))
    }

    @Test
    fun `adjuntos se agregan, actualizan, eliminan y detectan duplicados entre reportes`() = runBlocking {
        val a = repo.createDraft(op, TestData.campos())
        val b = repo.createDraft(op, TestData.campos())
        val archivo = archivoDeReporte(a.id)
        val adj = TestData.adjunto(a.id, archivo, sha = "hash-compartido")
        repo.addAttachment(adj)
        repo.addAttachment(TestData.adjunto(b.id, archivoDeReporte(b.id), sha = "hash-compartido"))

        assertEquals(listOf(adj), repo.attachmentsFor(a.id))
        assertEquals(listOf(adj), repo.observeAttachments(a.id).first())
        assertEquals(listOf(a.folio), repo.duplicateFolios("hash-compartido", b.id))
        assertTrue(repo.duplicateFolios("otro-hash", b.id).isEmpty())
        assertTrue(eventos(a.id).any { it.message.startsWith("Adjunto agregado: evidencia.jpg (Galería)") })

        repo.updateAttachment(adj.copy(displayName = "renombrado.jpg"))
        assertEquals("renombrado.jpg", repo.attachmentsFor(a.id).single().displayName)

        repo.removeAttachment(adj)
        assertTrue(repo.attachmentsFor(a.id).isEmpty())
        assertFalse("se borra el archivo físico", archivo.exists())
        assertTrue(eventos(a.id).any { it.message == "Adjunto eliminado: evidencia.jpg" })
        assertEquals(1, repo.observe(b.id).first()!!.attachments.size)
    }

    @Test
    fun `encolar, contar y eliminar solo antes del envio`() = runBlocking {
        val r = repo.createDraft(op, TestData.campos())
        val archivo = archivoDeReporte(r.id)
        assertTrue(repo.enqueue(r.id))
        assertEquals(1, repo.queuedCount())
        assertEquals(listOf(r.id), repo.queued().map { it.id })
        assertTrue(repo.enqueue(r.id, manualRetry = true))
        assertEquals("Reintento manual solicitado", repo.get(r.id)!!.lastMessage)

        assertTrue(repo.delete(r.id))
        assertNull(repo.get(r.id))
        assertFalse("se eliminan sus archivos", archivo.exists())

        val enviando = repo.crearEnEstado(op, ReportStatus.ENVIANDO)
        assertFalse(repo.delete(enviando.id))
        assertFalse(repo.enqueue(repo.crearEnEstado(op, ReportStatus.SINCRONIZADO).id))
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `ciclo de sincronizacion actualiza intentos, comprobante y mensajes`() = runBlocking {
        val r = repo.crearEnEstado(op, ReportStatus.EN_COLA)
        assertTrue(repo.markSending(r.id, 1))
        assertFalse("ya no está en cola", repo.markSending(r.id, 2))
        val enviando = repo.get(r.id)!!
        assertEquals(ReportStatus.ENVIANDO, enviando.status)
        assertEquals(1, enviando.attemptCount)
        assertNotNull(enviando.lastAttemptAt)

        repo.markRetryScheduled(r.id, retriesUsed = 1, delaySeconds = 5, reason = "Falló el envío: HTTP 503", attempt = 1)
        val reintento = repo.get(r.id)!!
        assertEquals(ReportStatus.EN_COLA, reintento.status)
        assertEquals(1, reintento.autoRetryCount)
        assertTrue(reintento.nextRetryAt!! > System.currentTimeMillis())
        assertEquals("Falló el envío: HTTP 503 · Reintento automático 1/3 en 5 s", reintento.lastMessage)

        repo.clearBackoff()
        assertNull(repo.get(r.id)!!.nextRetryAt)

        repo.markOfflineRequeue(r.id, retriesUsed = 1, attempt = null)
        assertEquals("Sin conexión: se enviará automáticamente al recuperar la señal", repo.get(r.id)!!.lastMessage)

        repo.markError(r.id, "Se agotaron los reintentos", attempt = 4, retriesUsed = 3)
        assertEquals(ReportStatus.ERROR, repo.get(r.id)!!.status)

        repo.enqueue(r.id, manualRetry = true)
        repo.markSending(r.id, 5)
        repo.markSynced(r.id, "VIA-ABC12345", "Reporte recibido.", "EN_REVISION", 5)
        val ok = repo.get(r.id)!!
        assertEquals(ReportStatus.SINCRONIZADO, ok.status)
        assertEquals("VIA-ABC12345", ok.serverReceiptId)
        assertEquals("EN_REVISION", ok.processingStatus)
        assertNotNull(ok.syncedAt)
        val ultimos = eventos(r.id).map { it.status }
        assertEquals(ReportStatus.SINCRONIZADO, ultimos.last())
        assertTrue(eventos(r.id).last().message.contains("Comprobante: VIA-ABC12345"))
    }

    @Test
    fun `envios interrumpidos vuelven a la cola al reiniciar`() = runBlocking {
        assertEquals(0, repo.recoverInterrupted())
        val a = repo.crearEnEstado(op, ReportStatus.ENVIANDO)
        val b = repo.crearEnEstado(op, ReportStatus.ENVIANDO)
        assertEquals(2, repo.recoverInterrupted())
        assertEquals(ReportStatus.EN_COLA, repo.get(a.id)!!.status)
        assertEquals(ReportStatus.EN_COLA, repo.get(b.id)!!.status)
    }

    @Test
    fun `supervisor marca correccion y se crea una nueva version sin borrar la anterior`() = runBlocking {
        val borrador = repo.createDraft(op, TestData.campos())
        assertFalse(repo.markNeedsCorrection(borrador.id, "nota", TestData.supervisor))
        assertNull("un borrador no genera nuevas versiones", repo.createNewVersion(borrador.id, op))
        assertNull(repo.createNewVersion("no-existe", op))

        val sinc = repo.crearEnEstado(op, ReportStatus.SINCRONIZADO, TestData.campos(tipo = "INC", proceso = "SSO", extra = "Alto"))
        assertTrue(repo.markNeedsCorrection(sinc.id, "Falta la firma", TestData.supervisor))
        val observado = repo.get(sinc.id)!!
        assertEquals(ReportStatus.REQUIERE_CORRECCION, observado.status)
        assertEquals("Falta la firma", observado.supervisorNote)
        assertEquals("Observado por Valentina Soto: Falta la firma", observado.lastMessage)

        val v2 = repo.createNewVersion(sinc.id, op)!!
        assertEquals(2, v2.version)
        assertEquals(sinc.id, v2.parentReportId)
        assertEquals("INC", v2.reportTypeCode)
        assertEquals("Alto", v2.extraField)
        assertFalse(v2.qualityConfirmed)
        assertTrue(eventos(v2.id).single().message.startsWith("Versión 2 creada como corrección de ${sinc.folio}"))
        assertTrue(eventos(sinc.id).any { it.message.contains("Se generó la nueva versión ${v2.folio}") })
        assertNotNull("el registro anterior se conserva", repo.get(sinc.id))

        // También se puede versionar un reporte sincronizado sin observación
        val otro = repo.crearEnEstado(op, ReportStatus.SINCRONIZADO)
        assertEquals(2, repo.createNewVersion(otro.id, op)!!.version)
    }

    @Test
    fun `procesamiento simulado se registra solo cuando cambia`() = runBlocking {
        repo.updateProcessing("no-existe", "PROCESADO", null) // no falla
        val r = repo.crearEnEstado(op, ReportStatus.SINCRONIZADO)
        val antes = eventos(r.id).size
        repo.updateProcessing(r.id, "PROCESADO", "{\"proceso\":\"Extracción mina\"}")
        repo.updateProcessing(r.id, "PROCESADO", "{\"proceso\":\"Extracción mina\"}")
        assertEquals(antes + 1, eventos(r.id).size)
        assertEquals("PROCESADO", repo.get(r.id)!!.processingStatus)
        assertEquals("Procesamiento VITAIROS (simulación): PROCESADO", eventos(r.id).last().message)
    }

    @Test
    fun `datos sinteticos crean reportes completos en cola`() = runBlocking {
        assertEquals(6, repo.createSyntheticQueued(op, 6))
        val todos = repo.observeAll().first()
        assertEquals(6, todos.size)
        assertEquals(6, repo.queuedCount())
        todos.forEach { item ->
            assertEquals(1, item.attachments.size)
            assertTrue(item.report.qualityConfirmed)
            assertTrue(item.attachments.single().displayName.startsWith("sintetico_VTR-"))
            assertTrue(File(item.attachments.single().localPath!!).exists())
        }
        // MANT e INC llevan su campo condicional
        assertTrue(todos.any { it.report.reportTypeCode == "MANT" && it.report.extraField!!.startsWith("EQ-") })
        assertTrue(todos.any { it.report.reportTypeCode == "INC" && it.report.extraField in listOf("Bajo", "Medio", "Alto", "Crítico") })
    }

    @Test
    fun `folio nuevo usa la fecha indicada`() {
        // 2026-11-24 12:00 UTC
        val folio = ReportRepository.newFolio(1_795_000_000_000L)
        assertTrue(folio.startsWith("VTR-2611"))
        assertEquals(15, folio.length)
    }
}
