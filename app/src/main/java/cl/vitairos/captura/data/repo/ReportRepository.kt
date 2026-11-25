package cl.vitairos.captura.data.repo

import cl.vitairos.captura.data.files.AttachmentStorage
import cl.vitairos.captura.data.files.SyntheticDocs
import cl.vitairos.captura.data.local.AppDatabase
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.local.ReportEntity
import cl.vitairos.captura.data.local.ReportEventEntity
import cl.vitairos.captura.data.local.ReportWithAttachments
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.prefs.SettingsStore
import cl.vitairos.captura.domain.RetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Campos que el usuario puede editar antes de sincronizar. */
data class EditableFields(
    val typeCode: String?,
    val processCode: String?,
    val area: String?,
    val shift: String?,
    val extra: String?,
    val observations: String,
    val qualityConfirmed: Boolean,
)

/**
 * Única puerta de acceso a los datos locales. Toda transición de estado queda registrada
 * en la bitácora (report_events) para reconstruir el recorrido del reporte (trazabilidad).
 */
class ReportRepository(
    db: AppDatabase,
    private val storage: AttachmentStorage,
    private val settings: SettingsStore,
) {
    private val reports = db.reportDao()
    private val attachments = db.attachmentDao()
    private val events = db.eventDao()

    private fun now() = System.currentTimeMillis()

    // ------------------------------------------------------------------ lecturas
    fun observeAll(): Flow<List<ReportWithAttachments>> = reports.observeAll()
    fun observe(id: String): Flow<ReportWithAttachments?> = reports.observe(id)
    fun observeEvents(id: String): Flow<List<ReportEventEntity>> = events.observeForReport(id)
    fun observeAttachments(id: String): Flow<List<AttachmentEntity>> = attachments.observeForReport(id)
    suspend fun get(id: String): ReportEntity? = reports.get(id)
    suspend fun attachmentsFor(id: String): List<AttachmentEntity> = attachments.forReport(id)
    suspend fun queued(): List<ReportEntity> = reports.getQueued()
    suspend fun queuedCount(): Int = reports.countQueued()

    private suspend fun log(reportId: String, status: ReportStatus, message: String, attempt: Int? = null) {
        events.insert(ReportEventEntity(reportId = reportId, timestamp = now(), status = status, message = message, attempt = attempt))
    }

    // ------------------------------------------------------------------ creación / edición
    suspend fun createDraft(user: DemoUser, fields: EditableFields, parent: ReportEntity? = null): ReportEntity {
        val now = now()
        val type = Catalog.type(fields.typeCode)
        val entity = ReportEntity(
            id = UUID.randomUUID().toString(),
            folio = newFolio(now),
            reportTypeCode = fields.typeCode.orEmpty(),
            reportTypeVersion = type?.version.orEmpty(),
            processCode = fields.processCode.orEmpty(),
            area = fields.area,
            shift = fields.shift,
            extraField = fields.extra,
            observations = fields.observations,
            createdBy = user.id,
            createdByName = user.displayName,
            createdByRole = user.role.label,
            deviceInstallId = settings.installId(),
            createdAt = now,
            updatedAt = now,
            status = ReportStatus.BORRADOR,
            qualityConfirmed = fields.qualityConfirmed,
            lastMessage = "Borrador guardado en el dispositivo",
            version = (parent?.version ?: 0) + 1,
            parentReportId = parent?.id,
        )
        reports.upsert(entity)
        if (parent == null) {
            log(entity.id, ReportStatus.BORRADOR, "Borrador creado por ${user.displayName}")
        } else {
            log(entity.id, ReportStatus.BORRADOR, "Versión ${entity.version} creada como corrección de ${parent.folio}")
            log(parent.id, parent.status, "Se generó la nueva versión ${entity.folio} (v${entity.version}); este registro se conserva")
        }
        return entity
    }

    /** Guarda cambios solo si el estado aún lo permite. @return false si el reporte ya no es editable. */
    suspend fun saveFields(id: String, f: EditableFields): Boolean =
        reports.updateEditableFields(
            id = id,
            typeCode = f.typeCode.orEmpty(),
            typeVersion = Catalog.type(f.typeCode)?.version.orEmpty(),
            processCode = f.processCode.orEmpty(),
            area = f.area,
            shift = f.shift,
            extra = f.extra?.takeIf { it.isNotBlank() },
            observations = f.observations,
            qualityConfirmed = f.qualityConfirmed,
            now = now(),
        ) > 0

    /** Si estaba En cola, lo retira (→ Borrador) para que la sincronización no lo tome mientras se edita. */
    suspend fun takeForEditing(id: String): Boolean {
        val moved = reports.transition(id, listOf(ReportStatus.EN_COLA.name), ReportStatus.BORRADOR.name,
            "En edición (retirado temporalmente de la cola)", now())
        if (moved > 0) {
            log(id, ReportStatus.BORRADOR, "Retirado de la cola para edición")
            return true
        }
        return reports.get(id)?.status?.isEditable == true
    }

    suspend fun addAttachment(attachment: AttachmentEntity) {
        attachments.upsert(attachment)
        val status = reports.get(attachment.reportId)?.status ?: ReportStatus.BORRADOR
        log(attachment.reportId, status, "Adjunto agregado: ${attachment.displayName} (${attachment.source.label})")
    }

    suspend fun updateAttachment(attachment: AttachmentEntity) = attachments.upsert(attachment)

    suspend fun removeAttachment(attachment: AttachmentEntity) {
        attachments.delete(attachment.id)
        storage.deleteFile(attachment.localPath)
        val status = reports.get(attachment.reportId)?.status ?: ReportStatus.BORRADOR
        log(attachment.reportId, status, "Adjunto eliminado: ${attachment.displayName}")
    }

    /** Folios de otros reportes que ya contienen exactamente el mismo archivo. */
    suspend fun duplicateFolios(sha256: String, reportId: String): List<String> =
        attachments.reportsWithHash(sha256, reportId).distinct().mapNotNull { reports.get(it)?.folio }

    suspend fun enqueue(id: String, manualRetry: Boolean = false): Boolean {
        val msg = if (manualRetry) "Reintento manual solicitado" else "Guardado localmente y agregado a la cola de envío"
        val ok = reports.enqueue(id, msg, now()) > 0
        if (ok) log(id, ReportStatus.EN_COLA, msg)
        return ok
    }

    suspend fun delete(id: String): Boolean {
        val ok = reports.deleteIfDeletable(id) > 0
        if (ok) storage.deleteReportFiles(id)
        return ok
    }

    /** Corrección posterior a la sincronización: nueva versión, sin borrar el registro anterior. */
    suspend fun createNewVersion(originalId: String, user: DemoUser): ReportEntity? {
        val original = reports.get(originalId) ?: return null
        if (original.status != ReportStatus.REQUIERE_CORRECCION && original.status != ReportStatus.SINCRONIZADO) return null
        return createDraft(
            user = user,
            fields = EditableFields(
                typeCode = original.reportTypeCode,
                processCode = original.processCode,
                area = original.area,
                shift = original.shift,
                extra = original.extraField,
                observations = original.observations,
                qualityConfirmed = false,
            ),
            parent = original,
        )
    }

    // ------------------------------------------------------------------ supervisor
    suspend fun markNeedsCorrection(id: String, note: String, supervisor: DemoUser): Boolean {
        val msg = "Observado por ${supervisor.displayName}: $note"
        val ok = reports.markNeedsCorrection(id, note, msg, now()) > 0
        if (ok) log(id, ReportStatus.REQUIERE_CORRECCION, msg)
        return ok
    }

    suspend fun updateProcessing(id: String, status: String, json: String?) {
        val current = reports.get(id) ?: return
        if (current.processingStatus != status || current.processingResultJson != json) {
            reports.updateProcessing(id, status, json, now())
            log(id, current.status, "Procesamiento VITAIROS (simulación): $status")
        }
    }

    // ------------------------------------------------------------------ sincronización
    suspend fun recoverInterrupted(): Int {
        val ids = reports.sendingIds()
        if (ids.isEmpty()) return 0
        val msg = "Envío interrumpido (app cerrada o reiniciada); vuelve a la cola"
        reports.recoverInterrupted(msg, now())
        ids.forEach { log(it, ReportStatus.EN_COLA, msg) }
        return ids.size
    }

    suspend fun clearBackoff() = reports.clearBackoff()

    suspend fun markSending(id: String, attempt: Int): Boolean {
        val ok = reports.markSending(id, "Enviando (intento #$attempt)…", now()) > 0
        if (ok) log(id, ReportStatus.ENVIANDO, "Intento de envío #$attempt", attempt)
        return ok
    }

    /**
     * Marca el reporte como sincronizado. Los archivos adjuntos SE CONSERVAN como copia local
     * (almacenamiento privado de la app, sin respaldo en la nube) para consultar el historial y la
     * evidencia sin conexión. Como un reporte sincronizado no se puede eliminar, la copia vive
     * mientras la app esté instalada.
     */
    suspend fun markSynced(id: String, receipt: String?, message: String, processing: String?, attempt: Int) {
        reports.markSynced(id, receipt, message, processing, now())
        log(id, ReportStatus.SINCRONIZADO, "$message Comprobante: ${receipt ?: "—"}. Copia local de los adjuntos conservada en el dispositivo.", attempt)
    }

    suspend fun markRetryScheduled(id: String, retriesUsed: Int, delaySeconds: Int, reason: String, attempt: Int) {
        val msg = "$reason · Reintento automático $retriesUsed/${RetryPolicy.maxAutoRetries} en $delaySeconds s"
        reports.markFailed(id, ReportStatus.EN_COLA.name, retriesUsed, now() + delaySeconds * 1000L, msg, now())
        log(id, ReportStatus.EN_COLA, msg, attempt)
    }

    suspend fun markError(id: String, reason: String, attempt: Int, retriesUsed: Int) {
        reports.markFailed(id, ReportStatus.ERROR.name, retriesUsed, null, reason, now())
        log(id, ReportStatus.ERROR, reason, attempt)
    }

    suspend fun markOfflineRequeue(id: String, retriesUsed: Int, attempt: Int?) {
        val msg = "Sin conexión: se enviará automáticamente al recuperar la señal"
        reports.markFailed(id, ReportStatus.EN_COLA.name, retriesUsed, null, msg, now())
        log(id, ReportStatus.EN_COLA, msg, attempt)
    }

    // ------------------------------------------------------------------ datos de prueba
    /** Crea [count] reportes sintéticos completos y los deja En cola (prueba de ≥10 pendientes). */
    suspend fun createSyntheticQueued(user: DemoUser, count: Int): Int {
        repeat(count) { i ->
            val type = Catalog.reportTypes[i % Catalog.reportTypes.size]
            val process = Catalog.processes[(i * 3) % Catalog.processes.size]
            val area = Catalog.areas[i % Catalog.areas.size]
            val shift = Catalog.shifts[i % Catalog.shifts.size]
            val extra = type.extraField?.let { ef ->
                val opts = ef.options
                if (opts != null) opts[i % opts.size] else "EQ-${100 + i}"
            }
            val fields = EditableFields(type.code, process.code, area, shift, extra,
                "Documento sintético de prueba #${i + 1}", qualityConfirmed = true)
            val draft = createDraft(user, fields)
            val bitmap = withContext(Dispatchers.Default) {
                SyntheticDocs.render(draft.folio, type.name, process.name, area, shift, user.displayName)
            }
            val att = storage.saveSynthetic(draft.id, bitmap, "sintetico_${draft.folio}.jpg")
            bitmap.recycle()
            addAttachment(att)
            enqueue(draft.id)
        }
        return count
    }

    companion object {
        fun newFolio(now: Long): String =
            "VTR-" + SimpleDateFormat("yyMMdd", Locale.US).format(Date(now)) + "-" +
                UUID.randomUUID().toString().take(4).uppercase(Locale.US)
    }
}
