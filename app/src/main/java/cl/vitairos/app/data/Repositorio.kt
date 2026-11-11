package cl.vitairos.app.data

import java.util.UUID

/**
 * Única puerta a los datos: base SQLite (Room) + fotos + servicio de envío.
 * Recibe interfaces, no clases de Android, para poder probarlo con pruebas unitarias.
 *
 * @param ahora reloj usado para las fechas (en las pruebas se reemplaza por uno fijo).
 */
class Repositorio(
    private val dao: ReporteDao,
    private val fotos: AlmacenFotos,
    private val api: ServicioEnvio,
    private val ahora: () -> Long = { System.currentTimeMillis() },
) {

    fun reportesDe(usuario: String) = dao.observarDe(usuario)
    fun reporte(id: String) = dao.observar(id)
    suspend fun enCola(usuario: String) = dao.enCola(usuario)

    suspend fun crear(
        usuario: Usuario, tipo: String, proceso: String, observaciones: String, fotoPath: String, enviar: Boolean,
    ): Reporte {
        val momento = ahora()
        val r = Reporte(
            id = UUID.randomUUID().toString(),
            folio = Catalogo.nuevoFolio(momento),
            tipo = tipo,
            proceso = proceso,
            observaciones = observaciones.trim(),
            fotoPath = fotoPath,
            usuario = usuario.id,
            usuarioNombre = usuario.nombre,
            creadoEn = momento,
            estado = if (enviar) Estado.EN_COLA else Estado.BORRADOR,
            mensaje = if (enviar) "Guardado en el teléfono y en cola de envío." else "Borrador guardado en el teléfono.",
        )
        dao.guardar(r)
        return r
    }

    /** Envía (o reintenta) un reporte y actualiza su estado. Devuelve null si no hay nada que enviar. */
    suspend fun enviar(id: String): Respuesta? {
        val r = dao.obtener(id) ?: return null
        if (r.estado == Estado.SINCRONIZADO) return null
        val respuesta = api.enviar(r)
        val actualizado = when (respuesta) {
            is Respuesta.Exito -> r.copy(
                estado = Estado.SINCRONIZADO, intentos = r.intentos + 1, sincronizadoEn = ahora(),
                comprobante = respuesta.comprobante, mensaje = "Recibido por VITAIROS.",
            )
            is Respuesta.SinConexion -> r.copy(
                estado = Estado.EN_COLA, intentos = r.intentos + 1,
                mensaje = "Sin conexión: quedó en cola. Envíalo cuando vuelva la señal.",
            )
            is Respuesta.Falla -> r.copy(
                estado = Estado.ERROR, intentos = r.intentos + 1,
                mensaje = "Falló el envío: ${respuesta.detalle}. Usa «Reintentar».",
            )
        }
        dao.guardar(actualizado)
        return respuesta
    }

    /** Elimina el reporte y su foto. Solo funciona si aún no se envió (regla del DAO). */
    suspend fun eliminar(reporte: Reporte): Boolean {
        val ok = dao.eliminar(reporte.id) > 0
        if (ok) fotos.borrar(reporte.fotoPath)
        return ok
    }
}
