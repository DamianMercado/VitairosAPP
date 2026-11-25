package cl.vitairos.captura.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.vitairos.captura.R
import cl.vitairos.captura.data.files.PdfUtils
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.local.ReportWithAttachments
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.domain.attachmentCountLabel
import cl.vitairos.captura.ui.theme.Vitairos
import cl.vitairos.captura.ui.theme.monoStyle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Área segura para barras de acciones inferiores: respeta la barra de navegación del sistema
 * (3 botones o gestos) y agrega un margen extra para que los botones no queden pegados al borde.
 */
fun Modifier.safeBottomActions(): Modifier = this
    .navigationBarsPadding()
    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)

/** Conectividad efectiva y estado de sincronización, disponibles en toda la UI. */
val LocalOnline = compositionLocalOf { true }
val LocalSyncing = compositionLocalOf { false }

private val esCL: Locale = Locale.forLanguageTag("es-CL")
fun formatDateTime(ms: Long?): String = if (ms == null) "—" else SimpleDateFormat("dd-MM-yyyy HH:mm", esCL).format(Date(ms))
fun formatDateTimeSec(ms: Long?): String = if (ms == null) "—" else SimpleDateFormat("dd-MM-yyyy HH:mm:ss", esCL).format(Date(ms))

// ------------------------------------------------------------------ Barra superior
@Composable
fun VitairosTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Vitairos.Oro, maxLines = 1)
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                } else {
                    Image(
                        painter = painterResource(R.drawable.vitairos_isotipo),
                        contentDescription = "VITAIROS",
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(32.dp),
                    )
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Vitairos.VerdeNoche,
                titleContentColor = Vitairos.Hueso,
                navigationIconContentColor = Vitairos.Hueso,
                actionIconContentColor = Vitairos.Hueso,
            ),
        )
        ConnectivityBanner()
        if (LocalSyncing.current) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Vitairos.Cobre,
                trackColor = Vitairos.VerdeMineral,
            )
        }
    }
}

@Composable
fun ConnectivityBanner() {
    AnimatedVisibility(visible = !LocalOnline.current) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Vitairos.OroFondo).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Vitairos.OroOscuro, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Sin conexión · Modo offline: los reportes se guardan en el dispositivo",
                style = MaterialTheme.typography.labelMedium,
                color = Vitairos.OroOscuro,
            )
        }
    }
}

// ------------------------------------------------------------------ Estados
data class StatusStyle(val container: Color, val content: Color, val icon: ImageVector)

fun statusStyle(status: ReportStatus): StatusStyle = when (status) {
    ReportStatus.BORRADOR -> StatusStyle(Vitairos.AceroFondo, Vitairos.AceroOscuro, Icons.Outlined.EditNote)
    ReportStatus.EN_COLA -> StatusStyle(Vitairos.OroFondo, Vitairos.OroOscuro, Icons.Outlined.Schedule)
    ReportStatus.ENVIANDO -> StatusStyle(Vitairos.Niebla, Vitairos.VerdeMineral, Icons.Outlined.CloudUpload)
    ReportStatus.SINCRONIZADO -> StatusStyle(Vitairos.OkFondo, Vitairos.OkVerde, Icons.Outlined.CloudDone)
    ReportStatus.ERROR -> StatusStyle(Vitairos.ErrorFondo, Vitairos.ErrorRojo, Icons.Outlined.ErrorOutline)
    ReportStatus.REQUIERE_CORRECCION -> StatusStyle(Vitairos.CobreFondo, Vitairos.CobreOscuro, Icons.Outlined.RateReview)
}

/** Estado SIEMPRE con texto + ícono (nunca solo color), según la guía visual. */
@Composable
fun StatusChip(status: ReportStatus, modifier: Modifier = Modifier) {
    val style = statusStyle(status)
    Surface(shape = RoundedCornerShape(50), color = style.container, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status == ReportStatus.ENVIANDO) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = style.content)
            } else {
                Icon(style.icon, contentDescription = null, tint = style.content, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(status.label, style = MaterialTheme.typography.labelMedium, color = style.content, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SimulationBadge(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = Vitairos.CobreFondo, border = BorderStroke(1.dp, Vitairos.Cobre), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Science, contentDescription = null, tint = Vitairos.CobreOscuro, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("SIMULACIÓN", style = monoStyle(11), color = Vitairos.CobreOscuro, fontWeight = FontWeight.Medium)
        }
    }
}

// ------------------------------------------------------------------ Tarjetas
@Composable
fun VCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = Vitairos.Blanco, contentColor = Vitairos.VerdeNoche)
    val border = BorderStroke(1.dp, Vitairos.Niebla)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(Locale.ROOT),
            style = monoStyle(12),
            color = Vitairos.VerdeMineral,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun InfoRow(label: String, value: String?, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Vitairos.VerdeMineral, modifier = Modifier.weight(0.42f))
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = if (mono) monoStyle(13) else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f),
        )
    }
    HorizontalDivider(color = Vitairos.Niebla)
}

@Composable
fun ReportCard(
    item: ReportWithAttachments,
    onClick: () -> Unit,
    showUser: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val r = item.report
    VCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    Catalog.typeName(r.reportTypeCode),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(Catalog.processName(r.processCode), r.area).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vitairos.VerdeMineral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(r.status)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            r.folio + if (r.version > 1) " · corrección ${r.version}" else "",
            style = monoStyle(13),
            color = Vitairos.VerdeProfundo,
        )
        Text(
            formatDateTime(r.createdAt) + if (showUser) " · ${r.createdByName}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Vitairos.VerdeMineral,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = Vitairos.VerdeMineral)
            // Solo el total de archivos (sin nombres individuales) para una tarjeta más limpia.
            Text(
                attachmentCountLabel(item.attachments.size),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (r.attemptCount > 0) {
                Text("Intentos: ${r.attemptCount}", style = monoStyle(11), color = Vitairos.VerdeMineral)
            }
        }
        footer?.invoke(this)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Vitairos.Acero, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Vitairos.VerdeMineral)
    }
}

// ------------------------------------------------------------------ Diálogos y selección
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) Vitairos.ErrorRojo else Vitairos.VerdeProfundo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Vitairos.Hueso,
    )
}

/** Ícono rojo de error que se muestra dentro de un campo inválido. */
@Composable
fun ErrorIcon() {
    Icon(
        Icons.Outlined.ErrorOutline,
        contentDescription = "Campo con error",
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(20.dp),
    )
}

/** Mensaje de error con ícono bajo el campo (retroalimentación visual de la validación). */
@Composable
fun FieldError(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Campo de selección desde lista FIJA (proceso, tipo, área…): opciones grandes, cómodas en terreno. */
@Composable
fun <T> SelectField(
    label: String,
    selectedText: String?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionSupporting: ((T) -> String)? = null,
    required: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedText.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (required) "$label *" else label) },
            placeholder = { Text("Seleccionar") },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (error != null) ErrorIcon()
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            },
            isError = error != null,
            supportingText = error?.let { msg -> @Composable { FieldError(msg) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { open = true }
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        val text = optionLabel(option)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelect(option)
                                    open = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = text == selectedText, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(text, style = MaterialTheme.typography.bodyLarge)
                                optionSupporting?.invoke(option)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = Vitairos.VerdeMineral)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
            containerColor = Vitairos.Hueso,
        )
    }
}

// ------------------------------------------------------------------ Adjuntos
@Composable
fun PdfPagePreview(path: String, modifier: Modifier = Modifier, page: Int = 0, widthPx: Int = 700) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path, page, widthPx) {
        value = withContext(Dispatchers.IO) { PdfUtils.renderPage(File(path), page, widthPx) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = "Página ${page + 1} del PDF", modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier.background(Vitairos.Niebla), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Vitairos.VerdeMineral)
        }
    }
}

@Composable
fun AttachmentThumb(attachment: AttachmentEntity, modifier: Modifier = Modifier) {
    val path = attachment.localPath
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Vitairos.Niebla), contentAlignment = Alignment.Center) {
        when {
            path == null || !File(path).exists() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = Vitairos.VerdeMineral)
                Text("Sin archivo", style = MaterialTheme.typography.labelSmall, color = Vitairos.VerdeMineral)
            }
            attachment.mimeType == "application/pdf" -> PdfPagePreview(path, Modifier.fillMaxSize(), widthPx = 300)
            else -> AsyncImage(
                model = File(path),
                contentDescription = attachment.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun AttachmentIcon(mime: String): ImageVector =
    if (mime == "application/pdf") Icons.Outlined.PictureAsPdf else Icons.Outlined.Description
