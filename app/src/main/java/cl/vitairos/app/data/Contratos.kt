package cl.vitairos.app.data

/**
 * Contratos (interfaces) que usa el Repositorio. Gracias a ellos el Repositorio no depende de
 * Android: en la app se usan [ApiSimulada] y [Fotos], y en las pruebas unitarias se usan versiones falsas.
 */

/** Respuesta del servidor al enviar un reporte. */
sealed interface Respuesta {
    data class Exito(val comprobante: String) : Respuesta
    data object SinConexion : Respuesta
    data class Falla(val detalle: String) : Respuesta
}

/** Envía un reporte al servidor (hoy: API simulada; mañana: VITAIROS Industrial AI). */
interface ServicioEnvio {
    suspend fun enviar(reporte: Reporte): Respuesta
}

/** Manejo de las fotos guardadas en el teléfono. */
interface AlmacenFotos {
    fun borrar(ruta: String?)
}
