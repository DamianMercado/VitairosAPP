package cl.vitairos.captura.ui.queue

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.data.local.ReportEntity
import cl.vitairos.captura.data.local.ReportWithAttachments
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.domain.Cooldown
import cl.vitairos.captura.domain.RetryPolicy
import cl.vitairos.captura.ui.components.ConfirmDialog
import cl.vitairos.captura.ui.components.EmptyState
import cl.vitairos.captura.ui.components.LocalOnline
import cl.vitairos.captura.ui.components.LocalSyncing
import cl.vitairos.captura.ui.components.ReportCard
import cl.vitairos.captura.ui.components.SectionTitle
import cl.vitairos.captura.ui.components.VCard
import cl.vitairos.captura.ui.components.VitairosTopBar
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QueueViewModel(private val container: AppContainer, user: DemoUser) : ViewModel() {
    companion object {
        const val KEY_ALL = "__sincronizar_todo__"
        const val KEY_ERRORS = "__reintentar_errores__"
    }

    private val visible = setOf(ReportStatus.ENVIANDO, ReportStatus.EN_COLA, ReportStatus.ERROR, ReportStatus.BORRADOR)

    val reports: StateFlow<List<ReportWithAttachments>> = container.repository.observeAll()
        .map { list ->
            list.filter { it.report.createdBy == user.id && it.report.status in visible }
                .sortedBy { it.report.createdAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Antirrebote de 5 s en los botones de reintento (evita ráfagas de clics y peticiones simultáneas)
    private val cooldown = Cooldown(RetryPolicy.MANUAL_COOLDOWN_MS)
    private val _blockedUntil = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Clave (id de reporte o [KEY_ALL]) → instante hasta el cual su botón queda inhabilitado. */
    val blockedUntil: StateFlow<Map<String, Long>> = _blockedUntil.asStateFlow()

    private fun acquire(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (!cooldown.tryAcquire(key, now)) return false
        _blockedUntil.update { it + (key to now + RetryPolicy.MANUAL_COOLDOWN_MS) }
        return true
    }

    fun syncNow() {
        if (acquire(KEY_ALL)) container.syncManager.requestSync(manual = true)
    }

    fun retry(id: String) {
        if (!acquire(id)) return
        viewModelScope.launch {
            if (container.repository.enqueue(id, manualRetry = true)) {
                container.events.info("Reintento de envío solicitado.")
                container.syncManager.requestSync(manual = true)
            }
        }
    }

    fun retryAllErrors(ids: List<String>) {
        if (!acquire(KEY_ERRORS)) return
        viewModelScope.launch {
            ids.forEach { container.repository.enqueue(it, manualRetry = true) }
            container.events.info("${ids.size} reporte(s) con error volvieron a la cola.")
            container.syncManager.requestSync(manual = true)
        }
    }

    fun delete(report: ReportEntity) {
        viewModelScope.launch {
            if (container.repository.delete(report.id)) {
                container.events.info("${report.folio} eliminado del dispositivo.")
            } else {
                container.events.error("No se pudo eliminar: el reporte ya comenzó a enviarse.")
            }
        }
    }
}

@Composable
fun QueueScreen(user: DemoUser, onOpenReport: (String) -> Unit, onEdit: (String) -> Unit) {
    val vm = appViewModel { c, _ -> QueueViewModel(c, user) }
    val reports by vm.reports.collectAsStateWithLifecycle()
    val online = LocalOnline.current
    val syncing = LocalSyncing.current
    var toDelete by remember { mutableStateOf<ReportEntity?>(null) }
    val blockedUntil by vm.blockedUntil.collectAsStateWithLifecycle()

    // Reloj para la cuenta regresiva del backoff
    val now by produceState(System.currentTimeMillis(), blockedUntil) {
        value = System.currentTimeMillis()
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    val sending = reports.filter { it.report.status == ReportStatus.ENVIANDO }
    val queued = reports.filter { it.report.status == ReportStatus.EN_COLA }
    val errors = reports.filter { it.report.status == ReportStatus.ERROR }
    val drafts = reports.filter { it.report.status == ReportStatus.BORRADOR }

    /** Segundos restantes de espera para una clave, o null si el botón está disponible. */
    fun waitSecs(key: String): Long? = blockedUntil[key]?.takeIf { it > now }?.let { (it - now + 999) / 1000 }

    Scaffold(
        topBar = { VitairosTopBar(title = "Cola de envíos", subtitle = "${sending.size + queued.size + errors.size} pendiente(s)") },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Vitairos.Hueso,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                VCard {
                    Text(
                        if (online) "Conectado" else "Sin conexión",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (online) Vitairos.OkVerde else Vitairos.OroOscuro,
                    )
                    Text(
                        "Reintentos automáticos: ${RetryPolicy.maxAutoRetries} (${RetryPolicy.backoffSeconds.joinToString(" s · ")} s). " +
                            "Luego el reporte queda en Error con reintento manual. La cola se conserva aunque cierres la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vitairos.VerdeMineral,
                    )
                    Spacer(Modifier.height(10.dp))
                    val waitAll = waitSecs(QueueViewModel.KEY_ALL)
                    Button(
                        onClick = vm::syncNow,
                        enabled = waitAll == null && (!syncing || queued.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                waitAll != null -> "Espera $waitAll s"
                                syncing -> "Sincronizando…"
                                else -> "Sincronizar ahora"
                            }
                        )
                    }
                }
            }

            if (reports.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.CloudDone, "Todo sincronizado", "No hay reportes pendientes en este dispositivo.")
                }
            }

            if (sending.isNotEmpty()) {
                item { SectionTitle("Enviando") }
                items(sending, key = { it.report.id }) { item ->
                    ReportCard(item, onClick = { onOpenReport(item.report.id) }, showUser = false)
                }
            }

            if (queued.isNotEmpty()) {
                item { SectionTitle("En cola (${queued.size})") }
                items(queued, key = { it.report.id }) { item ->
                    val r = item.report
                    ReportCard(item, onClick = { onOpenReport(r.id) }, showUser = false) {
                        val retryAt = r.nextRetryAt
                        if (retryAt != null && retryAt > now) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Timer, contentDescription = null, tint = Vitairos.OroOscuro, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Reintento automático ${r.autoRetryCount}/${RetryPolicy.maxAutoRetries} en ${((retryAt - now) / 1000) + 1} s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Vitairos.OroOscuro,
                                )
                            }
                        } else if (!online) {
                            Spacer(Modifier.height(8.dp))
                            Text("Esperando conexión…", style = MaterialTheme.typography.labelMedium, color = Vitairos.OroOscuro)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(r.id) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Editar")
                            }
                            OutlinedButton(onClick = { toDelete = r }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = Vitairos.ErrorRojo)
                                Spacer(Modifier.width(6.dp)); Text("Eliminar", color = Vitairos.ErrorRojo)
                            }
                        }
                        // Reintento manual con antirrebote de 5 s
                        val waitOne = waitSecs(r.id)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.retry(r.id) },
                            enabled = waitOne == null,
                            colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (waitOne != null) "Reintentar envío (espera $waitOne s)" else "Reintentar envío")
                        }
                    }
                }
            }

            if (errors.isNotEmpty()) {
                item {
                    SectionTitle("Error · requiere acción (${errors.size})") {
                        if (errors.size > 1) {
                            val waitErrors = waitSecs(QueueViewModel.KEY_ERRORS)
                            androidx.compose.material3.TextButton(
                                onClick = { vm.retryAllErrors(errors.map { it.report.id }) },
                                enabled = waitErrors == null,
                            ) {
                                Text(if (waitErrors != null) "Espera $waitErrors s" else "Reintentar todos")
                            }
                        }
                    }
                }
                items(errors, key = { it.report.id }) { item ->
                    val r = item.report
                    ReportCard(item, onClick = { onOpenReport(r.id) }, showUser = false) {
                        r.lastMessage?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Vitairos.ErrorRojo)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(r.id) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Corregir")
                            }
                            val waitErr = waitSecs(r.id)
                            Button(
                                onClick = { vm.retry(r.id) },
                                enabled = waitErr == null,
                                colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text(if (waitErr != null) "$waitErr s" else "Reintentar")
                            }
                        }
                    }
                }
            }

            if (drafts.isNotEmpty()) {
                item { SectionTitle("Borradores (no se envían hasta confirmarlos)") }
                items(drafts, key = { it.report.id }) { item ->
                    val r = item.report
                    ReportCard(item, onClick = { onOpenReport(r.id) }, showUser = false) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(r.id) }, modifier = Modifier.weight(1f)) { Text("Continuar") }
                            OutlinedButton(onClick = { toDelete = r }, modifier = Modifier.weight(1f)) {
                                Text("Eliminar", color = Vitairos.ErrorRojo)
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { r ->
        ConfirmDialog(
            title = "¿Eliminar ${r.folio}?",
            message = "Se borrará del dispositivo junto con sus adjuntos. Esta acción no se puede deshacer.",
            confirmText = "Eliminar",
            destructive = true,
            onConfirm = {
                vm.delete(r)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}
