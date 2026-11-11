package cl.vitairos.app.data

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Estados del reporte. Se muestran siempre con texto + ícono. */
enum class Estado(val texto: String) {
    BORRADOR("Borrador"),
    EN_COLA("En cola"),
    SINCRONIZADO("Sincronizado"),
    ERROR("Error"),
}

/** Usuario ficticio (no se guardan contraseñas: solo su hash). Contraseña demo: demo123 */
data class Usuario(val id: String, val nombre: String, internal val hash: String)

/** Catálogo cerrado de demostración (reemplazable por el oficial de VITAIROS). */
object Catalogo {
    val tipos = listOf(
        "Reporte de turno",
        "Orden de mantención",
        "Checklist de inspección",
        "Reporte de incidente",
        "Parte de producción",
    )

    val procesos = listOf(
        "Extracción mina",
        "Chancado",
        "Molienda",
        "Flotación",
        "Mantención mecánica",
        "Mantención eléctrica",
        "Transporte y despacho",
        "Seguridad y salud ocupacional",
    )

    val usuarios = listOf(
        Usuario("operador1", "Camila Rojas", "aac2199d4ce1df971c20415b2afc39537d85883137208e35dfbdf80806afc404"),
        Usuario("operador2", "Diego Fuentes", "d8bec6c5e92085bf0cd5a6fa9777d45241cafea9facb8bbc8fc55708966af9d7"),
    )

    fun usuarioPorId(id: String?): Usuario? = usuarios.firstOrNull { it.id == id }

    /** Login local simulado. */
    fun autenticar(usuario: String, clave: String): Usuario? {
        val u = usuarios.firstOrNull { it.id.equals(usuario.trim(), ignoreCase = true) } ?: return null
        return if (hash(u.id, clave) == u.hash) u else null
    }

    fun hash(usuario: String, clave: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("vitairos-mvp:$usuario:$clave".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun nuevoFolio(ahora: Long = System.currentTimeMillis()): String =
        "VTR-" + SimpleDateFormat("yyMMdd", Locale.US).format(Date(ahora)) + "-" +
            UUID.randomUUID().toString().take(4).uppercase(Locale.US)
}

/** Validación del formulario «Nuevo reporte». Devuelve campo → mensaje. */
object Validacion {
    const val MAX_OBSERVACIONES = 300

    fun errores(tipo: String?, proceso: String?, fotoPath: String?, fotoVerificada: Boolean, observaciones: String): Map<String, String> {
        val e = linkedMapOf<String, String>()
        if (tipo.isNullOrBlank()) e["tipo"] = "Selecciona el tipo de reporte."
        if (proceso.isNullOrBlank()) e["proceso"] = "Selecciona el proceso asociado."
        if (fotoPath == null) e["foto"] = "Toma o selecciona una foto del documento."
        else if (!fotoVerificada) e["foto"] = "Abre la foto y confirma «Se ve bien»."
        if (observaciones.length > MAX_OBSERVACIONES) e["observaciones"] = "Máximo $MAX_OBSERVACIONES caracteres."
        return e
    }
}
