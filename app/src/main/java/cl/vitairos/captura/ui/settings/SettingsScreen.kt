package cl.vitairos.captura.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.BuildConfig
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.UserRole
import cl.vitairos.captura.data.prefs.AppSettings
import cl.vitairos.captura.data.prefs.MockApiMode
import cl.vitairos.captura.domain.formatBytes
import cl.vitairos.captura.ui.components.ConfirmDialog
import cl.vitairos.captura.ui.components.InfoRow
import cl.vitairos.captura.ui.components.SectionTitle
import cl.vitairos.captura.ui.components.SimulationBadge
import cl.vitairos.captura.ui.components.VCard
import cl.vitairos.captura.ui.components.VitairosTopBar
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer, private val user: DemoUser) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings
    var installId by mutableStateOf("…")
        private set
    var storageBytes by mutableStateOf(0L)
        private set
    var generating by mutableStateOf(false)
        private set

    fun refresh() {
        viewModelScope.launch {
            installId = container.settingsStore.installId()
            storageBytes = withContext(Dispatchers.IO) { container.storage.totalStoredBytes() }
        }
    }

    fun setOffline(value: Boolean) {
        viewModelScope.launch { container.settingsStore.setSimulateOffline(value) }
    }

    fun setMode(mode: MockApiMode) {
        viewModelScope.launch {
            container.settingsStore.setMockApiMode(mode)
            container.events.info("API mock: ${mode.label}")
        }
    }

    fun generateSynthetic() {
        if (generating) return
        viewModelScope.launch {
            generating = true
            val n = container.repository.createSyntheticQueued(user, 10)
            container.events.info("$n reportes sintéticos creados y agregados a la cola.")
            container.syncManager.requestSync()
            storageBytes = withContext(Dispatchers.IO) { container.storage.totalStoredBytes() }
            generating = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.settingsStore.setUser(null)
            container.events.info("Sesión cerrada. Los reportes pendientes siguen guardados en el dispositivo.")
        }
    }
}

@Composable
fun SettingsScreen(user: DemoUser) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c, user) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = { VitairosTopBar(title = "Ajustes", subtitle = "Perfil y modo demostración") },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Vitairos.Hueso,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle("Perfil") }
            item {
                VCard {
                    InfoRow("Nombre", user.displayName)
                    InfoRow("Usuario", user.username, mono = true)
                    InfoRow("Perfil", user.role.label)
                    InfoRow("ID de instalación", vm.installId, mono = true)
                    InfoRow("Adjuntos en el dispositivo", formatBytes(vm.storageBytes))
                }
            }

            item { SectionTitle("Modo demostración") { SimulationBadge() } }
            item {
                VCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Simular sin conexión", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Fuerza el modo offline para probar la cola y la sincronización al reconectar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Vitairos.VerdeMineral,
                            )
                        }
                        Switch(
                            checked = settings.simulateOffline,
                            onCheckedChange = vm::setOffline,
                            colors = SwitchDefaults.colors(checkedTrackColor = Vitairos.Cobre, checkedThumbColor = Vitairos.Blanco),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Respuesta de la API mock", style = MaterialTheme.typography.titleMedium)
                    MockApiMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setMode(mode) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.mockApiMode == mode, onClick = { vm.setMode(mode) })
                            Column {
                                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = Vitairos.VerdeMineral)
                            }
                        }
                    }
                    if (user.role == UserRole.OPERADOR) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = vm::generateSynthetic,
                            enabled = !vm.generating,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (vm.generating) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Code, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Generar 10 reportes sintéticos en cola")
                        }
                        Text(
                            "Útil para validar ≥10 pendientes sin pérdida (activa antes «Simular sin conexión»).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Vitairos.VerdeMineral,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            item { SectionTitle("Seguridad y datos") }
            item {
                VCard {
                    Text(
                        "• Reportes y adjuntos en almacenamiento privado de la app (no en carpetas públicas).\n" +
                            "• Tras sincronizar se conserva una copia local de los adjuntos para consultarlos sin conexión.\n" +
                            "• La sesión guarda solo el ID del usuario ficticio; nunca la contraseña.\n" +
                            "• Respaldo en la nube deshabilitado. Solo datos sintéticos o autorizados.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Versión", BuildConfig.VERSION_NAME, mono = true)
                    InfoRow("API", BuildConfig.API_BASE_URL + " (mock)", mono = true)
                }
            }

            item {
                Button(
                    onClick = { confirmLogout = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cerrar sesión")
                }
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "¿Cerrar sesión?",
            message = "Los reportes pendientes se mantienen en el dispositivo y se sincronizarán cuando vuelvas a ingresar.",
            confirmText = "Cerrar sesión",
            onConfirm = {
                confirmLogout = false
                vm.logout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}
