package cl.vitairos.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del catálogo: usuarios ficticios, login simulado, listas fijas y folio. */
class CatalogoTest {

    @Test
    fun `login correcto con los dos usuarios ficticios`() {
        assertEquals("Camila Rojas", Catalogo.autenticar("operador1", "demo123")?.nombre)
        assertEquals("Diego Fuentes", Catalogo.autenticar("operador2", "demo123")?.nombre)
    }

    @Test
    fun `el usuario no distingue mayusculas ni espacios`() {
        assertNotNull(Catalogo.autenticar(" OPERADOR1 ", "demo123"))
    }

    @Test
    fun `login falla con clave incorrecta`() {
        assertNull(Catalogo.autenticar("operador1", "otra"))
        assertNull(Catalogo.autenticar("operador1", "DEMO123"))
    }

    @Test
    fun `login falla con usuario inexistente o vacio`() {
        assertNull(Catalogo.autenticar("nadie", "demo123"))
        assertNull(Catalogo.autenticar("", ""))
    }

    @Test
    fun `no se guarda la clave sino su hash`() {
        val h = Catalogo.hash("operador1", "demo123")
        assertEquals(64, h.length) // SHA-256 en hexadecimal
        assertNotEquals("demo123", h)
        assertEquals(h, Catalogo.hash("operador1", "demo123")) // siempre igual
        assertNotEquals(h, Catalogo.hash("operador2", "demo123")) // distinto por usuario
    }

    @Test
    fun `buscar usuario por id`() {
        assertEquals("operador2", Catalogo.usuarioPorId("operador2")?.id)
        assertNull(Catalogo.usuarioPorId("desconocido"))
        assertNull(Catalogo.usuarioPorId(null))
    }

    @Test
    fun `listas fijas sin elementos vacios ni repetidos`() {
        for (lista in listOf(Catalogo.tipos, Catalogo.procesos)) {
            assertTrue(lista.isNotEmpty())
            assertTrue(lista.none { it.isBlank() })
            assertEquals(lista.size, lista.toSet().size)
        }
    }

    @Test
    fun `folio con formato VTR-AAMMDD-XXXX`() {
        assertTrue(Regex("VTR-\\d{6}-[0-9A-F]{4}").matches(Catalogo.nuevoFolio()))
    }

    @Test
    fun `el folio usa la fecha de creacion`() {
        val diezDeNoviembre = 1_794_322_800_000L // 10-11-2026, 15:00 UTC
        assertTrue(Catalogo.nuevoFolio(diezDeNoviembre).startsWith("VTR-261110-"))
    }

    @Test
    fun `cada estado tiene su texto para la pantalla`() {
        assertEquals(
            listOf("Borrador", "En cola", "Sincronizado", "Error"),
            Estado.entries.map { it.texto },
        )
    }
}
