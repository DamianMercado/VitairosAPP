package cl.vitairos.app.ui

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
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.vitairos.app.ui.theme.Vitairos
import coil.compose.AsyncImage
import java.io.File

/**
 * Pantalla completa para VERIFICAR la foto (revisión de calidad básica).
 *
 * Se dibuja como capa dentro de la misma pantalla (no como Dialog) y usa el ÁREA SEGURA
 * (safeDrawing): barra de estado, recortes de cámara y barra de navegación de 3 botones o gestos,
 * en cualquier marca o tamaño de celular. Debe llamarse dentro de un Box a pantalla completa.
 *
 * @param alRotar  null = no se muestra «Rotar».
 * @param alRepetir null = no se muestra «Repetir».
 * @param textoConfirmar texto del botón principal («Se ve bien» o «Cerrar»).
 */
@Composable
fun VerificarFoto(
    ruta: String,
    alCerrar: () -> Unit,
    alConfirmar: () -> Unit,
    alRotar: (() -> Unit)? = null,
    alRepetir: (() -> Unit)? = null,
    textoConfirmar: String = "Se ve bien",
) {
    BackHandler(onBack = alCerrar)

    Surface(color = Vitairos.VerdeNoche, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Verificar foto",
                    color = Vitairos.Hueso,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = alCerrar) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Vitairos.Hueso) }
            }

            Box(Modifier.weight(1f).fillMaxWidth().background(Vitairos.VerdeMineral), contentAlignment = Alignment.Center) {
                if (File(ruta).exists()) {
                    AsyncImage(
                        model = File(ruta),
                        contentDescription = "Foto del documento",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                } else {
                    Text("La foto no está disponible.", color = Vitairos.Hueso)
                }
            }

            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Vitairos.Niebla, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Verifica: ¿es legible? ¿corresponde al documento? ¿está bien orientada?",
                        color = Vitairos.Niebla,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                BotonesVerificacion(alRotar, alRepetir, alConfirmar, textoConfirmar)
            }
        }
    }
}

/**
 * «Rotar», «Repetir» y «Se ve bien» ADAPTATIVOS al ancho disponible y al tamaño de letra:
 * - Pantalla ancha: los tres en una fila.
 * - Celular normal: «Rotar» y «Repetir» en una fila y «Se ve bien» a lo ancho debajo.
 * - Pantalla muy angosta o letra muy grande: los tres apilados.
 * Altura mínima de 48 dp (no fija) para que el texto nunca se corte.
 */
@Composable
fun BotonesVerificacion(
    alRotar: (() -> Unit)?,
    alRepetir: (() -> Unit)?,
    alConfirmar: () -> Unit,
    textoConfirmar: String,
) {
    val secundarios = listOfNotNull(
        alRotar?.let { Triple("Rotar", Icons.AutoMirrored.Outlined.RotateRight, it) },
        alRepetir?.let { Triple("Repetir", Icons.Outlined.SwapHoriz, it) },
    )
    val escalaLetra = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unaFila = maxWidth >= (if (escalaLetra > 1.15f) 560.dp else 440.dp)
        val apilarTodo = maxWidth < 280.dp || escalaLetra > 1.6f
        when {
            unaFila -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secundarios.forEach { (texto, icono, accion) -> BotonSecundario(texto, icono, accion, Modifier.weight(1f)) }
                BotonPrincipal(textoConfirmar, alConfirmar, Modifier.weight(1f))
            }
            apilarTodo -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                secundarios.forEach { (texto, icono, accion) -> BotonSecundario(texto, icono, accion, Modifier.fillMaxWidth()) }
                BotonPrincipal(textoConfirmar, alConfirmar, Modifier.fillMaxWidth())
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (secundarios.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        secundarios.forEach { (texto, icono, accion) -> BotonSecundario(texto, icono, accion, Modifier.weight(1f)) }
                    }
                }
                BotonPrincipal(textoConfirmar, alConfirmar, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BotonSecundario(texto: String, icono: ImageVector, alPulsar: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = alPulsar, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(icono, contentDescription = null, tint = Vitairos.Hueso, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, color = Vitairos.Hueso, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BotonPrincipal(texto: String, alPulsar: () -> Unit, modifier: Modifier) {
    Button(
        onClick = alPulsar,
        colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(texto, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
