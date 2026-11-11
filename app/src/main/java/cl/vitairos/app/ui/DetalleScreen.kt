package cl.vitairos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.vitairos.app.data.Estado
import cl.vitairos.app.ui.theme.Vitairos

/** Detalle: foto, datos (metadata), estado de envío y acciones según el estado. */
@Composable
fun DetalleScreen(vm: MainViewModel, id: String, alVolver: () -> Unit) {
    val flujo = remember(id) { vm.reporte(id) }
    val reporte by flujo.collectAsStateWithLifecycle(initialValue = null)
    var verFoto by remember { mutableStateOf(false) }
    var confirmarEliminar by remember { mutableStateOf(false) }
    val r = reporte
    val enviando = id in vm.enviando

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (verFoto) Modifier.clearAndSetSemantics {} else Modifier,
            topBar = { BarraSuperior("Detalle del reporte", r?.folio, alVolver = alVolver) },
            bottomBar = {
                if (r != null && r.estado != Estado.SINCRONIZADO) {
                    BarraInferior {
                        when {
                            enviando -> {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Vitairos.Cobre)
                                Spacer(Modifier.width(8.dp))
                                Text("Enviando…", style = MaterialTheme.typography.bodyMedium)
                            }
                            r.estado == Estado.ERROR -> Button(
                                onClick = { vm.enviar(r.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Reintentar", maxLines = 1)
                            }
                            else -> {
                                OutlinedButton(onClick = { confirmarEliminar = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Vitairos.ErrorRojo)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Eliminar", color = Vitairos.ErrorRojo, maxLines = 1)
                                }
                                Button(
                                    onClick = { vm.enviar(r.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo),
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Enviar", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(LocalAvisos.current) },
            containerColor = Vitairos.Hueso,
        ) { padding ->
            if (r == null) {
                Box(Modifier.fillMaxSize().padding(padding))
            } else {
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Miniatura(
                        r.fotoPath,
                        Modifier.fillMaxWidth().height(220.dp).clickable(enabled = r.fotoPath != null) { verFoto = true },
                    )
                    ChipEstado(r.estado, enviando)
                    r.mensaje?.let { msg ->
                        val s = estiloEstado(r.estado)
                        Surface(color = s.fondo, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(msg, color = s.texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                        }
                    }
                    SeccionTitulo("Datos del reporte")
                    Surface(
                        color = Vitairos.Blanco,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Vitairos.Niebla),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            FilaDato("Folio", r.folio, mono = true)
                            FilaDato("Tipo de reporte", r.tipo)
                            FilaDato("Proceso", r.proceso)
                            FilaDato("Observaciones", r.observaciones)
                            FilaDato("Usuario", r.usuarioNombre)
                            FilaDato("Creado", fechaHora(r.creadoEn))
                            FilaDato("Estado", r.estado.texto)
                            FilaDato("Intentos de envío", r.intentos.toString())
                            FilaDato("Sincronizado", fechaHora(r.sincronizadoEn))
                            FilaDato("Comprobante", r.comprobante, mono = true)
                            FilaDato("ID único", r.id, mono = true)
                        }
                    }
                }
            }
        }

        val ruta = r?.fotoPath
        if (verFoto && ruta != null) {
            VerificarFoto(
                ruta = ruta,
                alCerrar = { verFoto = false },
                alConfirmar = { verFoto = false },
                textoConfirmar = "Cerrar",
            )
        }
    }

    if (confirmarEliminar && r != null) {
        DialogoConfirmar(
            titulo = "¿Eliminar ${r.folio}?",
            mensaje = "Se borrará del teléfono junto con su foto. Solo es posible porque aún no se envió.",
            textoConfirmar = "Eliminar",
            alConfirmar = {
                confirmarEliminar = false
                vm.eliminar(r, alVolver)
            },
            alCancelar = { confirmarEliminar = false },
        )
    }
}
