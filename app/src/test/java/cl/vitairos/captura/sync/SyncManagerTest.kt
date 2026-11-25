package cl.vitairos.captura.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.vitairos.captura.data.files.AttachmentStorage
import cl.vitairos.captura.data.local.AppDatabase
import cl.vitairos.captura.data.local.ReportEntity
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.prefs.SettingsStore
import cl.vitairos.captura.data.remote.OfflineException
import cl.vitairos.captura.data.remote.ProcessingResponse
import cl.vitairos.captura.data.remote.ReportMetadataDto
import cl.vitairos.captura.data.remote.UploadResponse
import cl.vitairos.captura.data.remote.VitairosApi
import cl.vitairos.captura.data.repo.ReportRepository
import cl.vitairos.captura.testutil.EventRecorder
import cl.vitairos.captura.testutil.TestData
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.File
import java.io.IOException

/** Motor de sincronización: éxito, reintentos con backoff, error manual, sin conexión y cancelación. */
@RunWith(AndroidJUnit4::class)
class SyncManagerTest {

    /** API falsa: cada prueba decide qué responde. */
    private class FakeApi : VitairosApi {
        val metadatas = mutableListOf<ReportMetadataDto>()
        val nombresArchivos = mutableListOf<String>()
        var onUpload: suspend () -> Response<UploadResponse> = { ok("VIA-0000TEST") }
        var onProcessing: suspend (String) -> Response<ProcessingResponse> = { Response.success(null) }

        override suspend fun uploadReport(metadata: RequestBody, files: List<MultipartBody.Part>): Response<UploadResponse> {
            val json = Buffer().also { metadata.writeTo(it) }.readUtf8()
            metadatas += Gson().fromJson(json, ReportMetadataDto::class.java)
            files.forEach { p -> p.headers?.get("Content-Disposition")?.let { nombresArchivos += it } }
            return onUpload()
        }

        override suspend fun processing(reportId: String): Response<ProcessingResponse> = onProcessing(reportId)
    }

    companion object {
        fun ok(receipt: String, duplicate: Boolean = false): Response<UploadResponse> = Response.success(
            if (duplicate) 200 else 201,
            UploadResponse(receipt, "id", "RECIBIDO", if (duplicate) "Ya recibido." else "Recibido.", duplicate, "EN_REVISION"),
        )

        fun errorHttp(code: Int, body: String): Response<UploadResponse> =
            Response.error(code, body.toResponseBody("application/json".toMediaType()))
    }

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: ReportRepository
    private lateinit var scope: CoroutineScope
    private lateinit var events: AppEvents
    private lateinit var recorder: EventRecorder
    private val online = MutableStateFlow(true)
    private val api = FakeApi()
    private var programaciones = 0
    private lateinit var sync: SyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = ReportRepository(db, AttachmentStorage(context), SettingsStore(context))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        events = AppEvents()
        recorder = EventRecorder(events)
        sync = SyncManager(repo, api, Gson(), online, events, scope) { programaciones++ }
    }

    @After
    fun tearDown() {
        recorder.detener()
        scope.cancel()
        db.close()
    }

    /** Reporte completo En cola con un adjunto real en almacenamiento privado. */
    private fun reporteEnCola(conArchivo: Boolean = true, reintentosUsados: Int = 0): ReportEntity = runBlocking {
        val r = repo.createDraft(TestData.operador, TestData.campos(tipo = "INC", proceso = "SSO", extra = "Alto"))
        if (conArchivo) {
            val f = File(context.filesDir, "attachments/${r.id}/evidencia final.jpg").apply {
                parentFile!!.mkdirs(); writeBytes(ByteArray(64) { 7 })
            }
            repo.addAttachment(TestData.adjunto(r.id, f))
        }
        repo.enqueue(r.id)
        if (reintentosUsados > 0) repo.markOfflineRequeue(r.id, reintentosUsados, null)
        repo.get(r.id)!!
    }

    private fun estado(id: String) = runBlocking { repo.get(id)!! }
    private fun procesar() = runBlocking { withTimeout(15_000) { sync.processQueue(waitForLock = true) } }

    private fun esperar(timeoutMs: Long = 10_000, condicion: () -> Boolean) {
        val fin = System.currentTimeMillis() + timeoutMs
        while (!condicion()) {
            if (System.currentTimeMillis() > fin) throw AssertionError("Tiempo agotado")
            Thread.sleep(20)
        }
    }

    @Test
    fun `envio exitoso sincroniza el reporte y notifica el comprobante`() {
        val r = reporteEnCola()
        procesar()
        val s = estado(r.id)
        assertEquals(ReportStatus.SINCRONIZADO, s.status)
        assertEquals("VIA-0000TEST", s.serverReceiptId)
        assertEquals("EN_REVISION", s.processingStatus)
        assertEquals(1, s.attemptCount)
        assertTrue(recorder.contiene("${r.folio} enviado correctamente · Comprobante VIA-0000TEST"))

        val meta = api.metadatas.single()
        assertEquals(r.id, meta.reportId)
        assertEquals("Reporte de incidente", meta.reportType)
        assertEquals("Seguridad y salud ocupacional", meta.process)
        assertEquals("Alto", meta.extraField)
        assertEquals(1, meta.attempt)
        assertEquals(1, meta.attachments.size)
        assertTrue("el nombre del archivo se sanea", api.nombresArchivos.single().contains("filename=\"evidencia_final.jpg\""))
        assertFalse(sync.syncing.value)
    }

    @Test
    fun `un reporte ya recibido no se duplica`() {
        api.onUpload = { ok("VIA-DUPLICAD", duplicate = true) }
        val r = reporteEnCola()
        procesar()
        assertEquals(ReportStatus.SINCRONIZADO, estado(r.id).status)
        assertTrue(recorder.contiene("${r.folio} ya estaba recibido por VITAIROS: no se duplicó."))
    }

    @Test
    fun `respuesta exitosa sin cuerpo usa valores por defecto`() {
        api.onUpload = { Response.success(201, null as UploadResponse?) }
        val r = reporteEnCola()
        procesar()
        val s = estado(r.id)
        assertEquals(ReportStatus.SINCRONIZADO, s.status)
        assertNull(s.serverReceiptId)
        assertEquals("Reporte recibido.", s.lastMessage)
        assertTrue(recorder.contiene("Comprobante —"))
    }

    @Test
    fun `sin adjuntos locales el reporte pasa a error con accion manual`() {
        val r = reporteEnCola(conArchivo = false)
        procesar()
        val s = estado(r.id)
        assertEquals(ReportStatus.ERROR, s.status)
        assertTrue(s.lastMessage!!.startsWith("No se encontraron los adjuntos locales"))
        assertTrue(api.metadatas.isEmpty())
        assertTrue(recorder.contiene("${r.folio}: No se encontraron los adjuntos locales"))
    }

    @Test
    fun `un error HTTP programa el reintento automatico con backoff de 5 s`() {
        api.onUpload = {
            online.value = false // detiene el ciclo para revisar el estado intermedio
            errorHttp(503, """{"status":"ERROR","message":"Servicio no disponible"}""")
        }
        val r = reporteEnCola()
        procesar()
        val s = estado(r.id)
        assertEquals(ReportStatus.EN_COLA, s.status)
        assertEquals(1, s.autoRetryCount)
        assertTrue(s.nextRetryAt!! > System.currentTimeMillis() + 3_000)
        assertTrue(s.lastMessage!!.contains("HTTP 503 · Servicio no disponible"))
        assertTrue(recorder.contiene("${r.folio}: error al enviar. Reintento automático 1/3 en 5 s."))
    }

    @Test
    fun `agotados los 3 reintentos queda en error y el cuerpo invalido no rompe el flujo`() {
        api.onUpload = { errorHttp(500, "no es json") }
        val r = reporteEnCola(reintentosUsados = 3)
        procesar()
        val s = estado(r.id)
        assertEquals(ReportStatus.ERROR, s.status)
        assertTrue(s.lastMessage!!.contains("HTTP 500 · sin detalle"))
        assertTrue(s.lastMessage!!.contains("Se agotaron los 3 reintentos automáticos"))
        assertTrue(recorder.contiene("${r.folio}: pendiente de reintento manual."))
    }

    @Test
    fun `error de red con conexion activa cuenta como falla`() {
        api.onUpload = { throw IOException("timeout") }
        val r = reporteEnCola(reintentosUsados = 3)
        procesar()
        assertTrue(estado(r.id).lastMessage!!.contains("Error de red (IOException)"))
    }

    @Test
    fun `un error inesperado se informa sin cerrar la app`() {
        api.onUpload = { throw IllegalStateException("x") }
        val r = reporteEnCola(reintentosUsados = 3)
        procesar()
        assertEquals(ReportStatus.ERROR, estado(r.id).status)
        assertTrue(estado(r.id).lastMessage!!.contains("Error inesperado (IllegalStateException)"))
    }

    @Test
    fun `perder la conexion durante el envio deja el reporte en cola sin gastar reintentos`() {
        api.onUpload = {
            online.value = false
            throw OfflineException()
        }
        val r = reporteEnCola()
        procesar()
        val s = estado(r.id)
        assertEquals(ReportStatus.EN_COLA, s.status)
        assertEquals(0, s.autoRetryCount)
        assertNull(s.nextRetryAt)
        assertTrue(recorder.contiene("Sin conexión durante el envío de ${r.folio}: queda en cola."))

        // Un IOException cuando ya no hay red también se trata como "sin conexión"
        online.value = true
        api.onUpload = {
            online.value = false
            throw IOException("red caída")
        }
        procesar()
        assertEquals(ReportStatus.EN_COLA, estado(r.id).status)
        assertEquals(0, estado(r.id).autoRetryCount)
    }

    @Test
    fun `sin conexion no se intenta enviar`() {
        online.value = false
        val r = reporteEnCola()
        procesar()
        assertEquals(ReportStatus.EN_COLA, estado(r.id).status)
        assertTrue(api.metadatas.isEmpty())
    }

    @Test
    fun `espera el backoff pendiente antes de reintentar`() {
        val r = reporteEnCola()
        runBlocking { repo.markRetryScheduled(r.id, 1, 1, "Falló el envío: prueba", 1) }
        val inicio = System.currentTimeMillis()
        procesar()
        assertEquals(ReportStatus.SINCRONIZADO, estado(r.id).status)
        assertTrue("respetó la espera", System.currentTimeMillis() - inicio >= 700)
    }

    @Test
    fun `si se cancela durante el envio el reporte nunca queda colgado en enviando`() {
        val llamado = CompletableDeferred<Unit>()
        api.onUpload = {
            llamado.complete(Unit)
            awaitCancellation()
        }
        val r = reporteEnCola()
        val job = scope.launch { sync.processQueue(waitForLock = true) }
        runBlocking {
            withTimeout(10_000) { llamado.await() }
            job.cancel()
            job.join()
        }
        val s = estado(r.id)
        assertEquals(ReportStatus.EN_COLA, s.status)
        assertEquals("Sin conexión: se enviará automáticamente al recuperar la señal", s.lastMessage)
    }

    @Test
    fun `sincronizar ahora sin conexion avisa y programa el respaldo`() {
        online.value = false
        sync.requestSync(manual = true)
        assertTrue(recorder.contiene("Sin conexión: los reportes permanecen en cola y se enviarán automáticamente."))
        assertEquals(1, programaciones)
    }

    @Test
    fun `sincronizar ahora con conexion envia la cola en segundo plano`() {
        val a = reporteEnCola()
        val b = reporteEnCola()
        runBlocking { repo.markRetryScheduled(b.id, 1, 30, "Falló el envío: prueba", 1) }
        sync.requestSync(manual = true) // el envío manual anula la espera de 30 s
        esperar { estado(a.id).status == ReportStatus.SINCRONIZADO && estado(b.id).status == ReportStatus.SINCRONIZADO }
        assertEquals(1, programaciones)
        assertEquals(2, api.metadatas.size)
    }

    @Test
    fun `al iniciar recupera envios interrumpidos y reacciona a la conectividad`() {
        val r = reporteEnCola()
        runBlocking { repo.markSending(r.id, 1) }
        online.value = true
        sync.start()
        esperar { recorder.contiene("1 envío(s) interrumpido(s) se recuperaron y siguen en cola.") }
        esperar { estado(r.id).status == ReportStatus.SINCRONIZADO }
        online.value = false
        esperar { recorder.contiene("Sin conexión. Los reportes se guardan en el dispositivo y quedan en cola.") }
    }

    @Test
    fun `consulta del procesamiento simulado`() = runBlocking {
        val r = reporteEnCola()
        procesar()
        api.onProcessing = { id ->
            Response.success(ProcessingResponse(id, "PROCESADO", true, "Campos extraídos de ejemplo.", mapOf("proceso" to "Molienda")))
        }
        assertEquals("Campos extraídos de ejemplo.", sync.checkProcessing(r.id))
        val s = repo.get(r.id)!!
        assertEquals("PROCESADO", s.processingStatus)
        assertTrue(s.processingResultJson!!.contains("Molienda"))

        api.onProcessing = { id -> Response.success(ProcessingResponse(id, "EN_REVISION", true, null, null)) }
        assertEquals("Estado: EN_REVISION", sync.checkProcessing(r.id))

        api.onProcessing = { Response.error(404, "{}".toResponseBody("application/json".toMediaType())) }
        assertEquals("No fue posible consultar el procesamiento (HTTP 404).", sync.checkProcessing(r.id))

        api.onProcessing = { throw OfflineException() }
        assertEquals("Sin conexión: intenta nuevamente cuando haya señal.", sync.checkProcessing(r.id))

        api.onProcessing = { throw IllegalArgumentException() }
        assertEquals("Error al consultar (IllegalArgumentException).", sync.checkProcessing(r.id))
    }
}
