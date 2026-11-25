package cl.vitairos.captura.data.remote

import android.content.Context
import cl.vitairos.captura.data.prefs.AppSettings
import cl.vitairos.captura.data.prefs.MockApiMode
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.UUID
import kotlin.random.Random

/**
 * API SIMULADA de VITAIROS (cuestionario §7). Intercepta las llamadas de Retrofit y responde
 * localmente, sin salir del dispositivo. Permite demostrar:
 *  - éxito (201) con comprobante de recepción e id recibido,
 *  - errores HTTP (503 intermitente / 500 permanente) según Ajustes,
 *  - "sin conexión" (IOException) cuando no hay red real o se activa la simulación offline,
 *  - idempotencia: un reportId ya recibido no se duplica,
 *  - estado de procesamiento simulado (En revisión → Procesado) con campos extraídos de ejemplo.
 */
class MockApiInterceptor(
    private val settings: () -> AppSettings,
    private val networkAvailable: () -> Boolean,
    private val server: MockServerState,
    private val gson: Gson,
    /** Latencia simulada (ms). En las pruebas unitarias se usa 0. */
    private val latencyMs: () -> Long = { Random.nextLong(700, 1600) },
    /** Fuente de azar del modo intermitente (en las pruebas se usa una semilla fija). */
    private val random: Random = Random.Default,
) : Interceptor {

    private val json = "application/json; charset=utf-8".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Latencia de red simulada
        latencyMs().takeIf { it > 0 }?.let { Thread.sleep(it) }

        val s = settings()
        if (!s.loaded || !networkAvailable() || s.simulateOffline) {
            throw OfflineException()
        }

        val path = request.url.encodedPath
        return when {
            request.method == "POST" && path == "/api/v1/reports" -> handleUpload(request, s)
            request.method == "GET" && path.startsWith("/api/v1/reports/") && path.endsWith("/processing") ->
                handleProcessing(request, path.removePrefix("/api/v1/reports/").removeSuffix("/processing"))
            else -> respond(request, 404, ErrorResponse("NOT_FOUND", "Recurso no encontrado en la API mock"))
        }
    }

    private fun handleUpload(request: Request, s: AppSettings): Response {
        when (s.mockApiMode) {
            MockApiMode.SIEMPRE_FALLA ->
                return respond(request, 500, ErrorResponse("ERROR", "Error interno del servidor (simulado)"))
            MockApiMode.INTERMITENTE -> if (random.nextBoolean()) {
                return respond(request, 503, ErrorResponse("ERROR", "Servicio no disponible temporalmente (simulado)"))
            }
            MockApiMode.NORMAL -> Unit
        }

        val body = request.body as? MultipartBody
            ?: return respond(request, 400, ErrorResponse("ERROR", "Se esperaba multipart/form-data"))

        var metadata: ReportMetadataDto? = null
        var fileCount = 0
        body.parts.forEach { part ->
            val disposition = part.headers?.get("Content-Disposition").orEmpty()
            when {
                disposition.contains("name=\"metadata\"") -> {
                    val buffer = Buffer()
                    part.body.writeTo(buffer)
                    metadata = runCatching { gson.fromJson(buffer.readUtf8(), ReportMetadataDto::class.java) }.getOrNull()
                }
                disposition.contains("filename=") -> fileCount++
            }
        }

        val meta = metadata
            ?: return respond(request, 422, ErrorResponse("ERROR", "Metadata inválida o ausente"))
        if (fileCount == 0) {
            return respond(request, 422, ErrorResponse("ERROR", "El reporte debe incluir al menos un adjunto"))
        }

        server.receivedReceipt(meta.reportId)?.let { receipt ->
            return respond(
                request, 200,
                UploadResponse(receipt, meta.reportId, "RECIBIDO", "Reporte ya recibido anteriormente (sin duplicar).", true, "EN_REVISION"),
            )
        }

        val receipt = "VIA-" + UUID.randomUUID().toString().take(8).uppercase()
        server.register(meta.reportId, receipt, meta.reportType, meta.process)
        return respond(
            request, 201,
            UploadResponse(receipt, meta.reportId, "RECIBIDO", "Reporte recibido correctamente ($fileCount adjunto(s)).", false, "EN_REVISION"),
        )
    }

    private fun handleProcessing(request: Request, reportId: String): Response {
        val entry = server.entry(reportId)
            ?: return respond(request, 404, ErrorResponse("NOT_FOUND", "Reporte no recibido por la API mock"))
        val elapsed = System.currentTimeMillis() - entry.receivedAt
        return if (elapsed < 20_000) {
            respond(
                request, 200,
                ProcessingResponse(reportId, "EN_REVISION", true, "En cola de procesamiento (simulación).", null),
            )
        } else {
            respond(
                request, 200,
                ProcessingResponse(
                    reportId, "PROCESADO", true, "Campos extraídos de ejemplo (simulación, no es IA real).",
                    linkedMapOf(
                        "tipo_documento" to entry.type,
                        "proceso" to entry.process,
                        "fecha_documento" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(entry.receivedAt)),
                        "firma_detectada" to "sí (ejemplo)",
                        "confianza_promedio" to "0.87 (ejemplo)",
                        "revision_humana" to "pendiente",
                    ),
                ),
            )
        }
    }

    private fun respond(request: Request, code: Int, payload: Any): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(gson.toJson(payload).toResponseBody(json))
            .build()
}

/** Señala pérdida de conectividad (real o simulada). */
class OfflineException : IOException("Sin conexión a Internet")

/** "Servidor" mock persistido en SharedPreferences privadas: recuerda los reportId recibidos. */
class MockServerState(context: Context) {
    data class Entry(val receipt: String, val receivedAt: Long, val type: String, val process: String)

    private val prefs = context.getSharedPreferences("mock_server_state", Context.MODE_PRIVATE)

    @Synchronized
    fun receivedReceipt(reportId: String): String? = entry(reportId)?.receipt

    @Synchronized
    fun register(reportId: String, receipt: String, type: String, process: String) {
        prefs.edit().putString(reportId, listOf(receipt, System.currentTimeMillis(), type, process).joinToString("|")).apply()
    }

    @Synchronized
    fun entry(reportId: String): Entry? {
        val raw = prefs.getString(reportId, null) ?: return null
        val p = raw.split("|")
        if (p.size < 4) return null
        return Entry(p[0], p[1].toLongOrNull() ?: 0L, p[2], p[3])
    }
}
