package cl.vitairos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.vitairos.app.data.Estado
import cl.vitairos.app.ui.theme.Vitairos

/** Inicio: lista de reportes del usuario, botón «Nuevo reporte» y envío de pendientes. */
@Composable
fun InicioScreen(vm: MainViewModel, alNuevo: () -> Unit, alAbrir: (String) -> Unit) {
    val reportes by vm.reportes.collectAsStateWithLifecycle()
    val usuario = vm.usuario
    var ajustes by remember { mutableStateOf(false) }
    val pendientes = reportes.count { it.estado == Estado.EN_COLA }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = "VITAIROS",
                subtitulo = usuario?.let { "Hola, ${it.nombre} · Operador" },
                acciones = {
                    IconButton(onClick = { ajustes = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes") }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalAvisos.current) },
        containerColor = Vitairos.Hueso,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Aparece/desaparece con animación al activar o desactivar «Simular sin conexión».
                AnimatedVisibility(
                    visible = vm.sinConexion,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(Vitairos.OroFondo, MaterialTheme.shapes.small).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Vitairos.OroOscuro)
                        Spacer(Modifier.width(8.dp))
                        Text("Sin conexión (simulado): los reportes quedan guardados y en cola.", color = Vitairos.OroOscuro, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Button(
                    onClick = alNuevo,
                    colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Nuevo reporte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (pendientes > 0) {
                item {
                    OutlinedButton(onClick = vm::enviarPendientes, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enviar pendientes ($pendientes)")
                    }
                }
            }
            item { SeccionTitulo("Mis reportes (${reportes.size})") }
            if (reportes.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Vitairos.Acero, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Aún no tienes reportes", style = MaterialTheme.typography.titleMedium)
                        Text("Toca «Nuevo reporte» para capturar el primero.", style = MaterialTheme.typography.bodyMedium, color = Vitairos.VerdeMineral)
                    }
                }
            }
            items(reportes, key = { it.id }) { r ->
                val enviando = r.id in vm.enviando
                TarjetaReporte(r, enviando, alAbrir = { alAbrir(r.id) }) {
                    if (!enviando && (r.estado == Estado.ERROR || r.estado == Estado.EN_COLA)) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.enviar(r.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (r.estado == Estado.ERROR) "Reintentar" else "Enviar")
                        }
                    }
                }
            }
        }
    }

    if (ajustes) {
        AlertDialog(
            onDismissRequest = { ajustes = false },
            title = { Text("Ajustes · modo demostración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilaSwitch("Simular sin conexión", "Los envíos quedan En cola.", vm.sinConexion, vm::cambiarSinConexion)
                    FilaSwitch("Simular falla del servidor", "Los envíos terminan en Error.", vm.fallaServidor, vm::cambiarFallaServidor)
                    TextButton(onClick = {
                        ajustes = false
                        vm.logout()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cerrar sesión")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ajustes = false }) { Text("Listo") } },
            containerColor = Vitairos.Hueso,
        )
    }
}

@Composable
private fun FilaSwitch(titulo: String, detalle: String, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = Vitairos.VerdeMineral)
        }
        Switch(
            checked = valor,
            onCheckedChange = alCambiar,
            colors = SwitchDefaults.colors(checkedTrackColor = Vitairos.Cobre, checkedThumbColor = Vitairos.Blanco),
        )
    }
}
