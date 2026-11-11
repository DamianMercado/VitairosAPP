package cl.vitairos.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.vitairos.app.VitairosApp
import cl.vitairos.app.data.Catalogo
import cl.vitairos.app.data.Respuesta
import cl.vitairos.app.data.Reporte
import cl.vitairos.app.data.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado y acciones de toda la app (prototipo básico: un solo ViewModel). */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app as VitairosApp

    // ---- Sesión simulada
    var usuario by mutableStateOf(Catalogo.usuarioPorId(c.prefs.usuario))
        private set
    var errorLogin by mutableStateOf<String?>(null)
        private set

    // ---- Modo demostración
    var sinConexion by mutableStateOf(c.prefs.sinConexion)
        private set
    var fallaServidor by mutableStateOf(c.prefs.fallaServidor)
        private set

    /** Reportes que se están enviando en este momento (para mostrar «Enviando…»). */
    val enviando = mutableStateListOf<String>()

    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val mensajes: SharedFlow<String> = _mensajes

    val reportes: StateFlow<List<Reporte>> = snapshotFlow { usuario?.id }
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.repo.reportesDe(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun login(usuarioTexto: String, clave: String) {
        val u = Catalogo.autenticar(usuarioTexto, clave)
        if (u == null) {
            errorLogin = "Usuario o contraseña incorrectos."
            return
        }
        errorLogin = null
        usuario = u
        c.prefs.usuario = u.id
    }

    fun logout() {
        usuario = null
        c.prefs.usuario = null
    }

    fun cambiarSinConexion(valor: Boolean) {
        sinConexion = valor
        c.prefs.sinConexion = valor
    }

    fun cambiarFallaServidor(valor: Boolean) {
        fallaServidor = valor
        c.prefs.fallaServidor = valor
    }

    fun reporte(id: String): Flow<Reporte?> = c.repo.reporte(id)

    // ---- Fotos
    fun nuevaUriCamara(): Uri = c.fotos.nuevaUriCamara()
    suspend fun importarFoto(uri: Uri): Result<String> = c.fotos.importar(uri)
    suspend fun rotarFoto(ruta: String): String? = c.fotos.rotar(ruta)
    fun borrarFoto(ruta: String?) = c.fotos.borrar(ruta)

    // ---- Reportes
    fun guardar(tipo: String, proceso: String, observaciones: String, fotoPath: String, enviar: Boolean, alGuardar: (String) -> Unit) {
        val u = usuario ?: return
        viewModelScope.launch {
            val r = c.repo.crear(u, tipo, proceso, observaciones, fotoPath, enviar)
            alGuardar(r.id)
            if (enviar) enviar(r.id) else aviso("Borrador ${r.folio} guardado en el teléfono.")
        }
    }

    fun enviar(id: String) {
        if (id in enviando) return
        enviando += id
        viewModelScope.launch {
            try {
                when (c.repo.enviar(id)) {
                    is Respuesta.Exito -> aviso("Reporte enviado correctamente.")
                    is Respuesta.SinConexion -> aviso("Sin conexión: el reporte quedó en cola.")
                    is Respuesta.Falla -> aviso("Error al enviar. Puedes reintentar.")
                    null -> Unit
                }
            } finally {
                enviando -= id
            }
        }
    }

    fun enviarPendientes() {
        val u = usuario ?: return
        viewModelScope.launch {
            val pendientes = c.repo.enCola(u.id)
            if (pendientes.isEmpty()) aviso("No hay reportes en cola.")
            pendientes.forEach { enviar(it.id) }
        }
    }

    fun eliminar(reporte: Reporte, alEliminar: () -> Unit) {
        viewModelScope.launch {
            if (c.repo.eliminar(reporte)) {
                aviso("Reporte ${reporte.folio} eliminado.")
                alEliminar()
            } else {
                aviso("No se puede eliminar un reporte ya enviado.")
            }
        }
    }

    private fun aviso(texto: String) {
        _mensajes.tryEmit(texto)
    }
}
