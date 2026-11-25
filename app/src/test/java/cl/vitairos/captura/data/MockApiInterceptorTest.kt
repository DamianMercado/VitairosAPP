package cl.vitairos.captura.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.vitairos.captura.data.prefs.AppSettings
import cl.vitairos.captura.data.prefs.MockApiMode
import cl.vitairos.captura.data.remote.AttachmentMetaDto
import cl.vitairos.captura.data.remote.ErrorResponse
import cl.vitairos.captura.data.remote.MockApiInterceptor
import cl.vitairos.captura.data.remote.MockServerState
import cl.vitairos.captura.data.remote.OfflineException
import cl.vitairos.captura.data.remote.ReportMetadataDto
import cl.vitairos.captura.data.remote.VitairosApi
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.random.Random

/** API simulada: éxito, errores HTTP según Ajustes, sin conexión, idempotencia y procesamiento. */
@RunWith(AndroidJUnit4::class)
class MockApiInterceptorTest {

    /** Azar controlado: nextBoolean() siempre devuelve [valor]. */
    private class AzarFijo(private val valor: Boolean) : Random() {
        override fun nextBits(bitCount: Int): Int = if (valor) 1 else 0
    }

    private val gson = Gson()
    private lateinit var context: Context
    private lateinit var server: MockServerState
    private var settings = AppSettings(loaded = true)
    private var red = true
    private var azar: Random = AzarFijo(false)
    private lateinit var client: OkHttpClient
    private lateinit var api: VitairosApi

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockServerState(context)
        client = OkHttpClient.Builder()
            .addInterceptor(
                MockApiInterceptor(
                    settings = { settings },
                    networkAvailable = { red },
                    server = server,
                    gson = gson,
                    latencyMs = { 0L },
                    random = object : Random() {
                        override fun nextBits(bitCount: Int): Int = azar.nextBits(bitCount)
                    },
                )
            )
            .build()
        api = Retrofit.Builder()
            .baseUrl("https://api-mock.vitairos.local/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(VitairosApi::class.java)
    }

    private fun metadata(reportId: String = "R-1") = gson.toJson(
        ReportMetadataDto(
            reportId = reportId, folio = "VTR-261124-ABCD", reportType = "Reporte de turno", reportTypeVersion = "v1.2",
            process = "Chancado", area = "Rajo Norte", shift = "Turno A", extraField = null, observations = "ok",
            userId = "u-op-01", userName = "Camila Rojas", userRole = "Operador", deviceInstallId = "INST-TEST",
            createdAt = "2026-11-24T10:00:00-03:00", attempt = 1, version = 1, parentReportId = null,
            attachments = listOf(AttachmentMetaDto("A-1", "foto.jpg", "image/jpeg", 3, "abc")),
        )
    ).toRequestBody("application/json".toMediaType())

    private fun archivo() = MultipartBody.Part.createFormData(
        "files", "foto.jpg", byteArrayOf(1, 2, 3).toRequestBody("image/jpeg".toMediaType()),
    )

    private fun subir(reportId: String = "R-1", archivos: List<MultipartBody.Part> = listOf(archivo())) =
        runBlocking { api.uploadReport(metadata(reportId), archivos) }

    private fun mensajeDeError(body: String?) = gson.fromJson(body, ErrorResponse::class.java).message

    @Test
    fun `envio correcto entrega comprobante y la segunda vez no duplica`() {
        val r1 = subir()
        assertEquals(201, r1.code())
        val b1 = r1.body()!!
        assertTrue(b1.receiptId!!.matches(Regex("VIA-[0-9A-F]{8}")))
        assertEquals("R-1", b1.reportId)
        assertEquals("RECIBIDO", b1.status)
        assertEquals(false, b1.duplicate)
        assertEquals("EN_REVISION", b1.processingStatus)
        assertEquals("Reporte recibido correctamente (1 adjunto(s)).", b1.message)

        val r2 = subir()
        assertEquals(200, r2.code())
        assertEquals(true, r2.body()!!.duplicate)
        assertEquals(b1.receiptId, r2.body()!!.receiptId)

        val entry = server.entry("R-1")!!
        assertEquals("Reporte de turno", entry.type)
        assertEquals("Chancado", entry.process)
        assertEquals(b1.receiptId, server.receivedReceipt("R-1"))
    }

    @Test
    fun `sin red, sin preferencias cargadas o modo offline lanza OfflineException`() {
        fun esperaOffline() {
            try {
                subir()
                fail("se esperaba OfflineException")
            } catch (e: OfflineException) {
                assertEquals("Sin conexión a Internet", e.message)
            }
        }
        red = false
        esperaOffline()
        red = true
        settings = AppSettings(loaded = false)
        esperaOffline()
        settings = AppSettings(loaded = true, simulateOffline = true)
        esperaOffline()
    }

    @Test
    fun `modo siempre falla responde 500 y el intermitente alterna 503 y exito`() {
        settings = AppSettings(loaded = true, mockApiMode = MockApiMode.SIEMPRE_FALLA)
        val r500 = subir()
        assertEquals(500, r500.code())
        assertEquals("Error interno del servidor (simulado)", mensajeDeError(r500.errorBody()?.string()))

        settings = AppSettings(loaded = true, mockApiMode = MockApiMode.INTERMITENTE)
        azar = AzarFijo(true)
        val r503 = subir()
        assertEquals(503, r503.code())
        assertEquals("Servicio no disponible temporalmente (simulado)", mensajeDeError(r503.errorBody()?.string()))
        azar = AzarFijo(false)
        assertEquals(201, subir("R-2").code())
    }

    @Test
    fun `se rechaza un envio sin adjuntos o con metadata invalida`() {
        val sinAdjuntos = subir(archivos = emptyList())
        assertEquals(422, sinAdjuntos.code())
        assertEquals("El reporte debe incluir al menos un adjunto", mensajeDeError(sinAdjuntos.errorBody()?.string()))

        val cuerpo = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("metadata", "{esto no es json")
            .addPart(archivo())
            .build()
        val malo = client.newCall(
            Request.Builder().url("https://api-mock.vitairos.local/api/v1/reports").post(cuerpo).build()
        ).execute()
        assertEquals(422, malo.code)
        assertEquals("Metadata inválida o ausente", mensajeDeError(malo.body?.string()))
    }

    @Test
    fun `un POST que no es multipart responde 400 y una ruta desconocida 404`() {
        val noMultipart = client.newCall(
            Request.Builder().url("https://api-mock.vitairos.local/api/v1/reports")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertEquals(400, noMultipart.code)
        assertEquals("Error", noMultipart.message)

        val desconocida = client.newCall(Request.Builder().url("https://api-mock.vitairos.local/otra").build()).execute()
        assertEquals(404, desconocida.code)
        assertEquals("Recurso no encontrado en la API mock", mensajeDeError(desconocida.body?.string()))
    }

    @Test
    fun `procesamiento simulado pasa de en revision a procesado`() = runBlocking {
        assertEquals(404, api.processing("no-recibido").code())

        subir("R-NUEVO")
        val enRevision = api.processing("R-NUEVO").body()!!
        assertEquals("EN_REVISION", enRevision.status)
        assertEquals(true, enRevision.simulated)
        assertNull(enRevision.extractedFields)

        // Un reporte recibido hace más de 20 s ya aparece procesado
        context.getSharedPreferences("mock_server_state", Context.MODE_PRIVATE).edit()
            .putString("R-ANTIGUO", "VIA-OLD00001|0|Orden de mantención|Molienda").commit()
        val procesado = api.processing("R-ANTIGUO").body()!!
        assertEquals("PROCESADO", procesado.status)
        val campos = procesado.extractedFields!!
        assertEquals("Orden de mantención", campos["tipo_documento"])
        assertEquals("Molienda", campos["proceso"])
        assertEquals("pendiente", campos["revision_humana"])
        assertNotNull(campos["fecha_documento"])
    }

    @Test
    fun `estado del servidor ignora registros corruptos`() {
        context.getSharedPreferences("mock_server_state", Context.MODE_PRIVATE).edit()
            .putString("R-MALO", "solo|dos").putString("R-FECHA", "VIA-1|abc|Tipo|Proceso").commit()
        assertNull(server.entry("R-MALO"))
        assertNull(server.receivedReceipt("R-MALO"))
        assertEquals(0L, server.entry("R-FECHA")!!.receivedAt)
        assertFalse(server.receivedReceipt("R-FECHA").isNullOrBlank())
    }
}
