package cl.vitairos.captura.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.data.files.ImportResult
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.repo.EditableFields
import cl.vitairos.captura.domain.ReportForm
import cl.vitairos.captura.domain.ReportValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorUi(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val folio: String = "Se asigna al guardar",
    val createdAt: Long = System.currentTimeMillis(),
    val userName: String = "",
    val userRole: String = "",
    val deviceId: String = "",
    val version: Int = 1,
    val status: ReportStatus = ReportStatus.BORRADOR,
    val wasQueued: Boolean = false,
    val step: Int = 0,
    val typeCode: String? = null,
    val processCode: String? = null,
    val area: String? = null,
    val shift: String? = null,
    val extra: String = "",
    val observations: String = "",
    val attachments: List<AttachmentEntity> = emptyList(),
    val qualityConfirmed: Boolean = false,
    /** Adjuntos que el usuario ya abrió en la vista previa (revisión obligatoria). */
    val viewedIds: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val attachmentError: String? = null,
    val qualityPrompt: AttachmentEntity? = null,
    val closeRequested: Boolean = false,
    val doneReportId: String? = null,
) {
    /** true cuando TODOS los adjuntos fueron abiertos al menos una vez. */
    val allViewed: Boolean
        get() = attachments.isNotEmpty() && attachments.all { it.id in viewedIds }

    val viewedCount: Int
        get() = attachments.count { it.id in viewedIds }

    /** La confirmación solo vale si además se revisaron todos los adjuntos. */
    val effectiveConfirmed: Boolean
        get() = qualityConfirmed && allViewed

    val form: ReportForm
        get() = ReportForm(typeCode, processCode, area, shift, extra, observations, attachments.size, effectiveConfirmed)

    val fields: EditableFields
        get() = EditableFields(typeCode, processCode, area, shift, extra.takeIf { it.isNotBlank() }, observations.trim(), effectiveConfirmed)

    val hasInput: Boolean
        get() = typeCode != null || processCode != null || area != null || shift != null || extra.isNotBlank() ||
            observations.isNotBlank() || attachments.isNotEmpty()
}

/**
 * Flujo: Contexto (tipo, proceso, metadata) → Evidencia (captura/adjuntos + vista previa)
 * → Revisión (confirmación) → Guardar local + cola. Guarda automáticamente como borrador.
 */
class EditorViewModel(
    private val container: AppContainer,
    private val user: DemoUser,
    handle: SavedStateHandle,
) : ViewModel() {

    private val repo = container.repository
    private val editId: String? = handle.get<String>("reportId")
    private var reportId: String? = editId
    private val persistMutex = Mutex()

    private var cameraUri: Uri? = null
    private var replaceTargetId: String? = null

    private val _ui = MutableStateFlow(EditorUi(userName = user.displayName, userRole = user.role.label))
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val deviceId = container.settingsStore.installId()
            val id = editId
            if (id == null) {
                _ui.update { it.copy(loading = false, isNew = true, deviceId = deviceId) }
                return@launch
            }
            val before = repo.get(id)
            val editable = repo.takeForEditing(id)
            val r = repo.get(id)
            if (!editable || r == null) {
                container.events.error("Este reporte ya no puede editarse (se está enviando o ya fue sincronizado).")
                _ui.update { it.copy(loading = false, closeRequested = true) }
                return@launch
            }
            val atts = repo.attachmentsFor(id)
            _ui.update {
                it.copy(
                    loading = false,
                    isNew = false,
                    folio = r.folio,
                    createdAt = r.createdAt,
                    userName = r.createdByName,
                    userRole = r.createdByRole,
                    deviceId = r.deviceInstallId,
                    version = r.version,
                    status = r.status,
                    wasQueued = before?.status == ReportStatus.EN_COLA,
                    typeCode = r.reportTypeCode.ifBlank { null },
                    processCode = r.processCode.ifBlank { null },
                    area = r.area,
                    shift = r.shift,
                    extra = r.extraField.orEmpty(),
                    observations = r.observations,
                    attachments = atts,
                    qualityConfirmed = r.qualityConfirmed,
                    // Adjuntos ya confirmados en una revisión anterior cuentan como revisados;
                    // cualquier adjunto nuevo o reemplazado deberá abrirse otra vez.
                    viewedIds = if (r.qualityConfirmed) atts.map { it.id }.toSet() else emptySet(),
                    step = if (r.reportTypeCode.isBlank()) 0 else if (atts.isEmpty()) 1 else 2,
                )
            }
        }
    }

    // ------------------------------------------------------------------ campos
    fun setType(code: String) = _ui.update {
        if (it.typeCode == code) it else it.copy(typeCode = code, extra = "", errors = it.errors - "type" - "extra")
    }
    fun setProcess(code: String) = _ui.update { it.copy(processCode = code, errors = it.errors - "process") }
    fun setArea(value: String) = _ui.update { it.copy(area = value, errors = it.errors - "area") }
    fun setShift(value: String) = _ui.update { it.copy(shift = value, errors = it.errors - "shift") }
    fun setExtra(value: String) = _ui.update { it.copy(extra = value.take(60), errors = it.errors - "extra") }
    fun setObservations(value: String) = _ui.update {
        it.copy(observations = value.take(ReportValidation.MAX_OBSERVATIONS), errors = it.errors - "observations")
    }
    /** Solo se puede marcar cuando todos los adjuntos fueron revisados en la vista previa. */
    fun setQualityConfirmed(value: Boolean) = _ui.update {
        if (value && !it.allViewed) it else it.copy(qualityConfirmed = value, errors = it.errors - "quality")
    }

    /** Se llama al abrir un adjunto en la vista previa: recibe el indicador verde de «revisado». */
    fun markViewed(attachmentId: String) = _ui.update { it.copy(viewedIds = it.viewedIds + attachmentId) }
    fun dismissAttachmentError() = _ui.update { it.copy(attachmentError = null) }
    fun dismissQualityPrompt() = _ui.update { it.copy(qualityPrompt = null) }

    // ------------------------------------------------------------------ pasos
    fun goToStep(step: Int) {
        val current = _ui.value.step
        if (step < current) _ui.update { it.copy(step = step) } else if (step > current) next()
    }

    fun back() = _ui.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun next() {
        val s = _ui.value
        when (s.step) {
            0 -> {
                val errors = ReportValidation.validateContext(s.form)
                if (errors.isNotEmpty()) {
                    _ui.update { it.copy(errors = errors) }
                    return
                }
                viewModelScope.launch {
                    persist()
                    _ui.update { it.copy(step = 1, errors = emptyMap()) }
                }
            }
            1 -> {
                if (s.attachments.isEmpty()) {
                    _ui.update { it.copy(errors = mapOf("attachments" to "Adjunta al menos un documento.")) }
                    return
                }
                viewModelScope.launch {
                    persist()
                    _ui.update { it.copy(step = 2, errors = emptyMap()) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ persistencia
    private suspend fun ensureReport(): String = persistMutex.withLock {
        reportId?.let { return@withLock it }
        val draft = repo.createDraft(user, _ui.value.fields)
        reportId = draft.id
        _ui.update { it.copy(folio = draft.folio, createdAt = draft.createdAt, deviceId = draft.deviceInstallId) }
        draft.id
    }

    /** Guarda localmente (autoguardado). @return false si el reporte dejó de ser editable. */
    private suspend fun persist(): Boolean {
        val id = ensureReport()
        return repo.saveFields(id, _ui.value.fields)
    }

    fun saveDraft() {
        viewModelScope.launch {
            if (!_ui.value.hasInput && reportId == null) {
                _ui.update { it.copy(closeRequested = true) }
                return@launch
            }
            if (persist()) {
                container.events.info("Borrador guardado en el dispositivo.")
                _ui.update { it.copy(doneReportId = reportId) }
            } else {
                container.events.error("No se pudo guardar: el reporte ya no es editable.")
            }
        }
    }

    fun submit() {
        val s = _ui.value
        val errors = LinkedHashMap(ReportValidation.validateForQueue(s.form))
        if (s.attachments.isNotEmpty() && !s.allViewed) {
            errors["quality"] = "Abre y revisa cada adjunto (${s.viewedCount}/${s.attachments.size} revisados) para habilitar la confirmación."
        }
        if (errors.isNotEmpty()) {
            val step = when {
                errors.keys.any { it in setOf("type", "process", "area", "shift", "extra", "observations") } -> 0
                "attachments" in errors -> 1
                else -> 2
            }
            _ui.update { it.copy(errors = errors, step = step) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val ok = persist()
            val id = reportId
            if (!ok || id == null || !repo.enqueue(id)) {
                container.events.error("No se pudo agregar a la cola: el reporte ya no es editable.")
                _ui.update { it.copy(busy = false) }
                return@launch
            }
            if (container.online.value) {
                container.events.info("Guardado localmente y en cola: enviando…")
            } else {
                container.events.warning("Sin conexión: el reporte quedó guardado en cola y se enviará automáticamente.")
            }
            container.syncManager.requestSync()
            _ui.update { it.copy(busy = false, doneReportId = id) }
        }
    }

    // ------------------------------------------------------------------ adjuntos
    fun prepareCamera(replaceId: String?): Uri {
        replaceTargetId = replaceId
        return container.storage.createCameraTarget().also { cameraUri = it }
    }

    fun setReplaceTarget(replaceId: String?) {
        replaceTargetId = replaceId
    }

    fun onCameraResult(success: Boolean) {
        val uri = cameraUri
        cameraUri = null
        if (success && uri != null) {
            importUri(uri, AttachmentSource.CAMARA)
        } else {
            replaceTargetId = null
            container.storage.clearCameraCache()
        }
    }

    fun onCameraUnavailable() {
        replaceTargetId = null
        _ui.update { it.copy(attachmentError = "No se encontró una app de cámara en el dispositivo. Usa Galería o PDF.") }
    }

    fun onPickerUnavailable() {
        replaceTargetId = null
        _ui.update { it.copy(attachmentError = "No se encontró un selector de archivos en el dispositivo.") }
    }

    fun onPicked(uri: Uri?, source: AttachmentSource) {
        if (uri == null) {
            replaceTargetId = null
            return
        }
        importUri(uri, source)
    }

    /** Selección múltiple desde la galería (hasta completar el máximo de 5 adjuntos). */
    fun onPickedMultiple(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importMany(uris, AttachmentSource.GALERIA)
    }

    private fun importUri(uri: Uri, source: AttachmentSource) = importMany(listOf(uri), source)

    private fun importMany(uris: List<Uri>, source: AttachmentSource) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, attachmentError = null) }
            val id = ensureReport()
            val replaceId = replaceTargetId
            replaceTargetId = null

            var added = 0
            val problems = mutableListOf<String>()
            var firstWarning: AttachmentEntity? = null

            for (uri in uris) {
                val current = _ui.value.attachments
                val countForValidation = current.size - if (replaceId != null && added == 0 && current.any { it.id == replaceId }) 1 else 0
                when (val result = container.storage.import(id, uri, source, countForValidation)) {
                    is ImportResult.Rejected -> problems += result.reason
                    is ImportResult.Success -> {
                        val att = result.attachment
                        val replacing = if (added == 0) replaceId else null
                        if (current.any { it.sha256 == att.sha256 && it.id != replacing }) {
                            container.storage.deleteFile(att.localPath)
                            problems += "Ese archivo ya está adjunto en este reporte (duplicado)."
                            continue
                        }
                        if (replacing != null) {
                            current.firstOrNull { it.id == replacing }?.let { repo.removeAttachment(it) }
                        }
                        repo.addAttachment(att)
                        val otherFolios = repo.duplicateFolios(att.sha256, id)
                        if (otherFolios.isNotEmpty()) {
                            container.events.warning("Atención: «${att.displayName}» ya fue adjuntado en ${otherFolios.joinToString()}. Verifica que no sea un reenvío accidental.")
                        }
                        if (firstWarning == null && att.qualityWarning != null) firstWarning = att
                        added++
                        _ui.update { st ->
                            st.copy(
                                attachments = st.attachments.filterNot { it.id == replacing } + att,
                                qualityConfirmed = false,
                                errors = st.errors - "attachments",
                            )
                        }
                    }
                }
            }

            val error = when {
                problems.isEmpty() -> null
                uris.size == 1 -> problems.first()
                else -> "Se agregaron $added de ${uris.size} archivos. ${problems.distinct().joinToString(" ")}"
            }
            if (uris.size > 1 && added > 0) container.events.info("$added archivo(s) agregado(s) desde la galería.")
            _ui.update { it.copy(busy = false, attachmentError = error, qualityPrompt = firstWarning) }
        }
    }

    fun removeAttachment(att: AttachmentEntity) {
        viewModelScope.launch {
            repo.removeAttachment(att)
            _ui.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == att.id }, qualityConfirmed = false) }
        }
    }

    fun rotate(att: AttachmentEntity, onRotated: (AttachmentEntity) -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val rotated = container.storage.rotate(att)
            if (rotated != null) {
                repo.updateAttachment(rotated)
                _ui.update { s -> s.copy(attachments = s.attachments.map { if (it.id == att.id) rotated else it }) }
                onRotated(rotated)
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    // ------------------------------------------------------------------ autoguardado al salir
    override fun onCleared() {
        val s = _ui.value
        if (s.doneReportId != null || s.closeRequested || s.loading) return
        val id = reportId
        val fields = s.fields
        val form = s.form
        val wasQueued = s.wasQueued
        container.appScope.launch {
            if (id == null) {
                // Evita pérdida de información: si escribió algo, se conserva como borrador.
                if (s.hasInput) {
                    repo.createDraft(user, fields)
                    container.events.info("Se guardó un borrador con lo ingresado.")
                }
                return@launch
            }
            repo.saveFields(id, fields)
            if (wasQueued && ReportValidation.validateForQueue(form).isEmpty()) {
                repo.enqueue(id)
                container.syncManager.requestSync()
            } else {
                container.events.info("Cambios guardados en el dispositivo.")
            }
        }
    }

    fun typeName(): String? = Catalog.type(_ui.value.typeCode)?.name
}
