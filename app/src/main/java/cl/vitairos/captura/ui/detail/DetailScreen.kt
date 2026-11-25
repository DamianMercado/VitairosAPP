package cl.vitairos.captura.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.local.ReportEntity
import cl.vitairos.captura.data.local.ReportEventEntity
import cl.vitairos.captura.data.local.ReportWithAttachments
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.ProcessingStatus
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.model.UserRole
import cl.vitairos.captura.domain.Cooldown
import cl.vitairos.captura.domain.RetryPolicy
import cl.vitairos.captura.domain.formatBytes
import cl.vitairos.captura.ui.components.AttachmentPreviewDialog
import cl.vitairos.captura.ui.components.AttachmentThumb
import cl.vitairos.captura.ui.components.ConfirmDialog
import cl.vitairos.captura.ui.components.EmptyState
import cl.vitairos.captura.ui.components.InfoRow
import cl.vitairos.captura.ui.components.SectionTitle
import cl.vitairos.captura.ui.components.safeBottomActions
import cl.vitairos.captura.ui.components.SimulationBadge
import cl.vitairos.captura.ui.components.StatusChip
import cl.vitairos.captura.ui.components.VCard
import cl.vitairos.captura.ui.components.VitairosTopBar
import cl.vitairos.captura.ui.components.formatDateTime
import cl.vitairos.captura.ui.components.formatDateTimeSec
import cl.vitairos.captura.ui.components.statusStyle
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos
import cl.vitairos.captura.ui.theme.monoStyle
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DetailUi(
    val loaded: Boolean = false,
    val item: ReportWithAttachments? = null,
    val events: List<ReportEventEntity> = emptyList(),
    val parent: ReportEntity? = null,
    val children: List<ReportEntity> = emptyList(),
    val extracted: Map<String, String>? = null,
)

class DetailViewModel(
    private val container: AppContainer,
    private val user: DemoUser,
    handle: SavedStateHandle,
) : ViewModel() {
    val reportId: String = checkNotNull(handle.get<String>("reportId"))
    private val repo = container.repository

    val ui: StateFlow<DetailUi> = combine(
        repo.observe(reportId),
        repo.observeEvents(reportId),
        repo.observeAll(),
    ) { item, events, all ->
        val parentId = item?.report?.parentReportId
        DetailUi(
            loaded = true,
            item = item,
            events = events.reversed(),
            parent = parentId?.let { pid -> all.firstOrNull { it.report.id == pid }?.report },
            children = all.filter { it.report.parentReportId == reportId }.map { it.report },
            extracted = item?.report?.processingResultJson?.let { json ->
                runCatching {
                    container.gson.fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type)
                }.getOrNull()
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi())

    var checking by mutableStateOf(false)
        private set

    // Antirrebote de 5 s para «Enviar ahora» / «Reintentar».
    private val cooldown = Cooldown(RetryPolicy.MANUAL_COOLDOWN_MS)
    var retryBlockedUntil by mutableStateOf(0L)
        private set

    private fun acquireRetry(): Boolean {
        val now = System.currentTimeMillis()
        if (!cooldown.tryAcquire(reportId, now)) return false
        retryBlockedUntil = now + RetryPolicy.MANUAL_COOLDOWN_MS
        return true
    }

    fun sendNow() {
        if (!acquireRetry()) return
        viewModelScope.launch {
            if (repo.enqueue(reportId)) container.syncManager.requestSync(manual = true)
        }
    }

    fun retry() {
        if (!acquireRetry()) return
        viewModelScope.launch {
            if (repo.enqueue(reportId, manualRetry = true)) {
                container.events.info("Reintento manual solicitado.")
                container.syncManager.requestSync(manual = true)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val folio = ui.value.item?.report?.folio.orEmpty()
            if (repo.delete(reportId)) {
                container.events.info("$folio eliminado del dispositivo.")
                onDeleted()
            } else {
                container.events.error("No se pudo eliminar: el reporte ya comenzó a enviarse o fue sincronizado.")
            }
        }
    }

    fun markNeedsCorrection(note: String) {
        viewModelScope.launch {
            if (repo.markNeedsCorrection(reportId, note.trim(), user)) {
                container.events.warning("Reporte marcado como «Requiere corrección».")
            }
        }
    }

    fun createNewVersion(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val created = repo.createNewVersion(reportId, user)
            if (created != null) {
                container.events.info("Nueva versión ${created.folio} creada. Adjunta la evidencia corregida.")
                onCreated(created.id)
            }
        }
    }

    fun checkProcessing() {
        viewModelScope.launch {
            checking = true
            val msg = container.syncManager.checkProcessing(reportId)
            container.events.info(msg)
            checking = false
        }
    }
}

@Composable
fun DetailScreen(
    user: DemoUser,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenReport: (String) -> Unit,
) {
    val vm = appViewModel { c, h -> DetailViewModel(c, user, h) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<AttachmentEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var correctionDialog by remember { mutableStateOf(false) }

    val r = ui.item?.report
    val attachments = ui.item?.attachments.orEmpty()

    // Reloj de 1 s para mostrar la espera del antirrebote en los botones.
    val now by produceState(System.currentTimeMillis(), vm.retryBlockedUntil) {
        value = System.currentTimeMillis()
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val retryWait: Long? = vm.retryBlockedUntil.takeIf { it > now }?.let { (it - now + 999) / 1000 }

    // Box a pantalla completa: la vista previa se dibuja como capa sobre la pantalla (respeta el área segura).
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // Con la vista previa abierta, la pantalla de atrás queda oculta para lectores de pantalla.
            modifier = if (preview != null) Modifier.clearAndSetSemantics {} else Modifier,
            topBar = { VitairosTopBar(title = "Detalle del reporte", subtitle = r?.folio, onBack = onBack) },
            bottomBar = {
                if (r != null) {
                    DetailActions(
                        report = r,
                        user = user,
                        onEdit = { onEdit(r.id) },
                        onDelete = { confirmDelete = true },
                        onSend = vm::sendNow,
                        onRetry = vm::retry,
                        retryWait = retryWait,
                        onMarkCorrection = { correctionDialog = true },
                        onNewVersion = { vm.createNewVersion { id -> onEdit(id) } },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Vitairos.Hueso,
        ) { padding ->
            when {
                !ui.loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Vitairos.Cobre)
                }
                r == null -> EmptyState(
                    Icons.Outlined.SearchOff, "Reporte no disponible",
                    "Fue eliminado del dispositivo.", Modifier.padding(padding),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { HeaderCard(r, ui.parent, ui.children, onOpenReport) }

                    item { SectionTitle("Metadata del reporte") }
                    item {
                        VCard {
                            InfoRow("ID único", r.id, mono = true)
                            InfoRow("Folio", r.folio, mono = true)
                            InfoRow("Tipo de reporte", Catalog.typeName(r.reportTypeCode))
                            InfoRow("Proceso asociado", Catalog.processName(r.processCode))
                            InfoRow("Área / faena", r.area)
                            InfoRow("Turno", r.shift)
                            Catalog.type(r.reportTypeCode)?.extraField?.let { InfoRow(it.label, r.extraField) }
                            InfoRow("Observaciones", r.observations)
                            InfoRow("Usuario", "${r.createdByName} · ${r.createdByRole}")
                            InfoRow("Dispositivo (ID técnico)", r.deviceInstallId, mono = true)
                            InfoRow("Creado", formatDateTimeSec(r.createdAt))
                            InfoRow("Última actualización", formatDateTimeSec(r.updatedAt))
                            InfoRow("Corrección", if (r.version > 1) "Versión ${r.version} (reenvío corregido)" else "Original")
                        }
                    }

                    item { SectionTitle("Sincronización") }
                    item {
                        VCard {
                            InfoRow("Estado", r.status.label)
                            InfoRow("Intentos de envío", r.attemptCount.toString())
                            InfoRow("Reintentos automáticos (ciclo)", "${r.autoRetryCount}/${RetryPolicy.maxAutoRetries}")
                            InfoRow("Último intento", formatDateTimeSec(r.lastAttemptAt))
                            if (r.status == ReportStatus.EN_COLA && r.nextRetryAt != null) {
                                InfoRow("Próximo reintento", formatDateTimeSec(r.nextRetryAt))
                            }
                            InfoRow("Sincronizado", formatDateTimeSec(r.syncedAt))
                            InfoRow("Comprobante VITAIROS", r.serverReceiptId, mono = true)
                        }
                    }

                    item { SectionTitle("Adjuntos (${attachments.size})") }
                    if (attachments.isEmpty()) {
                        item { Text("Sin adjuntos.", style = MaterialTheme.typography.bodyMedium, color = Vitairos.VerdeMineral) }
                    }
                    items(attachments, key = { it.id }) { att ->
                        VCard(onClick = { preview = att }, contentPadding = 10.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AttachmentThumb(att, Modifier.size(width = 60.dp, height = 76.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(att.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${att.source.label} · ${att.mimeType.substringAfter('/').uppercase()} · ${formatBytes(att.storedSizeBytes)}" +
                                            (att.pageCount?.let { " · $it pág." } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Vitairos.VerdeMineral,
                                    )
                                    Text("SHA-256 ${att.sha256.take(16)}…", style = monoStyle(11), color = Vitairos.VerdeMineral)
                                    if (att.localPath == null) {
                                        Text("Archivo no disponible en este dispositivo", style = MaterialTheme.typography.labelSmall, color = Vitairos.OroOscuro)
                                    } else if (r.status == ReportStatus.SINCRONIZADO || r.status == ReportStatus.REQUIERE_CORRECCION) {
                                        Text("Sincronizado · copia local conservada", style = MaterialTheme.typography.labelSmall, color = Vitairos.OkVerde)
                                    }
                                }
                            }
                        }
                    }

                    if (r.status == ReportStatus.SINCRONIZADO || r.status == ReportStatus.REQUIERE_CORRECCION) {
                        item {
                            SectionTitle("Procesamiento VITAIROS Industrial AI") { SimulationBadge() }
                        }
                        item { ProcessingCard(r, ui.extracted, vm.checking, vm::checkProcessing) }
                    }

                    item { SectionTitle("Trazabilidad (${ui.events.size} eventos)") }
                    item {
                        VCard {
                            ui.events.forEachIndexed { index, e ->
                                TimelineRow(e)
                                if (index < ui.events.lastIndex) HorizontalDivider(color = Vitairos.Niebla)
                            }
                        }
                    }
                }
            }
        }

        preview?.let { att ->
            AttachmentPreviewDialog(attachment = att, onDismiss = { preview = null })
        }
    }
    if (confirmDelete && r != null) {
        ConfirmDialog(
            title = "¿Eliminar ${r.folio}?",
            message = "Se borrará del dispositivo junto con sus adjuntos. Solo es posible porque aún no se ha sincronizado.",
            confirmText = "Eliminar",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (correctionDialog) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { correctionDialog = false },
            title = { Text("Marcar «Requiere corrección»") },
            text = {
                Column {
                    Text("El operador deberá generar una nueva versión. El registro actual se conserva.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        label = { Text("Observación *") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = note.isNotBlank(),
                    onClick = {
                        vm.markNeedsCorrection(note)
                        correctionDialog = false
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { correctionDialog = false }) { Text("Cancelar") } },
            containerColor = Vitairos.Hueso,
        )
    }
}

@Composable
private fun HeaderCard(r: ReportEntity, parent: ReportEntity?, children: List<ReportEntity>, onOpenReport: (String) -> Unit) {
    val style = statusStyle(r.status)
    VCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(Catalog.typeName(r.reportTypeCode), style = MaterialTheme.typography.titleLarge)
                Text(r.folio, style = monoStyle(14), color = Vitairos.VerdeProfundo)
            }
            StatusChip(r.status)
        }
        Spacer(Modifier.height(8.dp))
        Text(r.status.description, style = MaterialTheme.typography.bodySmall, color = Vitairos.VerdeMineral)
        r.lastMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Surface(color = style.container, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = style.content, modifier = Modifier.padding(10.dp))
            }
        }
        r.supervisorNote?.let { note ->
            Spacer(Modifier.height(8.dp))
            Surface(color = Vitairos.CobreFondo, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Observación del supervisor", style = MaterialTheme.typography.labelMedium, color = Vitairos.CobreOscuro, fontWeight = FontWeight.Bold)
                    Text(note, style = MaterialTheme.typography.bodySmall, color = Vitairos.CobreOscuro)
                }
            }
        }
        if (parent != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "↳ Corrige a ${parent.folio} — ver registro original",
                style = MaterialTheme.typography.labelLarge,
                color = Vitairos.VerdeProfundo,
                modifier = Modifier.clickable { onOpenReport(parent.id) }.padding(vertical = 6.dp),
            )
        }
        children.forEach { child ->
            Text(
                "↳ Corregido en ${child.folio} · ${child.status.label}",
                style = MaterialTheme.typography.labelLarge,
                color = Vitairos.VerdeProfundo,
                modifier = Modifier.clickable { onOpenReport(child.id) }.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ProcessingCard(r: ReportEntity, extracted: Map<String, String>?, checking: Boolean, onCheck: () -> Unit) {
    VCard {
        val label = r.processingStatus?.let { s -> runCatching { ProcessingStatus.valueOf(s).label }.getOrDefault(s) } ?: "Sin información"
        InfoRow("Estado de procesamiento", "$label (simulado)")
        if (!extracted.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Campos extraídos de ejemplo — no es IA real", style = MaterialTheme.typography.labelMedium, color = Vitairos.CobreOscuro)
            extracted.forEach { (k, v) -> InfoRow(k, v, mono = true) }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onCheck, enabled = !checking, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text("Consultar estado (API mock)")
        }
        Text(
            "El procesamiento real de VITAIROS Industrial AI está fuera del alcance del MVP móvil.",
            style = MaterialTheme.typography.bodySmall,
            color = Vitairos.VerdeMineral,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TimelineRow(e: ReportEventEntity) {
    val style = statusStyle(e.status)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp).size(10.dp).background(style.content, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTimeSec(e.timestamp), style = monoStyle(11), color = Vitairos.VerdeMineral, modifier = Modifier.weight(1f))
                Text(
                    e.status.label + (e.attempt?.let { " · #$it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = style.content,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(e.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailActions(
    report: ReportEntity,
    user: DemoUser,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    retryWait: Long?,
    onMarkCorrection: () -> Unit,
    onNewVersion: () -> Unit,
) {
    val isOwner = report.createdBy == user.id
    val isSupervisor = user.role == UserRole.SUPERVISOR
    val hasActions = when {
        isSupervisor -> report.status == ReportStatus.SINCRONIZADO
        isOwner -> report.status != ReportStatus.SINCRONIZADO
        else -> false
    }
    if (!hasActions) return

    Surface(color = Vitairos.Blanco, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().safeBottomActions(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val danger = Color(0xFF9E2A1E)
            if (isSupervisor) {
                Button(
                    onClick = onMarkCorrection,
                    colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Outlined.RateReview, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("Marcar requiere corrección")
                }
            } else {
                OwnerActions(report.status, danger, onEdit, onDelete, onSend, onRetry, retryWait, onNewVersion)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.OwnerActions(
    status: ReportStatus,
    danger: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    retryWait: Long?,
    onNewVersion: () -> Unit,
) {
    when (status) {
        ReportStatus.BORRADOR -> {
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = danger)
                Spacer(Modifier.width(6.dp)); Text("Eliminar", color = danger)
            }
            Button(onClick = onEdit, modifier = Modifier.weight(1.4f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo)) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp)); Text("Continuar edición")
            }
        }
        ReportStatus.EN_COLA -> {
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = danger)
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(4.dp)); Text("Editar")
            }
            Button(onClick = onSend, enabled = retryWait == null, modifier = Modifier.weight(1.6f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp)); Text(if (retryWait != null) "Espera $retryWait s" else "Enviar ahora")
            }
        }
        ReportStatus.ENVIANDO -> {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Vitairos.Cobre)
            Text("Enviando… no se puede editar durante el envío.", style = MaterialTheme.typography.bodyMedium)
        }
        ReportStatus.ERROR -> {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp)); Text("Corregir")
            }
            Button(onClick = onRetry, enabled = retryWait == null, modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp)); Text(if (retryWait != null) "Espera $retryWait s" else "Reintentar")
            }
        }
        ReportStatus.REQUIERE_CORRECCION -> {
            Button(onClick = onNewVersion, modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche)) {
                Icon(Icons.Outlined.FileCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("Crear nueva versión corregida")
            }
        }
ReportStatus.SINCRONIZADO -> Unit
    }
}
