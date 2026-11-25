package cl.vitairos.captura.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.domain.formatBytes
import cl.vitairos.captura.ui.theme.Vitairos
import cl.vitairos.captura.ui.theme.monoStyle
import coil.compose.AsyncImage
import java.io.File

/**
 * Vista previa a pantalla completa = "revisión de calidad básica" (cuestionario §4):
 * el usuario verifica legibilidad, que corresponda al documento esperado y su orientación.
 *
 * Se dibuja como CAPA dentro de la misma pantalla (no como Dialog): así Android entrega
 * correctamente el área segura (barra de estado, recortes y barra de navegación de 3 botones
 * o gestos) en cualquier marca y tamaño de celular. Debe llamarse dentro de un Box a pantalla completa.
 */
@Composable
fun AttachmentPreviewDialog(
    attachment: AttachmentEntity,
    onDismiss: () -> Unit,
    onRotate: (() -> Unit)? = null,
    onReplace: (() -> Unit)? = null,
) {
    BackHandler(onBack = onDismiss)

    Surface(color = Vitairos.VerdeNoche, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(attachment.displayName, color = Vitairos.Hueso, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(attachment.mimeType.substringAfter('/').uppercase())
                            append(" · ")
                            append(formatBytes(attachment.storedSizeBytes))
                            attachment.pageCount?.let { append(" · $it pág.") }
                        },
                        style = monoStyle(12),
                        color = Vitairos.Acero,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Vitairos.Hueso) }
            }

            var page by remember(attachment.id) { mutableIntStateOf(0) }
            val pages = attachment.pageCount ?: 1
            val path = attachment.localPath
            Box(Modifier.weight(1f).fillMaxWidth().background(Vitairos.VerdeMineral), contentAlignment = Alignment.Center) {
                when {
                    path == null || !File(path).exists() ->
                        Text("El archivo no está disponible en este dispositivo.", color = Vitairos.Hueso, modifier = Modifier.padding(24.dp))
                    attachment.mimeType == "application/pdf" ->
                        PdfPagePreview(path, Modifier.fillMaxSize().padding(8.dp), page = page, widthPx = 1200)
                    else -> AsyncImage(
                        model = File(path),
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            }
            if (attachment.mimeType == "application/pdf" && pages > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior", color = Vitairos.Hueso) }
                    Text("Página ${page + 1} de $pages", color = Vitairos.Hueso, style = monoStyle(13))
                    TextButton(onClick = { if (page < pages - 1) page++ }, enabled = page < pages - 1) { Text("Siguiente", color = Vitairos.Hueso) }
                }
            }

            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                attachment.qualityWarning?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Vitairos.Oro, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("$it (heurística automática)", color = Vitairos.Oro, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Vitairos.Niebla, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Verifica: ¿es legible? ¿corresponde al documento esperado? ¿está bien orientado?",
                        color = Vitairos.Niebla,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ReviewActions(
                    onRotate = if (attachment.mimeType.startsWith("image/") && path != null) onRotate else null,
                    onReplace = onReplace,
                    onConfirm = onDismiss,
                )
            }
        }
    }
}

/**
 * Botones «Rotar», «Repetir» y «Se ve bien» ADAPTATIVOS según el ancho disponible y el tamaño de letra:
 * - Pantalla ancha: los tres en una fila.
 * - Celular normal: «Rotar» y «Repetir» en una fila y «Se ve bien» a lo ancho debajo.
 * - Pantalla muy angosta o letra muy grande: los tres apilados a lo ancho.
 * La altura mínima es 48 dp (no fija) para que el texto nunca se corte.
 */
@Composable
fun ReviewActions(onRotate: (() -> Unit)?, onReplace: (() -> Unit)?, onConfirm: () -> Unit) {
    val secondary = listOfNotNull(
        onRotate?.let { Triple("Rotar", Icons.Outlined.RotateRight, it) },
        onReplace?.let { Triple("Repetir", Icons.Outlined.SwapHoriz, it) },
    )
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val oneRow = maxWidth >= (if (fontScale > 1.15f) 560.dp else 440.dp)
        val stackAll = maxWidth < 280.dp || fontScale > 1.6f
        when {
            oneRow -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondary.forEach { (label, icon, action) -> SecondaryAction(label, icon, action, Modifier.weight(1f)) }
                ConfirmAction(onConfirm, Modifier.weight(1f))
            }
            stackAll -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                secondary.forEach { (label, icon, action) -> SecondaryAction(label, icon, action, Modifier.fillMaxWidth()) }
                ConfirmAction(onConfirm, Modifier.fillMaxWidth())
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (secondary.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        secondary.forEach { (label, icon, action) -> SecondaryAction(label, icon, action, Modifier.weight(1f)) }
                    }
                }
                ConfirmAction(onConfirm, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SecondaryAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = null, tint = Vitairos.Hueso, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Vitairos.Hueso, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ConfirmAction(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text("Se ve bien", maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
