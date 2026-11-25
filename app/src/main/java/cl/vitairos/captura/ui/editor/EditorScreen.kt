package cl.vitairos.captura.ui.editor

import cl.vitairos.captura.ui.components.ErrorIcon
import cl.vitairos.captura.ui.components.FieldError
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.domain.AttachmentRules
import cl.vitairos.captura.domain.ReportValidation
import cl.vitairos.captura.domain.formatBytes
import cl.vitairos.captura.ui.components.AttachmentPreviewDialog
import cl.vitairos.captura.ui.components.AttachmentThumb
import cl.vitairos.captura.ui.components.ConfirmDialog
import cl.vitairos.captura.ui.components.InfoRow
import cl.vitairos.captura.ui.components.LocalOnline
import cl.vitairos.captura.ui.components.SectionTitle
import cl.vitairos.captura.ui.components.safeBottomActions
import cl.vitairos.captura.ui.components.SelectField
import cl.vitairos.captura.ui.components.VCard
import cl.vitairos.captura.ui.components.VitairosTopBar
import cl.vitairos.captura.ui.components.formatDateTime
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos

@Composable
fun EditorScreen(user: DemoUser, onBack: () -> Unit, onDone: (String) -> Unit) {
    val vm = appViewModel { c, h -> EditorViewModel(c, user, h) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val online = LocalOnline.current
    var preview by remember { mutableStateOf<AttachmentEntity?>(null) }
    var toRemove by remember { mutableStateOf<AttachmentEntity?>(null) }

    LaunchedEffect(ui.doneReportId) { ui.doneReportId?.let(onDone) }
    LaunchedEffect(ui.closeRequested) { if (ui.closeRequested) onBack() }
    BackHandler(enabled = ui.step > 0) { vm.back() }

    // --- Captura: cámara del sistema, selector de fotos y selector de PDF (Storage Access Framework)
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.onCameraResult(ok)
    }
    // Galería: selección simple (para reemplazar un adjunto) y MÚLTIPLE (para agregar varios a la vez).
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.onPicked(uri, AttachmentSource.GALERIA)
    }
    val galleryMultiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia((AttachmentRules.MAX_FILES - ui.attachments.size).coerceAtLeast(2))
    ) { uris ->
        vm.onPickedMultiple(uris)
    }

    // Abrir un adjunto en la vista previa lo marca como «revisado» (indicador verde).
    LaunchedEffect(preview?.id) { preview?.let { vm.markViewed(it.id) } }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.onPicked(uri, AttachmentSource.PDF)
    }
    val launchCapture: (AttachmentSource, String?) -> Unit = { source, replaceId ->
        when (source) {
            AttachmentSource.CAMARA -> {
                val target = vm.prepareCamera(replaceId)
                try {
                    cameraLauncher.launch(target)
                } catch (e: ActivityNotFoundException) {
                    vm.onCameraUnavailable()
                }
            }
            AttachmentSource.GALERIA, AttachmentSource.SINTETICO -> {
                vm.setReplaceTarget(replaceId)
                val remaining = AttachmentRules.MAX_FILES - ui.attachments.size
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                try {
                    if (replaceId != null || remaining <= 1) {
                        galleryLauncher.launch(request)
                    } else {
                        galleryMultiLauncher.launch(request)
                    }
                } catch (e: ActivityNotFoundException) {
                    vm.onPickerUnavailable()
                }
            }
            AttachmentSource.PDF -> {
                vm.setReplaceTarget(replaceId)
                try {
                    pdfLauncher.launch(arrayOf("application/pdf"))
                } catch (e: ActivityNotFoundException) {
                    vm.onPickerUnavailable()
                }
            }
        }
    }

    // Box a pantalla completa: la vista previa se dibuja como capa sobre la pantalla (respeta el área segura).
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // Con la vista previa abierta, la pantalla de atrás queda oculta para lectores de pantalla.
            modifier = if (preview != null) Modifier.clearAndSetSemantics {} else Modifier,
            topBar = {
                VitairosTopBar(
                    title = if (ui.isNew) "Nuevo reporte" else "Editar reporte",
                    subtitle = ui.folio + if (ui.version > 1) " · corrección ${ui.version}" else "",
                    onBack = onBack,
                )
            },
            bottomBar = {
                if (!ui.loading) {
                    EditorBottomBar(
                        step = ui.step,
                        busy = ui.busy,
                        online = online,
                        onSaveDraft = vm::saveDraft,
                        onBack = vm::back,
                        onNext = vm::next,
                        onSubmit = vm::submit,
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Vitairos.Hueso,
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                StepIndicator(ui.step, onStep = vm::goToStep)
                if (ui.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Vitairos.Cobre, trackColor = Vitairos.Niebla)
                }
                if (ui.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Vitairos.Cobre) }
                } else {
                    when (ui.step) {
                        0 -> ContextStep(ui, vm)
                        1 -> EvidenceStep(
                            ui = ui,
                            onCapture = { source -> launchCapture(source, null) },
                            onPreview = { preview = it },
                            onRemove = { toRemove = it },
                            onReplace = { att -> launchCapture(att.source, att.id) },
                            onDismissError = vm::dismissAttachmentError,
                        )
                        else -> ReviewStep(ui, online, onPreview = { preview = it }, onConfirm = vm::setQualityConfirmed, onEditStep = vm::goToStep)
                    }
                }
            }
        }

        preview?.let { att ->
            AttachmentPreviewDialog(
                attachment = att,
                onDismiss = { preview = null },
                onRotate = { vm.rotate(att) { rotated -> preview = rotated } },
                onReplace = {
                    preview = null
                    launchCapture(att.source, att.id)
                },
            )
        }
    }
    toRemove?.let { att ->
        ConfirmDialog(
            title = "¿Quitar adjunto?",
            message = "Se eliminará «${att.displayName}» de este reporte.",
            confirmText = "Quitar",
            destructive = true,
            onConfirm = {
                vm.removeAttachment(att)
                toRemove = null
            },
            onDismiss = { toRemove = null },
        )
    }
    ui.qualityPrompt?.let { att ->
        AlertDialog(
            onDismissRequest = vm::dismissQualityPrompt,
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Vitairos.OroOscuro) },
            title = { Text("Revisa la calidad") },
            text = {
                Text(
                    "${att.qualityWarning} (detección heurística). Si el documento es legible puedes conservarlo; " +
                        "de lo contrario, repite la captura.",
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissQualityPrompt) { Text("Conservar") } },
            dismissButton = {
                TextButton(onClick = {
                    vm.dismissQualityPrompt()
                    launchCapture(att.source, att.id)
                }) { Text("Repetir captura") }
            },
            containerColor = Vitairos.Hueso,
        )
    }
}

// ====================================================================== Indicador de pasos
@Composable
private fun StepIndicator(step: Int, onStep: (Int) -> Unit) {
    val labels = listOf("Contexto", "Evidencia", "Revisión")
    Row(
        Modifier.fillMaxWidth().background(Vitairos.Blanco).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val done = i < step
            val active = i == step
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { onStep(i) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).background(
                        when {
                            active -> Vitairos.Cobre
                            done -> Vitairos.VerdeProfundo
                            else -> Vitairos.Niebla
                        },
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Outlined.Check, contentDescription = "Completado", tint = Vitairos.Hueso, modifier = Modifier.size(16.dp))
                    } else {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = if (active) Vitairos.VerdeNoche else Vitairos.VerdeMineral)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
            if (i < labels.lastIndex) {
                Box(
                    Modifier.weight(1f).padding(horizontal = 4.dp).height(2.dp)
                        .background(if (done) Vitairos.VerdeProfundo else Vitairos.Niebla)
                )
            }
        }
    }
}

// ====================================================================== Paso 1: contexto
@Composable
private fun ContextStep(ui: EditorUi, vm: EditorViewModel) {
    val type = Catalog.type(ui.typeCode)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VCard {
            Text("Datos automáticos (no editables)", style = MaterialTheme.typography.labelLarge, color = Vitairos.VerdeMineral)
            InfoRow("Folio", ui.folio, mono = true)
            InfoRow("Fecha / hora", formatDateTime(ui.createdAt))
            InfoRow("Usuario", "${ui.userName} · ${ui.userRole}")
            InfoRow("Dispositivo", ui.deviceId, mono = true)
            InfoRow("Estado", ui.status.label)
        }
        SelectField(
            label = "Tipo de reporte",
            selectedText = type?.name,
            options = Catalog.reportTypes,
            optionLabel = { it.name },
            optionSupporting = { it.description },
            onSelect = { vm.setType(it.code) },
            required = true,
            error = ui.errors["type"],
        )
        SelectField(
            label = "Proceso asociado",
            selectedText = Catalog.process(ui.processCode)?.name,
            options = Catalog.processes,
            optionLabel = { it.name },
            onSelect = { vm.setProcess(it.code) },
            required = true,
            error = ui.errors["process"],
        )
        SelectField(
            label = "Área / faena",
            selectedText = ui.area,
            options = Catalog.areas,
            optionLabel = { it },
            onSelect = { vm.setArea(it) },
            required = type?.requiresArea == true,
            error = ui.errors["area"],
        )
        SelectField(
            label = "Turno",
            selectedText = ui.shift,
            options = Catalog.shifts,
            optionLabel = { it },
            onSelect = { vm.setShift(it) },
            required = type?.requiresShift == true,
            error = ui.errors["shift"],
        )
        val extraField = type?.extraField
        if (extraField != null) {
            val options = extraField.options
            if (options != null) {
                SelectField(
                    label = extraField.label,
                    selectedText = ui.extra.ifBlank { null },
                    options = options,
                    optionLabel = { it },
                    onSelect = { vm.setExtra(it) },
                    required = true,
                    error = ui.errors["extra"],
                )
            } else {
                val extraError = ui.errors["extra"]
                OutlinedTextField(
                    value = ui.extra,
                    onValueChange = { vm.setExtra(it) },
                    label = { Text("${extraField.label} *") },
                    placeholder = { Text(extraField.hint) },
                    singleLine = true,
                    isError = extraError != null,
                    trailingIcon = if (extraError != null) (@Composable { ErrorIcon() }) else null,
                    supportingText = extraError?.let { msg -> @Composable { FieldError(msg) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val obsError = ui.errors["observations"]
        OutlinedTextField(
            value = ui.observations,
            onValueChange = { vm.setObservations(it) },
            label = { Text("Observaciones") },
            placeholder = { Text("Contexto adicional útil para la revisión (opcional)") },
            minLines = 3,
            isError = obsError != null,
            trailingIcon = if (obsError != null) (@Composable { ErrorIcon() }) else null,
            supportingText = {
                if (obsError != null) FieldError(obsError)
                else Text("${ui.observations.length}/${ReportValidation.MAX_OBSERVATIONS}")
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "* Obligatorio según el tipo de reporte. Fecha, usuario e identificador se registran automáticamente.",
            style = MaterialTheme.typography.bodySmall,
            color = Vitairos.VerdeMineral,
        )
    }
}

// ====================================================================== Paso 2: evidencia
@Composable
private fun EvidenceStep(
    ui: EditorUi,
    onCapture: (AttachmentSource) -> Unit,
    onPreview: (AttachmentEntity) -> Unit,
    onRemove: (AttachmentEntity) -> Unit,
    onReplace: (AttachmentEntity) -> Unit,
    onDismissError: () -> Unit,
) {
    val canAdd = ui.attachments.size < AttachmentRules.MAX_FILES && !ui.busy
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("Evidencia documental", style = MaterialTheme.typography.titleLarge)
            Text(
                "${ui.attachments.size}/${AttachmentRules.MAX_FILES} adjuntos · JPG, PNG o PDF · máx. 10 MB por archivo. " +
                    "En Galería puedes elegir varias imágenes a la vez.",
                style = MaterialTheme.typography.bodySmall,
                color = Vitairos.VerdeMineral,
            )
        }
        ui.attachmentError?.let { msg ->
            Surface(color = Vitairos.ErrorFondo, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Vitairos.ErrorRojo)
                    Text(msg, color = Vitairos.ErrorRojo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(10.dp))
                    IconButton(onClick = onDismissError) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Vitairos.ErrorRojo) }
                }
            }
        }
        ui.errors["attachments"]?.let { FieldError(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureButton(Icons.Outlined.PhotoCamera, "Tomar foto", canAdd, Modifier.weight(1f), primary = true) { onCapture(AttachmentSource.CAMARA) }
            CaptureButton(Icons.Outlined.PhotoLibrary, "Galería", canAdd, Modifier.weight(1f)) { onCapture(AttachmentSource.GALERIA) }
            CaptureButton(Icons.Outlined.PictureAsPdf, "PDF", canAdd, Modifier.weight(1f)) { onCapture(AttachmentSource.PDF) }
        }

        if (ui.attachments.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Vitairos.Blanco,
                border = BorderStroke(1.dp, Vitairos.Acero),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Aún no hay adjuntos. Captura o selecciona el documento del reporte.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vitairos.VerdeMineral,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        ui.attachments.forEach { att ->
            AttachmentRow(
                att,
                viewed = att.id in ui.viewedIds,
                onPreview = { onPreview(att) },
                onReplace = { onReplace(att) },
                onRemove = { onRemove(att) },
            )
        }

        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = Vitairos.VerdeMineral, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Usa solo documentos sintéticos o autorizados. No fotografíes personas, credenciales ni pantallas sensibles. " +
                    "Las imágenes se comprimen y se guardan en el almacenamiento privado de la app.",
                style = MaterialTheme.typography.bodySmall,
                color = Vitairos.VerdeMineral,
            )
        }
    }
}

@Composable
private fun CaptureButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (primary) Vitairos.VerdeProfundo else Vitairos.Blanco,
        border = if (primary) null else BorderStroke(1.dp, Vitairos.Acero),
        modifier = modifier.height(88.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val tint = when {
                !enabled -> Vitairos.Acero
                primary -> Vitairos.Hueso
                else -> Vitairos.VerdeProfundo
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

@Composable
private fun AttachmentRow(
    att: AttachmentEntity,
    viewed: Boolean,
    onPreview: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    VCard(contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReviewedThumb(att, viewed, Modifier.size(width = 68.dp, height = 88.dp).clickable(onClick = onPreview))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(att.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (att.originalSizeBytes != att.storedSizeBytes) {
                        "${formatBytes(att.originalSizeBytes)} → ${formatBytes(att.storedSizeBytes)} (optimizado)"
                    } else {
                        formatBytes(att.storedSizeBytes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Vitairos.VerdeMineral,
                )
                Text(
                    att.source.label + (att.pageCount?.let { " · $it pág." } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Vitairos.VerdeMineral,
                )
                att.qualityWarning?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Vitairos.OroOscuro, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Vitairos.OroOscuro)
                    }
                }
            }
            Column {
                IconButton(onClick = onPreview) { Icon(Icons.Outlined.Visibility, contentDescription = "Ver") }
                IconButton(onClick = onReplace) { Icon(Icons.Outlined.SwapHoriz, contentDescription = "Reemplazar") }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Quitar", tint = Vitairos.ErrorRojo) }
            }
        }
    }
}

// ====================================================================== Paso 3: revisión
@Composable
private fun ReviewStep(
    ui: EditorUi,
    online: Boolean,
    onPreview: (AttachmentEntity) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onEditStep: (Int) -> Unit,
) {
    val type = Catalog.type(ui.typeCode)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Resumen del reporte") { TextButton(onClick = { onEditStep(0) }) { Text("Editar") } }
        VCard {
            InfoRow("Folio", ui.folio, mono = true)
            InfoRow("Tipo", type?.name)
            InfoRow("Proceso", Catalog.process(ui.processCode)?.name)
            InfoRow("Área / faena", ui.area)
            InfoRow("Turno", ui.shift)
            type?.extraField?.let { InfoRow(it.label, ui.extra) }
            InfoRow("Observaciones", ui.observations)
            InfoRow("Usuario", ui.userName)
            InfoRow("Creado", formatDateTime(ui.createdAt))
        }
        SectionTitle("Adjuntos (${ui.attachments.size})") { TextButton(onClick = { onEditStep(1) }) { Text("Editar") } }
        Text(
            if (ui.allViewed) {
                "Todos los adjuntos fueron revisados."
            } else {
                "Toca cada adjunto para abrirlo y revisarlo (${ui.viewedCount}/${ui.attachments.size} revisados)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (ui.allViewed) Vitairos.OkVerde else Vitairos.OroOscuro,
            fontWeight = FontWeight.Medium,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.attachments.forEach { att ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                    ReviewedThumb(
                        att,
                        viewed = att.id in ui.viewedIds,
                        modifier = Modifier.size(width = 96.dp, height = 124.dp).clickable { onPreview(att) },
                    )
                    Text(att.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val warnings = ui.attachments.filter { it.qualityWarning != null }
        if (warnings.isNotEmpty()) {
            Surface(color = Vitairos.OroFondo, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Advertencias de calidad: " + warnings.joinToString("; ") { "${it.displayName}: ${it.qualityWarning}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Vitairos.OroOscuro,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        val qualityError = ui.errors["quality"]
        Surface(
            color = Vitairos.Blanco,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (qualityError != null) Vitairos.ErrorRojo else Vitairos.Niebla),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Inhabilitado hasta que TODOS los adjuntos se hayan abierto en la vista previa.
            Row(
                Modifier.clickable(enabled = ui.allViewed) { onConfirm(!ui.qualityConfirmed) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = ui.effectiveConfirmed,
                    onCheckedChange = onConfirm,
                    enabled = ui.allViewed,
                    colors = CheckboxDefaults.colors(checkedColor = Vitairos.VerdeProfundo),
                )
                Text(
                    "Revisé la vista previa: los documentos son legibles, corresponden al reporte y están bien orientados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ui.allViewed) Vitairos.VerdeNoche else Vitairos.Acero,
                )
            }
        }
        qualityError?.let { Text(it, color = Vitairos.ErrorRojo, style = MaterialTheme.typography.bodySmall) }
        if (!online) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Vitairos.OroOscuro)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sin conexión: el reporte se guardará en el dispositivo y quedará en cola para enviarse automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vitairos.OroOscuro,
                )
            }
        }
    }
}

// ====================================================================== Barra inferior
@Composable
private fun EditorBottomBar(
    step: Int,
    busy: Boolean,
    online: Boolean,
    onSaveDraft: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(color = Vitairos.Blanco, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().safeBottomActions(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val primary = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo, contentColor = Vitairos.Hueso)
            when (step) {
                0 -> {
                    OutlinedButton(onClick = onSaveDraft, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) { Text("Guardar borrador") }
                    Button(onClick = onNext, enabled = !busy, colors = primary, modifier = Modifier.weight(1f).height(52.dp)) { Text("Siguiente") }
                }
                1 -> {
                    OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) { Text("Atrás") }
                    Button(onClick = onNext, enabled = !busy, colors = primary, modifier = Modifier.weight(1f).height(52.dp)) { Text("Revisar") }
                }
                else -> {
                    OutlinedButton(onClick = onSaveDraft, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) { Text("Borrador") }
                    Button(
                        onClick = onSubmit,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
                        modifier = Modifier.weight(1.6f).height(52.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (online) "Guardar y enviar" else "Guardar en cola", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Miniatura con indicador de revisión: check verde si ya se abrió, ojo gris si falta revisarla. */
@Composable
private fun ReviewedThumb(att: AttachmentEntity, viewed: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        AttachmentThumb(att, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(if (viewed) Vitairos.OkVerde else Vitairos.AceroOscuro, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (viewed) Icons.Outlined.Check else Icons.Outlined.Visibility,
                contentDescription = if (viewed) "Revisado" else "Sin revisar",
                tint = Vitairos.Blanco,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
