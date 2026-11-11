package cl.vitairos.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la validación del formulario «Nuevo reporte». */
class ValidacionTest {

    private fun validar(
        tipo: String? = "Reporte de turno",
        proceso: String? = "Chancado",
        foto: String? = "/fotos/a.jpg",
        verificada: Boolean = true,
        obs: String = "",
    ) = Validacion.errores(tipo, proceso, foto, verificada, obs)

    @Test
    fun `formulario completo no tiene errores`() {
        assertTrue(validar().isEmpty())
    }

    @Test
    fun `formulario vacio marca tipo proceso y foto en ese orden`() {
        val e = validar(tipo = null, proceso = null, foto = null, verificada = false)
        assertEquals(listOf("tipo", "proceso", "foto"), e.keys.toList())
    }

    @Test
    fun `texto en blanco cuenta como vacio`() {
        val e = validar(tipo = "   ", proceso = "")
        assertEquals(setOf("tipo", "proceso"), e.keys)
    }

    @Test
    fun `mensajes de error por campo`() {
        val e = validar(tipo = null, proceso = null, foto = null)
        assertEquals("Selecciona el tipo de reporte.", e["tipo"])
        assertEquals("Selecciona el proceso asociado.", e["proceso"])
        assertEquals("Toma o selecciona una foto del documento.", e["foto"])
    }

    @Test
    fun `la foto debe estar verificada`() {
        val e = validar(verificada = false)
        assertEquals(setOf("foto"), e.keys)
        assertEquals("Abre la foto y confirma «Se ve bien».", e["foto"])
    }

    @Test
    fun `observaciones son opcionales`() {
        assertFalse("observaciones" in validar(obs = ""))
    }

    @Test
    fun `observaciones aceptan hasta 300 caracteres`() {
        assertTrue(validar(obs = "x".repeat(Validacion.MAX_OBSERVACIONES)).isEmpty())
    }

    @Test
    fun `observaciones con 301 caracteres dan error`() {
        val e = validar(obs = "x".repeat(Validacion.MAX_OBSERVACIONES + 1))
        assertEquals("Máximo 300 caracteres.", e["observaciones"])
    }
}
