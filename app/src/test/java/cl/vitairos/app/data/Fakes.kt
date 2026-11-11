package cl.vitairos.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Versiones FALSAS (en memoria) de la base de datos, del servidor y de las fotos.
 * Permiten probar el Repositorio sin teléfono, sin Room y sin red.
 */
class FakeReporteDao : ReporteDao {
    val tabla = MutableStateFlow<Map<String, Reporte>>(emptyMap())

    override fun observarDe(usuario: String): Flow<List<Reporte>> =
        tabla.map { filas -> filas.values.filter { it.usuario == usuario }.sortedByDescending { it.creadoEn } }

    override fun observar(id: String): Flow<Reporte?> = tabla.map { it[id] }

    override suspend fun obtener(id: String): Reporte? = tabla.value[id]

    override suspend fun enCola(usuario: String): List<Reporte> =
        tabla.value.values.filter { it.usuario == usuario && it.estado == Estado.EN_COLA }.sortedBy { it.creadoEn }

    override suspend fun guardar(reporte: Reporte) {
        tabla.value = tabla.value + (reporte.id to reporte)
    }

    /** Misma regla que la consulta SQL real: solo se borra si está en BORRADOR o EN_COLA. */
    override suspend fun eliminar(id: String): Int {
        val r = tabla.value[id] ?: return 0
        if (r.estado != Estado.BORRADOR && r.estado != Estado.EN_COLA) return 0
        tabla.value = tabla.value - id
        return 1
    }
}

/** Servidor falso: responde lo que la prueba le indique y recuerda qué recibió. */
class FakeServicioEnvio(var siguiente: Respuesta = Respuesta.Exito("VIA-TEST0001")) : ServicioEnvio {
    val recibidos = mutableListOf<Reporte>()
    override suspend fun enviar(reporte: Reporte): Respuesta {
        recibidos += reporte
        return siguiente
    }
}

/** Almacén de fotos falso: anota qué fotos se borraron. */
class FakeAlmacenFotos : AlmacenFotos {
    val borradas = mutableListOf<String?>()
    override fun borrar(ruta: String?) {
        borradas += ruta
    }
}
