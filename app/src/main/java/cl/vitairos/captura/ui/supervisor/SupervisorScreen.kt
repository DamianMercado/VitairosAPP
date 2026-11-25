package cl.vitairos.captura.ui.supervisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.data.local.ReportWithAttachments
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.ui.components.EmptyState
import cl.vitairos.captura.ui.components.ReportCard
import cl.vitairos.captura.ui.components.SectionTitle
import cl.vitairos.captura.ui.components.StatusChip
import cl.vitairos.captura.ui.components.VCard
import cl.vitairos.captura.ui.components.VitairosTopBar
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SupervisorViewModel(container: AppContainer) : ViewModel() {
    val reports: StateFlow<List<ReportWithAttachments>> = container.repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Vista ACOTADA del perfil Supervisor (cuestionario §2): consulta reportes y estados de todos los
 * operadores y permite marcar un reporte sincronizado como «Requiere corrección».
 */
@Composable
fun SupervisorScreen(user: DemoUser, onOpenReport: (String) -> Unit) {
    val vm = appViewModel { c, _ -> SupervisorViewModel(c) }
    val reports by vm.reports.collectAsStateWithLifecycle()
    val toReview = reports.filter { it.report.status == ReportStatus.SINCRONIZADO }
    val observed = reports.filter { it.report.status == ReportStatus.REQUIERE_CORRECCION }

    Scaffold(
        topBar = { VitairosTopBar(title = "Revisión", subtitle = "Supervisor · ${user.displayName}") },
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
                    Text("Resumen de todos los operadores", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Vista de supervisor simulada para el MVP: los datos provienen de este mismo dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vitairos.VerdeMineral,
                    )
                    ReportStatus.entries.forEach { s ->
                        Row(Modifier.padding(top = 8.dp)) {
                            StatusChip(s)
                            Text(
                                "  ${reports.count { it.report.status == s }}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            item { SectionTitle("Sincronizados por revisar (${toReview.size})") }
            if (toReview.isEmpty()) {
                item { EmptyState(Icons.Outlined.TaskAlt, "Nada por revisar", "Aún no hay reportes sincronizados.") }
            }
            items(toReview, key = { it.report.id }) { item ->
                ReportCard(item, onClick = { onOpenReport(item.report.id) })
            }
            if (observed.isNotEmpty()) {
                item { SectionTitle("Observados · requieren corrección (${observed.size})") }
                items(observed, key = { it.report.id }) { item ->
                    ReportCard(item, onClick = { onOpenReport(item.report.id) })
                }
            }
        }
    }
}
