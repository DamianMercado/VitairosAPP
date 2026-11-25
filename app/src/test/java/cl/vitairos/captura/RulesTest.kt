package cl.vitairos.captura

import cl.vitairos.captura.data.model.DemoUsers
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.repo.ReportRepository
import cl.vitairos.captura.domain.AttachmentRules
import cl.vitairos.captura.domain.Cooldown
import cl.vitairos.captura.domain.attachmentCountLabel
import cl.vitairos.captura.domain.dayGroupLabel
import cl.vitairos.captura.domain.ReportForm
import cl.vitairos.captura.domain.ReportValidation
import cl.vitairos.captura.domain.RetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Evidencia de pruebas de las reglas acordadas con VITAIROS (cuestionario §4, §5, §6, §8). */
class RulesTest {

    // ---------------- Reintentos (§6)
    @Test
    fun `politica de reintentos es 5, 15 y 30 segundos y luego reintento manual`() {
        assertEquals(3, RetryPolicy.maxAutoRetries)
        assertEquals(5, RetryPolicy.nextDelaySeconds(0))
        assertEquals(15, RetryPolicy.nextDelaySeconds(1))
        assertEquals(30, RetryPolicy.nextDelaySeconds(2))
        assertNull(RetryPolicy.nextDelaySeconds(3))
    }

    // ---------------- Estados (§3 y §6)
    @Test
    fun `solo borrador y en cola se pueden eliminar`() {
        assertTrue(ReportStatus.BORRADOR.isDeletable)
        assertTrue(ReportStatus.EN_COLA.isDeletable)
        assertFalse(ReportStatus.ENVIANDO.isDeletable)
        assertFalse(ReportStatus.SINCRONIZADO.isDeletable)
        assertFalse(ReportStatus.ERROR.isDeletable)
    }

    @Test
    fun `no se edita durante el envio ni despues de sincronizar`() {
        assertTrue(ReportStatus.BORRADOR.isEditable)
        assertTrue(ReportStatus.EN_COLA.isEditable)
        assertTrue(ReportStatus.ERROR.isEditable)
        assertFalse(ReportStatus.ENVIANDO.isEditable)
        assertFalse(ReportStatus.SINCRONIZADO.isEditable)
        assertFalse(ReportStatus.REQUIERE_CORRECCION.isEditable) // se corrige con nueva versión
    }

    // ---------------- Adjuntos (§4)
    @Test
    fun `adjuntos validos JPG PNG PDF hasta 10 MB`() {
        assertNull(AttachmentRules.validate("image/jpeg", 2_000_000, 0))
        assertNull(AttachmentRules.validate("image/png", 1, 4))
        assertNull(AttachmentRules.validate("application/pdf", AttachmentRules.MAX_BYTES, 0))
    }

    @Test
    fun `se rechazan formato, vacio, tamano excedido y sexto adjunto`() {
        assertNotNull(AttachmentRules.validate("image/heic", 1000, 0))
        assertNotNull(AttachmentRules.validate("image/jpeg", 0, 0))
        assertNotNull(AttachmentRules.validate("application/pdf", AttachmentRules.MAX_BYTES + 1, 0))
        assertNotNull(AttachmentRules.validate("image/jpeg", 1000, 5))
    }

    @Test
    fun `mime se normaliza por extension cuando falta`() {
        assertEquals("image/jpeg", AttachmentRules.normalizeMime(null, "foto.JPG"))
        assertEquals("application/pdf", AttachmentRules.normalizeMime("application/octet-stream", "doc.pdf"))
        assertEquals("image/jpeg", AttachmentRules.normalizeMime("image/jpg", null))
    }

    // ---------------- Metadata (§5)
    private val valid = ReportForm(
        typeCode = "TURNO", processCode = "CHA", area = "Rajo Norte", shift = "Turno A · Día (08:00–20:00)",
        extra = null, observations = "", attachmentCount = 1, qualityConfirmed = true,
    )

    @Test
    fun `reporte completo pasa validacion`() {
        assertTrue(ReportValidation.validateForQueue(valid).isEmpty())
    }

    @Test
    fun `tipo y proceso son obligatorios`() {
        val errors = ReportValidation.validateForQueue(valid.copy(typeCode = null, processCode = null))
        assertTrue("type" in errors)
        assertTrue("process" in errors)
    }

    @Test
    fun `campos condicionales segun tipo`() {
        // Mantención exige código de equipo; incidente exige severidad.
        assertTrue("extra" in ReportValidation.validateContext(valid.copy(typeCode = "MANT")))
        assertTrue(ReportValidation.validateContext(valid.copy(typeCode = "MANT", extra = "CAEX-114")).isEmpty())
        // Parte de producción no exige área.
        assertTrue(ReportValidation.validateContext(valid.copy(typeCode = "PROD", area = null)).isEmpty())
    }

    @Test
    fun `se exige al menos un adjunto y confirmacion de revision`() {
        val errors = ReportValidation.validateForQueue(valid.copy(attachmentCount = 0, qualityConfirmed = false))
        assertTrue("attachments" in errors)
        assertTrue("quality" in errors)
    }

    // ---------------- Usuarios ficticios (§2 y §8)
    @Test
    fun `login local con usuarios ficticios`() {
        assertNotNull(DemoUsers.authenticate("operador1", "demo123"))
        assertNotNull(DemoUsers.authenticate(" SUPERVISOR ", "demo123"))
        assertNull(DemoUsers.authenticate("operador1", "otra"))
        assertNull(DemoUsers.authenticate("desconocido", "demo123"))
    }

    @Test
    fun `folio tiene formato legible`() {
        val folio = ReportRepository.newFolio(System.currentTimeMillis())
        assertTrue(folio, Regex("VTR-\\d{6}-[0-9A-F]{4}").matches(folio))
    }

    // ---------------- Ajustes v0.2 (correcciones solicitadas)
    @Test
    fun `tarjeta muestra solo el total de adjuntos`() {
        assertEquals("Sin archivos adjuntos", attachmentCountLabel(0))
        assertEquals("1 archivo adjunto", attachmentCountLabel(1))
        assertEquals("3 archivos adjuntos", attachmentCountLabel(3))
    }

    @Test
    fun `agrupacion cronologica hoy ayer y hace N dias`() {
        val hoy = LocalDate.of(2026, 10, 9)
        assertEquals("Hoy", dayGroupLabel(hoy, hoy))
        assertEquals("Ayer", dayGroupLabel(hoy.minusDays(1), hoy))
        assertEquals("Hace 2 días", dayGroupLabel(hoy.minusDays(2), hoy))
        assertEquals("Hace 6 días", dayGroupLabel(hoy.minusDays(6), hoy))
        assertEquals("02-10-2026", dayGroupLabel(hoy.minusDays(7), hoy))
    }

    @Test
    fun `antirrebote de reintento bloquea 5 segundos por reporte`() {
        val c = Cooldown(RetryPolicy.MANUAL_COOLDOWN_MS)
        assertTrue(c.tryAcquire("R1", 1_000))
        assertFalse(c.tryAcquire("R1", 3_000))      // segundo clic a los 2 s: ignorado
        assertTrue(c.tryAcquire("R2", 3_000))       // otro reporte no se ve afectado
        assertEquals(6_000L, c.blockedUntil("R1", 3_000))
        assertTrue(c.tryAcquire("R1", 6_000))       // pasados 5 s vuelve a estar disponible
        assertNull(c.blockedUntil("R2", 9_000))
    }
}
