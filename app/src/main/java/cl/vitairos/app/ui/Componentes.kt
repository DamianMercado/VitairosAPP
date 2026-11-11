package cl.vitairos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.vitairos.app.R
import cl.vitairos.app.data.Estado
import cl.vitairos.app.data.Reporte
import cl.vitairos.app.ui.theme.Vitairos
import cl.vitairos.app.ui.theme.monoStyle
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Avisos (Snackbar) disponibles en todas las pantallas. */
val LocalAvisos = staticCompositionLocalOf { SnackbarHostState() }

fun fechaHora(ms: Long?): String =
    if (ms == null) "—" else SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.forLanguageTag("es-CL")).format(Date(ms))

@Composable
fun BarraSuperior(
    titulo: String,
    subtitulo: String? = null,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(titulo, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitulo != null) {
                    Text(subtitulo, style = MaterialTheme.typography.labelMedium, color = Vitairos.Oro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            } else {
                Image(
                    painterResource(R.drawable.vitairos_isotipo),
                    contentDescription = "VITAIROS",
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(32.dp),
                )
            }
        },
        actions = acciones,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Vitairos.VerdeNoche,
            titleContentColor = Vitairos.Hueso,
            navigationIconContentColor = Vitairos.Hueso,
            actionIconContentColor = Vitairos.Hueso,
        ),
    )
}

/** Barra de botones inferior que respeta la barra de navegación del sistema en cualquier celular. */
@Composable
fun BarraInferior(contenido: @Composable RowScope.() -> Unit) {
    Surface(color = Vitairos.Blanco, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = contenido,
        )
    }
}

data class EstiloEstado(val fondo: Color, val texto: Color, val icono: ImageVector)

fun estiloEstado(e: Estado): EstiloEstado = when (e) {
    Estado.BORRADOR -> EstiloEstado(Vitairos.AceroFondo, Vitairos.AceroOscuro, Icons.Outlined.EditNote)
    Estado.EN_COLA -> EstiloEstado(Vitairos.OroFondo, Vitairos.OroOscuro, Icons.Outlined.Schedule)
    Estado.SINCRONIZADO -> EstiloEstado(Vitairos.OkFondo, Vitairos.OkVerde, Icons.Outlined.CloudDone)
    Estado.ERROR -> EstiloEstado(Vitairos.ErrorFondo, Vitairos.ErrorRojo, Icons.Outlined.ErrorOutline)
}

/** Estado siempre con texto + ícono. Mientras se envía muestra «Enviando…». */
@Composable
fun ChipEstado(estado: Estado, enviando: Boolean = false) {
    val s = if (enviando) EstiloEstado(Vitairos.Niebla, Vitairos.VerdeMineral, Icons.Outlined.Schedule) else estiloEstado(estado)
    Surface(shape = RoundedCornerShape(50), color = s.fondo) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (enviando) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = s.texto)
            } else {
                Icon(s.icono, contentDescription = null, tint = s.texto, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (enviando) "Enviando…" else estado.texto,
                style = MaterialTheme.typography.labelMedium,
                color = s.texto,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun TarjetaReporte(r: Reporte, enviando: Boolean, alAbrir: () -> Unit, pie: (@Composable () -> Unit)? = null) {
    Card(
        onClick = alAbrir,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Vitairos.Blanco, contentColor = Vitairos.VerdeNoche),
        border = BorderStroke(1.dp, Vitairos.Niebla),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Miniatura(r.fotoPath, Modifier.size(width = 52.dp, height = 64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.tipo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.proceso, style = MaterialTheme.typography.bodyMedium, color = Vitairos.VerdeMineral, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.folio, style = monoStyle(12), color = Vitairos.VerdeProfundo)
                    Text(fechaHora(r.creadoEn), style = MaterialTheme.typography.bodySmall, color = Vitairos.VerdeMineral)
                }
                Spacer(Modifier.width(8.dp))
                ChipEstado(r.estado, enviando)
            }
            pie?.invoke()
        }
    }
}

@Composable
fun Miniatura(ruta: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (ruta != null && File(ruta).exists()) {
            AsyncImage(model = File(ruta), contentDescription = "Foto del reporte", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Surface(color = Vitairos.Niebla, modifier = Modifier.matchParentSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = Vitairos.VerdeMineral)
                }
            }
        }
    }
}

@Composable
fun FilaDato(etiqueta: String, valor: String?, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = Vitairos.VerdeMineral, modifier = Modifier.weight(0.4f))
        Text(
            valor?.takeIf { it.isNotBlank() } ?: "—",
            style = if (mono) monoStyle(13) else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
        )
    }
    HorizontalDivider(color = Vitairos.Niebla)
}

/** Selector desde lista fija con opciones grandes (cómodas en terreno). */
@Composable
fun CampoSeleccion(
    etiqueta: String,
    valor: String?,
    opciones: List<String>,
    alElegir: (String) -> Unit,
    error: String? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("$etiqueta *") },
            placeholder = { Text("Seleccionar") },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (error != null) IconoError()
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            },
            isError = error != null,
            supportingText = error?.let { msg -> @Composable { MensajeError(msg) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).clickable { abierto = true })
    }
    if (abierto) {
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(etiqueta) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    opciones.forEach { opcion ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                alElegir(opcion)
                                abierto = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = opcion == valor, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(opcion, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
            containerColor = Vitairos.Hueso,
        )
    }
}

/** Ícono rojo de error que se muestra dentro del campo inválido. */
@Composable
fun IconoError() {
    Icon(
        Icons.Outlined.ErrorOutline,
        contentDescription = "Campo con error",
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(20.dp),
    )
}

/** Mensaje de error con ícono, debajo del campo (retroalimentación visual de la validación). */
@Composable
fun MensajeError(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DialogoConfirmar(titulo: String, mensaje: String, textoConfirmar: String, alConfirmar: () -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = alConfirmar) { Text(textoConfirmar, color = Vitairos.ErrorRojo, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
        containerColor = Vitairos.Hueso,
    )
}

@Composable
fun SeccionTitulo(texto: String) {
    Text(
        texto.uppercase(Locale.ROOT),
        style = monoStyle(12),
        color = Vitairos.VerdeMineral,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}
