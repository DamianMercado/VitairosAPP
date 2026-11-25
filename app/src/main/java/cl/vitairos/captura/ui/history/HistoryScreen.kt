package cl.vitairos.captura.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.data.local.ReportWithAttachments
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.model.UserRole
import cl.vitairos.captura.ui.components.EmptyState
import cl.vitairos.captura.ui.components.ReportCard
import cl.vitairos.captura.ui.components.VitairosTopBar
import cl.vitairos.captura.ui.components.statusStyle
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(container: AppContainer, private val user: DemoUser, handle: SavedStateHandle) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(
        handle.get<String>("status")?.let { runCatching { ReportStatus.valueOf(it) }.getOrNull() }
    )

    /** Supervisor ve todos los reportes; el operador, solo los propios. */
    private val scoped = container.repository.observeAll()

    val all: StateFlow<List<ReportWithAttachments>> = combine(scoped, query) { list, q ->
        list.filter { user.role == UserRole.SUPERVISOR || it.report.createdBy == user.id }
            .filter { matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visible: StateFlow<List<ReportWithAttachments>> = combine(all, filter) { list, f ->
        if (f == null) list else list.filter { it.report.status == f }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun matches(item: ReportWithAttachments, q: String): Boolean {
        if (q.isBlank()) return true
        val r = item.report
        val haystack = listOfNotNull(
            r.folio, r.id, Catalog.typeName(r.reportTypeCode), Catalog.processName(r.processCode),
            r.area, r.createdByName, r.observations,
        ).joinToString(" ").lowercase()
        return haystack.contains(q.trim().lowercase())
    }
}

@Composable
fun HistoryScreen(user: DemoUser, onOpenReport: (String) -> Unit) {
    val vm = appViewModel { c, h -> HistoryViewModel(c, user, h) }
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val all by vm.all.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            VitairosTopBar(
                title = "Historial",
                subtitle = if (user.role == UserRole.SUPERVISOR) "Todos los operadores" else "Mis reportes",
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Vitairos.Hueso,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Buscar por folio, tipo, proceso, área…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Limpiar") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filter == null,
                        onClick = { vm.filter.value = null },
                        label = { Text("Todos (${all.size})") },
                    )
                }
                items(ReportStatus.entries.toList()) { s ->
                    val style = statusStyle(s)
                    FilterChip(
                        selected = filter == s,
                        onClick = { vm.filter.value = if (filter == s) null else s },
                        label = { Text("${s.label} (${all.count { it.report.status == s }})") },
                        leadingIcon = { Icon(style.icon, contentDescription = null, tint = style.content) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = style.container),
                    )
                }
            }
            if (visible.isEmpty()) {
                EmptyState(Icons.Outlined.History, "Sin resultados", "No hay reportes que coincidan con el filtro.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "${visible.size} reporte(s) · trazabilidad conservada en el dispositivo",
                            style = MaterialTheme.typography.labelMedium,
                            color = Vitairos.VerdeMineral,
                        )
                    }
                    items(visible, key = { it.report.id }) { item ->
                        ReportCard(item, onClick = { onOpenReport(item.report.id) }, showUser = user.role == UserRole.SUPERVISOR)
                    }
                }
            }
        }
    }
}
