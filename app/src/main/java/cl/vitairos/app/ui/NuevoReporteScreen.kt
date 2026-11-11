package cl.vitairos.app.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.vitairos.app.data.Catalogo
import cl.vitairos.app.data.Validacion
import cl.vitairos.app.ui.theme.Vitairos
import kotlinx.coroutines.launch

/**
 * Nuevo reporte en UNA sola pantalla: tipo, proceso, observaciones y foto (cámara o galería).
 * Al capturar se abre «Verificar foto»; solo se puede guardar con la foto verificada.
 */
@Composable
fun NuevoReporteScreen(vm: MainViewModel, alVolver: () -> Unit, alGuardar: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tipo by rememberSaveable { mutableStateOf<String?>(null) }
    var proceso by rememberSaveable { mutableStateOf<String?>(null) }
    var observaciones by rememberSaveable { mutableStateOf("") }
    var foto by rememberSaveable { mutableStateOf<String?>(null) }
    var fotoVerificada by rememberSaveable { mutableStateOf(false) }
    var fuenteCamara by rememberSaveable { mutableStateOf(true) }
    var uriCamara by rememberSaveable { mutableStateOf<String?>(null) }
    var verificando by rememberSaveable { mutableStateOf(false) }
    var cargando by rememberSaveable { mutableStateOf(false) }
    var errorFoto by rememberSaveable { mutableStateOf<String?>(null) }
    var errores by remember { mutableStateOf(mapOf<String, String>()) }
    var guardando by remember { mutableStateOf(false) }
    var rotando by remember { mutableStateOf(false) }
    val ocupado = guardando || cargando || rotando

    // Al salir sin guardar se borra la foto temporal para no dejar archivos huérfanos.
    val salir = {
        vm.borrarFoto(foto)
        alVolver()
    }
    BackHandler(enabled = !verificando, onBack = salir)

    fun recibirFoto(uri: Uri?) {
        if (uri == null) return
        cargando = true
        errorFoto = null
        scope.launch {
            vm.importarFoto(uri)
                .onSuccess { nueva ->
                    vm.borrarFoto(foto) // reemplaza la anterior
                    foto = nueva
                    fotoVerificada = false
                    verificando = true    // se abre la verificación automáticamente
                    errores = errores - "foto"
                }
                .onFailure { errorFoto = it.message ?: "No se pudo leer la imagen." }
            cargando = false
        }
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) recibirFoto(uriCamara?.let(Uri::parse))
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> recibirFoto(uri) }

    fun abrirCamara() {
        fuenteCamara = true
        val uri = vm.nuevaUriCamara()
        uriCamara = uri.toString()
        try {
            camara.launch(uri)
        } catch (e: ActivityNotFoundException) {
            errorFoto = "No hay app de cámara disponible. Usa Galería."
        }
    }

    fun abrirGaleria() {
        fuenteCamara = false
        try {
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            errorFoto = "No hay galería disponible en el dispositivo."
        }
    }

    fun guardar(enviar: Boolean) {
        if (ocupado) return // evita doble guardado o guardar mientras se procesa la foto
        val e = Validacion.errores(tipo, proceso, foto, fotoVerificada, observaciones)
        errores = e
        val t = tipo
        val p = proceso
        val f = foto
        if (e.isEmpty() && t != null && p != null && f != null) {
            guardando = true
            vm.guardar(t, p, observaciones, f, enviar, alGuardar)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // Mientras se verifica la foto, la pantalla de atrás queda oculta para lectores de pantalla.
            modifier = if (verificando) Modifier.clearAndSetSemantics {} else Modifier,
            topBar = { BarraSuperior("Nuevo reporte", "Completa los datos y la foto", alVolver = salir) },
            bottomBar = {
                BarraInferior {
                    OutlinedButton(onClick = { guardar(false) }, enabled = !ocupado, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text("Guardar borrador", maxLines = 1)
                    }
                    Button(
                        onClick = { guardar(true) },
                        enabled = !ocupado,
                        colors = ButtonDefaults.buttonColors(containerColor = Vitairos.Cobre, contentColor = Vitairos.VerdeNoche),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (vm.sinConexion) "Guardar en cola" else "Guardar y enviar", maxLines = 1, fontWeight = FontWeight.Bold)
                    }
                }
            },
            snackbarHost = { SnackbarHost(LocalAvisos.current) },
            containerColor = Vitairos.Hueso,
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CampoSeleccion("Tipo de reporte", tipo, Catalogo.tipos, { tipo = it; errores = errores - "tipo" }, errores["tipo"])
                CampoSeleccion("Proceso asociado", proceso, Catalogo.procesos, { proceso = it; errores = errores - "proceso" }, errores["proceso"])
                OutlinedTextField(
                    value = observaciones,
                    onValueChange = {
                        observaciones = it.take(Validacion.MAX_OBSERVACIONES)
                        errores = errores - "observaciones"
                    },
                    label = { Text("Observaciones (opcional)") },
                    minLines = 3,
                    isError = errores["observaciones"] != null,
                    trailingIcon = if (errores["observaciones"] != null) (@Composable { IconoError() }) else null,
                    supportingText = {
                        val err = errores["observaciones"]
                        if (err != null) MensajeError(err) else Text("${observaciones.length}/${Validacion.MAX_OBSERVACIONES}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                SeccionTitulo("Foto del documento *")
                val fotoActual = foto
                if (fotoActual == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = ::abrirCamara,
                            enabled = !cargando,
                            colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo),
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Cámara", maxLines = 1)
                        }
                        OutlinedButton(onClick = ::abrirGaleria, enabled = !cargando, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Galería", maxLines = 1)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Vitairos.Blanco,
                        border = BorderStroke(1.dp, if (fotoVerificada) Vitairos.OkVerde else Vitairos.Oro),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Miniatura(fotoActual, Modifier.size(width = 72.dp, height = 96.dp).clickable { verificando = true })
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (fotoVerificada) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = if (fotoVerificada) Vitairos.OkVerde else Vitairos.OroOscuro,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (fotoVerificada) "Foto verificada" else "Toca la foto para verificarla",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (fotoVerificada) Vitairos.OkVerde else Vitairos.OroOscuro,
                                    )
                                }
                                OutlinedButton(onClick = { if (fuenteCamara) abrirCamara() else abrirGaleria() }, enabled = !cargando) {
                                    Text("Cambiar foto", maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (cargando) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Vitairos.Cobre)
                        Spacer(Modifier.width(8.dp))
                        Text("Procesando foto…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                (errorFoto ?: errores["foto"])?.let {
                    MensajeError(it)
                }
                Text(
                    "Usa solo documentos sintéticos o autorizados. La foto se comprime y se guarda en el almacenamiento privado de la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vitairos.VerdeMineral,
                )
            }
        }

        val rutaVerificar = foto
        if (verificando && rutaVerificar != null) {
            VerificarFoto(
                ruta = rutaVerificar,
                alCerrar = { verificando = false },
                alConfirmar = {
                    if (!rotando) {
                        fotoVerificada = true
                        verificando = false
                        errores = errores - "foto"
                    }
                },
                alRotar = {
                    if (!rotando) {
                        rotando = true
                        scope.launch {
                            try {
                                vm.rotarFoto(rutaVerificar)?.let { nueva ->
                                    foto = nueva
                                    fotoVerificada = false
                                }
                            } finally {
                                rotando = false
                            }
                        }
                    }
                },
                alRepetir = {
                    verificando = false
                    if (fuenteCamara) abrirCamara() else abrirGaleria()
                },
            )
        }
    }
}
