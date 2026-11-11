package cl.vitairos.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del Repositorio (guardar, enviar, reintentar y eliminar) usando una base de datos,
 * un servidor y un almacén de fotos FALSOS. No necesitan teléfono ni Internet.
 */
class RepositorioTest {

    private val camila = Catalogo.usuarioPorId("operador1")!!
    private val diego = Catalogo.usuarioPorId("operador2")!!
    private val ahora = 1_794_322_800_000L // reloj fijo: 10-11-2026, 15:00 UTC

    private lateinit var dao: FakeReporteDao
    private lateinit var api: FakeServicioEnvio
    private lateinit var fotos: FakeAlmacenFotos
    private lateinit var repo: Repositorio

    @Before
    fun preparar() {
        dao = FakeReporteDao()
        api = FakeServicioEnvio()
        fotos = FakeAlmacenFotos()
        repo = Repositorio(dao, fotos, api, ahora = { ahora })
    }

    private fun crear(usuario: Usuario = camila, enviar: Boolean = false, obs: String = "  Correa 3 con desgaste  ") =
        runBlocking { repo.crear(usuario, "Reporte de turno", "Chancado", obs, "/fotos/a.jpg", enviar) }

    @Test
    fun `guardar borrador lo deja en la base local`() = runBlocking {
        val r = crear()
        val guardado = dao.obtener(r.id)!!
        assertEquals(Estado.BORRADOR, guardado.estado)
        assertEquals("Borrador guardado en el teléfono.", guardado.mensaje)
        assertEquals(0, guardado.intentos)
    }

    @Test
    fun `guardar y enviar lo deja en cola`() = runBlocking {
        val r = crear(enviar = true)
        assertEquals(Estado.EN_COLA, dao.obtener(r.id)!!.estado)
    }

    @Test
    fun `el reporte guarda usuario fecha folio y observaciones sin espacios extra`() {
        val r = crear()
        assertEquals("operador1", r.usuario)
        assertEquals("Camila Rojas", r.usuarioNombre)
        assertEquals(ahora, r.creadoEn)
        assertTrue(r.folio.startsWith("VTR-261110-"))
        assertEquals("Correa 3 con desgaste", r.observaciones)
        assertEquals("/fotos/a.jpg", r.fotoPath)
    }

    @Test
    fun `cada reporte tiene un id unico`() {
        assertNotEquals(crear().id, crear().id)
    }

    @Test
    fun `envio exitoso deja el reporte sincronizado con comprobante`() = runBlocking {
        val r = crear(enviar = true)
        val respuesta = repo.enviar(r.id)
        val final = dao.obtener(r.id)!!
        assertEquals(Respuesta.Exito("VIA-TEST0001"), respuesta)
        assertEquals(Estado.SINCRONIZADO, final.estado)
        assertEquals("VIA-TEST0001", final.comprobante)
        assertEquals(ahora, final.sincronizadoEn)
        assertEquals(1, final.intentos)
        assertEquals("Recibido por VITAIROS.", final.mensaje)
    }

    @Test
    fun `sin conexion el reporte queda en cola y suma un intento`() = runBlocking {
        api.siguiente = Respuesta.SinConexion
        val r = crear(enviar = true)
        repo.enviar(r.id)
        val final = dao.obtener(r.id)!!
        assertEquals(Estado.EN_COLA, final.estado)
        assertEquals(1, final.intentos)
        assertNull(final.comprobante)
    }

    @Test
    fun `falla del servidor deja el reporte en error con el detalle`() = runBlocking {
        api.siguiente = Respuesta.Falla("HTTP 500")
        val r = crear(enviar = true)
        repo.enviar(r.id)
        val final = dao.obtener(r.id)!!
        assertEquals(Estado.ERROR, final.estado)
        assertTrue(final.mensaje!!.contains("HTTP 500"))
    }

    @Test
    fun `reintentar despues de un error sincroniza y cuenta los dos intentos`() = runBlocking {
        api.siguiente = Respuesta.Falla("HTTP 500")
        val r = crear(enviar = true)
        repo.enviar(r.id)
        api.siguiente = Respuesta.Exito("VIA-OK000002")
        repo.enviar(r.id)
        val final = dao.obtener(r.id)!!
        assertEquals(Estado.SINCRONIZADO, final.estado)
        assertEquals(2, final.intentos)
        assertEquals("VIA-OK000002", final.comprobante)
    }

    @Test
    fun `un reporte ya sincronizado no se vuelve a enviar`() = runBlocking {
        val r = crear(enviar = true)
        repo.enviar(r.id)
        assertNull(repo.enviar(r.id))
        assertEquals(1, api.recibidos.size)
        assertEquals(1, dao.obtener(r.id)!!.intentos)
    }

    @Test
    fun `enviar un id que no existe no hace nada`() = runBlocking {
        assertNull(repo.enviar("no-existe"))
        assertTrue(api.recibidos.isEmpty())
    }

    @Test
    fun `eliminar un borrador borra el reporte y su foto`() = runBlocking {
        val r = crear()
        assertTrue(repo.eliminar(r))
        assertNull(dao.obtener(r.id))
        assertEquals(listOf<String?>("/fotos/a.jpg"), fotos.borradas)
    }

    @Test
    fun `un reporte sincronizado no se puede eliminar`() = runBlocking {
        val r = crear(enviar = true)
        repo.enviar(r.id)
        assertFalse(repo.eliminar(dao.obtener(r.id)!!))
        assertTrue(fotos.borradas.isEmpty())
    }

    @Test
    fun `cada operador ve solo sus reportes`() = runBlocking {
        crear(camila)
        crear(camila)
        crear(diego)
        val deCamila = repo.reportesDe("operador1").first()
        assertEquals(2, deCamila.size)
        assertTrue(deCamila.all { it.usuario == "operador1" })
    }

    @Test
    fun `la cola de envio solo trae los reportes en cola`() = runBlocking {
        val borrador = crear()
        val enCola = crear(enviar = true)
        val pendientes = repo.enCola("operador1")
        assertEquals(listOf(enCola.id), pendientes.map { it.id })
        assertFalse(pendientes.any { it.id == borrador.id })
    }
}
