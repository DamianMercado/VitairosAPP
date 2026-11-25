package cl.vitairos.captura.sync

import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.local.ReportEntity
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.remote.ErrorResponse
import cl.vitairos.captura.data.remote.OfflineException
import cl.vitairos.captura.data.remote.ReportMetadataDto
import cl.vitairos.captura.data.remote.AttachmentMetaDto
import cl.vitairos.captura.data.remote.VitairosApi
import cl.vitairos.captura.data.repo.ReportRepository
import cl.vitairos.captura.domain.RetryPolicy
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Motor de sincronización offline-first.
 *
 *  - Procesa la cola local (estado EN_COLA) de a un reporte, en orden de creación.
 *  - Un intento inicial + 3 reintentos automáticos con backoff 5 s / 15 s / 30 s.
 *    Agotados → estado ERROR con acción visible "Reintentar" (manual).
 *  - Sin conexión: no consume reintentos; el reporte espera en cola y se envía al volver la señal.
 *  - Tras cerrar la app / reiniciar: los "ENVIANDO" interrumpidos vuelven a EN_COLA y se reanudan.
 *  - WorkManager actúa como respaldo para sincronizar cuando haya red aunque la app esté cerrada.
 */
class SyncManager(
    private val repo: ReportRepository,
    private val api: VitairosApi,
    private val gson: Gson,
    private val online: StateFlow<Boolean>,
    private val events: AppEvents,
    private val scope: CoroutineScope,
    private val scheduleBackground: () -> Unit,
) {
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var pendingRequest = false

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private sealed interface Outcome {
        data class Success(val receipt: String?, val message: String, val duplicate: Boolean, val processing: String?) : Outcome
        data class Failure(val reason: String) : Outcome
        data object Offline : Outcome
    }

    fun start() {
        scope.launch {
            val recovered = repo.recoverInterrupted()
            if (recovered > 0) events.info("$recovered envío(s) interrumpido(s) se recuperaron y siguen en cola.")
            var previous: Boolean? = null
            var offlineSince = System.currentTimeMillis()
            online.collect { isOnline ->
                // Se ignora el "falso offline" inicial mientras cargan las preferencias (< 3 s).
                if (previous == false && isOnline && System.currentTimeMillis() - offlineSince > 3_000) {
                    val n = repo.queuedCount()
                    if (n > 0) events.info("Conexión recuperada: sincronizando $n reporte(s)…")
                }
                if (previous == true && !isOnline) {
                    events.warning("Sin conexión. Los reportes se guardan en el dispositivo y quedan en cola.")
                }
                if (!isOnline) offlineSince = System.currentTimeMillis()
                previous = isOnline
                if (isOnline) requestSync()
            }
        }
    }

    /** Solicita procesar la cola (no bloquea). @param manual true si lo pidió el usuario. */
    fun requestSync(manual: Boolean = false) {
        if (manual && !online.value) {
            events.warning("Sin conexión: los reportes permanecen en cola y se enviarán automáticamente.")
        }
        pendingRequest = true
        wake.trySend(Unit)
        scope.launch {
            if (manual) repo.clearBackoff()
            processQueue(waitForLock = false)
        }
        scheduleBackground()
    }

    /**
     * Procesa la cola mientras haya conexión y reportes pendientes.
     * @param waitForLock true desde WorkManager: espera a que termine una sincronización en curso.
     */
    suspend fun processQueue(waitForLock: Boolean) {
        if (waitForLock) mutex.lock() else if (!mutex.tryLock()) return
        try {
            _syncing.value = true
            runLoop()
        } finally {
            _syncing.value = false
            mutex.unlock()
        }
        if (pendingRequest && online.value && repo.queuedCount() > 0) {
            scope.launch { processQueue(waitForLock = false) }
        }
    }

    private suspend fun runLoop() {
        while (true) {
            pendingRequest = false
            if (!online.value) return
            val queued = repo.queued()
            if (queued.isEmpty()) return
            val now = System.currentTimeMillis()
            val due = queued.firstOrNull { (it.nextRetryAt ?: 0L) <= now }
            if (due == null) {
                // Todos esperan backoff: dormir hasta el más próximo o hasta una nueva solicitud.
                val next = queued.mapNotNull { it.nextRetryAt }.minOrNull() ?: now
                withTimeoutOrNull((next - now).coerceIn(200L, 60_000L)) { wake.receive() }
                continue
            }
            sendOne(due)
        }
    }

    private suspend fun sendOne(report: ReportEntity) {
        val attempt = report.attemptCount + 1
        if (!repo.markSending(report.id, attempt)) return // fue editado o eliminado entretanto

        // Garantía: pase lo que pase (cancelación, error), el reporte nunca queda "colgado" en ENVIANDO.
        var settled = false
        try {
            val atts = repo.attachmentsFor(report.id)
            val missing = atts.isEmpty() || atts.any { it.localPath == null || !File(it.localPath).exists() }
            if (missing) {
                val reason = "No se encontraron los adjuntos locales. Edita el reporte y adjunta nuevamente."
                withContext(NonCancellable) { repo.markError(report.id, reason, attempt, report.autoRetryCount) }
                settled = true
                events.error("${report.folio}: $reason")
                return
            }

            val outcome: Outcome = try {
                upload(report, atts, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                Outcome.Offline
            } catch (e: IOException) {
                if (online.value) Outcome.Failure("Error de red (${e.javaClass.simpleName})") else Outcome.Offline
            } catch (e: Exception) {
                Outcome.Failure("Error inesperado (${e.javaClass.simpleName})")
            }

            withContext(NonCancellable) { applyOutcome(report, attempt, outcome) }
            settled = true
        } finally {
            if (!settled) {
                withContext(NonCancellable) { repo.markOfflineRequeue(report.id, report.autoRetryCount, attempt) }
            }
        }
    }

    private suspend fun applyOutcome(report: ReportEntity, attempt: Int, outcome: Outcome) {
        when (outcome) {
            is Outcome.Success -> {
                repo.markSynced(report.id, outcome.receipt, outcome.message, outcome.processing, attempt)
                if (outcome.duplicate) {
                    events.info("${report.folio} ya estaba recibido por VITAIROS: no se duplicó.")
                } else {
                    events.success("${report.folio} enviado correctamente · Comprobante ${outcome.receipt ?: "—"}")
                }
            }
            is Outcome.Offline -> {
                repo.markOfflineRequeue(report.id, report.autoRetryCount, attempt)
                events.warning("Sin conexión durante el envío de ${report.folio}: queda en cola.")
            }
            is Outcome.Failure -> {
                val delay = RetryPolicy.nextDelaySeconds(report.autoRetryCount)
                if (delay != null) {
                    val used = report.autoRetryCount + 1
                    repo.markRetryScheduled(report.id, used, delay, "Falló el envío: ${outcome.reason}", attempt)
                    events.warning("${report.folio}: error al enviar. Reintento automático $used/${RetryPolicy.maxAutoRetries} en $delay s.")
                } else {
                    repo.markError(
                        report.id,
                        "Falló el envío: ${outcome.reason}. Se agotaron los ${RetryPolicy.maxAutoRetries} reintentos automáticos; usa Reintentar.",
                        attempt,
                        report.autoRetryCount,
                    )
                    events.error("${report.folio}: pendiente de reintento manual.")
                }
            }
        }
    }

    private suspend fun upload(report: ReportEntity, atts: List<AttachmentEntity>, attempt: Int): Outcome {
        val dto = ReportMetadataDto(
            reportId = report.id,
            folio = report.folio,
            reportType = Catalog.typeName(report.reportTypeCode),
            reportTypeVersion = report.reportTypeVersion,
            process = Catalog.processName(report.processCode),
            area = report.area,
            shift = report.shift,
            extraField = report.extraField,
            observations = report.observations,
            userId = report.createdBy,
            userName = report.createdByName,
            userRole = report.createdByRole,
            deviceInstallId = report.deviceInstallId,
            createdAt = isoFormat.format(Date(report.createdAt)),
            attempt = attempt,
            version = report.version,
            parentReportId = report.parentReportId,
            attachments = atts.map { AttachmentMetaDto(it.id, it.displayName, it.mimeType, it.storedSizeBytes, it.sha256) },
        )
        val metadata = gson.toJson(dto).toRequestBody("application/json; charset=utf-8".toMediaType())
        val parts = atts.map { a ->
            MultipartBody.Part.createFormData(
                "files",
                safeFileName(a.displayName),
                File(a.localPath!!).asRequestBody(a.mimeType.toMediaType()),
            )
        }
        val response = api.uploadReport(metadata, parts)
        return if (response.isSuccessful) {
            val body = response.body()
            Outcome.Success(
                receipt = body?.receiptId,
                message = body?.message ?: "Reporte recibido.",
                duplicate = body?.duplicate == true,
                processing = body?.processingStatus,
            )
        } else {
            val error = response.errorBody()?.string()?.let {
                runCatching { gson.fromJson(it, ErrorResponse::class.java) }.getOrNull()
            }
            Outcome.Failure("HTTP ${response.code()} · ${error?.message ?: "sin detalle"}")
        }
    }

    /** Consulta el estado de procesamiento SIMULADO de un reporte sincronizado. */
    suspend fun checkProcessing(reportId: String): String = try {
        val response = api.processing(reportId)
        val body = response.body()
        val status = body?.status
        if (response.isSuccessful && body != null && status != null) {
            repo.updateProcessing(reportId, status, body.extractedFields?.let { gson.toJson(it) })
            body.message ?: "Estado: $status"
        } else {
            "No fue posible consultar el procesamiento (HTTP ${response.code()})."
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        "Sin conexión: intenta nuevamente cuando haya señal."
    } catch (e: Exception) {
        "Error al consultar (${e.javaClass.simpleName})."
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "adjunto" }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
}
