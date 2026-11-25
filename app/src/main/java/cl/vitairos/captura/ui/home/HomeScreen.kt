package cl.vitairos.captura.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.data.local.ReportWithAttachments
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.ui.components.EmptyState
import cl.vitairos.captura.ui.components.LocalOnline
import cl.vitairos.captura.ui.components.ReportCard
import cl.vitairos.captura.ui.components.SectionTitle
import cl.vitairos.captura.ui.components.VCard
import cl.vitairos.captura.ui.components.VitairosTopBar
import cl.vitairos.captura.ui.components.formatDateTime
import cl.vitairos.captura.ui.components.statusStyle
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos
import cl.vitairos.captura.domain.dayGroupLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val container: AppContainer, user: DemoUser) : ViewModel() {
    val reports: StateFlow<List<ReportWithAttachments>> = container.repository.observeAll()
        .map { list -> list.filter { it.report.createdBy == user.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun syncNow() = container.syncManager.requestSync(manual = true)
}

@Composable
fun HomeScreen(
    user: DemoUser,
    onNewReport: () -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenHistory: (String?) -> Unit,
    onOpenQueue: () -> Unit,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c, user) }
    val reports by vm.reports.collectAsStateWithLifecycle()
    val online = LocalOnline.current
    val counts = ReportStatus.entries.associateWith { s -> reports.count { it.report.status == s } }
    val pending = reports.count { it.report.status.isPending }

    Scaffold(
        topBar = { VitairosTopBar(title = "Inicio", subtitle = "VITAIROS Industrial AI · Captura") },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Vitairos.Hueso,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Vitairos.VerdeProfundo, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Hola, ${user.displayName.substringBefore(" ")}", style = MaterialTheme.typography.headlineSmall, color = Vitairos.Hueso)
                        Text(
                            "${user.role.label} · ${formatDateTime(System.currentTimeMillis())}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Vitairos.Niebla,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (online) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = if (online) Vitairos.Oro else Vitairos.Cobre,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (online) "En línea · sincronización automática activa" else "Sin conexión · trabajando offline",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (online) Vitairos.Oro else Vitairos.Cobre,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onNewReport,
                            colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                        ) {
                            Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Nuevo reporte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { SectionTitle("Estado de mis reportes") }
            item {
                val statuses = ReportStatus.entries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    statuses.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { s ->
                                StatTile(s, counts[s] ?: 0, Modifier.weight(1f)) { onOpenHistory(s.name) }
                            }
                        }
                    }
                }
            }

            if (pending > 0) {
                item {
                    VCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, tint = Vitairos.OroOscuro)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$pending pendiente(s) de sincronizar", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (online) "Se envían automáticamente." else "Se enviarán al recuperar la conexión.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Vitairos.VerdeMineral,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenQueue, modifier = Modifier.weight(1f).height(48.dp)) { Text("Ver cola") }
                            Button(
                                onClick = vm::syncNow,
                                colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text("Sincronizar ahora") }
                        }
                    }
                }
            }

            item {
                SectionTitle("Recientes") {
                    TextButton(onClick = { onOpenHistory(null) }) { Text("Ver historial") }
                }
            }
            if (reports.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.Inventory2,
                        "Aún no tienes reportes",
                        "Toca «Nuevo reporte» para capturar tu primer documento.",
                    )
                }
            } else {
                // Separadores cronológicos: «Hoy», «Ayer», «Hace N días», luego la fecha.
                val today = LocalDate.now()
                val zone = ZoneId.systemDefault()
                val groups = reports.take(RECENT_LIMIT).groupBy { r ->
                    dayGroupLabel(Instant.ofEpochMilli(r.report.createdAt).atZone(zone).toLocalDate(), today)
                }
                groups.forEach { (label, group) ->
                    item(key = "dia-$label") { DayHeader(label, group.size) }
                    items(group, key = { it.report.id }) { item ->
                        ReportCard(item, onClick = { onOpenReport(item.report.id) }, showUser = false)
                    }
                }
            }
        }
    }
}

private const val RECENT_LIMIT = 5

@Composable
private fun DayHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Vitairos.VerdeProfundo)
        Spacer(Modifier.width(8.dp))
        Text("($count)", style = MaterialTheme.typography.bodySmall, color = Vitairos.VerdeMineral)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Vitairos.Acero)
    }
}

@Composable
private fun StatTile(status: ReportStatus, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = statusStyle(status)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = style.container,
        modifier = modifier.height(88.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(style.icon, contentDescription = null, tint = style.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.weight(1f))
                Text("$count", style = MaterialTheme.typography.headlineSmall, color = style.content)
            }
            Spacer(Modifier.weight(1f))
            Text(
                status.label,
                style = MaterialTheme.typography.labelMedium,
                color = style.content,
                maxLines = 2,
                modifier = Modifier.background(style.container),
            )
        }
    }
}
