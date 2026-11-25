package cl.vitairos.captura.domain

import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.DemoUsers
import cl.vitairos.captura.data.model.ProcessingStatus
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.model.UserRole
import cl.vitairos.captura.data.prefs.AppSettings
import cl.vitairos.captura.data.prefs.MockApiMode
import cl.vitairos.captura.sync.AppEvents
import cl.vitairos.captura.sync.MessageKind
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pruebas JVM puras (sin Android) de reglas, catálogo, usuarios y estados. */
class ReglasDetalleTest {

    @Test
    fun `normalizeMime acepta alias de jpeg y usa la extension cuando el mime es generico`() {
        assertEquals("image/jpeg", AttachmentRules.normalizeMime("IMAGE/JPG", null))
        assertEquals("image/jpeg", AttachmentRules.normalizeMime("image/pjpeg", "x.bin"))
        assertEquals("image/png", AttachmentRules.normalizeMime(" image/png ", null))
        assertEquals("application/pdf", AttachmentRules.normalizeMime("application/octet-stream", "Informe.PDF"))
        assertEquals("image/jpeg", AttachmentRules.normalizeMime(null, "foto.jpeg"))
        assertEquals("image/png", AttachmentRules.normalizeMime("", "captura.png"))
        assertEquals("text/plain", AttachmentRules.normalizeMime("text/plain", "notas.txt"))
        assertEquals("application/octet-stream", AttachmentRules.normalizeMime("application/octet-stream", "archivo.zip"))
        assertNull(AttachmentRules.normalizeMime(null, null))
        assertNull(AttachmentRules.normalizeMime(null, "sin_extension"))
    }

    @Test
    fun `validate entrega el motivo exacto del rechazo`() {
        assertEquals("Máximo 5 adjuntos por reporte.", AttachmentRules.validate("image/png", 10, 5))
        assertEquals(
            "Formato no permitido. Solo se aceptan JPG, PNG o PDF.",
            AttachmentRules.validate(null, 10, 0),
        )
        assertEquals(
            "Formato no permitido (image/gif). Solo se aceptan JPG, PNG o PDF.",
            AttachmentRules.validate("image/gif", 10, 0),
        )
        assertEquals("El archivo está vacío.", AttachmentRules.validate("application/pdf", 0, 0))
        assertEquals(
            "El archivo pesa 12.0 MB y supera el máximo de 10 MB.",
            AttachmentRules.validate("image/jpeg", 12L * 1024 * 1024, 0),
        )
        assertNull(AttachmentRules.validate("image/jpeg", AttachmentRules.MAX_BYTES, 4))
    }

    @Test
    fun `formatBytes usa B, KB y MB`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("1.5 MB", formatBytes(1536L * 1024))
    }

    @Test
    fun `etiqueta de adjuntos en singular y plural`() {
        assertEquals("Sin archivos adjuntos", attachmentCountLabel(0))
        assertEquals("1 archivo adjunto", attachmentCountLabel(1))
        assertEquals("5 archivos adjuntos", attachmentCountLabel(5))
    }

    @Test
    fun `separador cronologico para fechas futuras, recientes y antiguas`() {
        val hoy = LocalDate.of(2026, 11, 24)
        assertEquals("Hoy", dayGroupLabel(hoy.plusDays(1), hoy))
        assertEquals("Hace 6 días", dayGroupLabel(hoy.minusDays(6), hoy))
        assertEquals("17-11-2026", dayGroupLabel(hoy.minusDays(7), hoy))
    }

    @Test
    fun `cooldown informa hasta cuando esta bloqueado y libera la clave`() {
        val c = Cooldown(1_000)
        assertNull(c.blockedUntil("A", 0))
        assertTrue(c.tryAcquire("A", 100))
        assertEquals(1_100L, c.blockedUntil("A", 500))
        assertFalse(c.tryAcquire("A", 1_099))
        assertTrue(c.tryAcquire("B", 1_099))
        assertNull(c.blockedUntil("A", 1_100))
        assertTrue(c.tryAcquire("A", 1_100))
        // Valor por defecto: 5 s
        val porDefecto = Cooldown()
        assertTrue(porDefecto.tryAcquire("X", 0))
        assertFalse(porDefecto.tryAcquire("X", 4_999))
    }

    @Test
    fun `validacion de contexto exige area, turno y limite de observaciones`() {
        val base = ReportForm("TURNO", "EXT", null, null, null, "x".repeat(501), 1, true)
        val e = ReportValidation.validateContext(base)
        assertEquals(setOf("area", "shift", "observations"), e.keys)
        assertEquals("El área/faena es obligatoria para Reporte de turno.", e["area"])
        assertEquals("Máximo 500 caracteres.", e["observations"])

        val prod = ReportForm("PROD", "CHA", null, "Turno A", null, "", 1, true)
        assertTrue("PROD no exige área", ReportValidation.validateContext(prod).isEmpty())

        val inc = ReportForm("INC", "SSO", "Rajo Norte", "Turno A", " ", "", 1, true)
        assertEquals("Nivel de severidad es obligatorio.", ReportValidation.validateContext(inc)["extra"])
    }

    @Test
    fun `catalogo cerrado y busquedas por codigo`() {
        assertEquals(5, Catalog.reportTypes.size)
        assertEquals(8, Catalog.processes.size)
        assertEquals(5, Catalog.areas.size)
        assertEquals(2, Catalog.shifts.size)
        assertEquals("Orden de mantención", Catalog.typeName("MANT"))
        assertEquals("Código de equipo", Catalog.type("MANT")?.extraField?.label)
        assertEquals(listOf("Bajo", "Medio", "Alto", "Crítico"), Catalog.type("INC")?.extraField?.options)
        assertEquals("Sin tipo", Catalog.typeName(null))
        assertEquals("Sin proceso", Catalog.processName("XYZ"))
        assertEquals("Flotación", Catalog.processName("FLO"))
        assertNull(Catalog.type(""))
        assertNull(Catalog.process(null))
        // Todos los códigos son únicos
        assertEquals(Catalog.reportTypes.size, Catalog.reportTypes.map { it.code }.toSet().size)
    }

    @Test
    fun `usuarios ficticios guardan solo hash y se autentican sin distinguir mayusculas`() {
        assertEquals(3, DemoUsers.all.size)
        val sup = DemoUsers.authenticate("  SUPERVISOR ", "demo123")
        assertEquals("u-sup-01", sup?.id)
        assertEquals(UserRole.SUPERVISOR, sup?.role)
        assertEquals("Diego Fuentes", DemoUsers.authenticate("operador2", "demo123")?.displayName)
        assertNull(DemoUsers.authenticate("operador1", "DEMO123"))
        assertNull(DemoUsers.authenticate("nadie", "demo123"))
        assertNull(DemoUsers.byId(null))
        DemoUsers.all.forEach { u ->
            assertEquals(64, u.passwordHash.length)
            assertFalse(u.passwordHash.contains("demo123"))
            assertEquals(DemoUsers.hash(u.username, "demo123"), u.passwordHash)
        }
    }

    @Test
    fun `estados con etiqueta y reglas de edicion, eliminacion y pendiente`() {
        assertEquals(listOf("BORRADOR", "EN_COLA", "ERROR"), ReportStatus.editableNames)
        val pendientes = ReportStatus.entries.filter { it.isPending }
        assertEquals(listOf(ReportStatus.EN_COLA, ReportStatus.ENVIANDO, ReportStatus.ERROR), pendientes)
        ReportStatus.entries.forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.description.isNotBlank())
        }
        assertFalse(ReportStatus.REQUIERE_CORRECCION.isEditable)
        assertFalse(ReportStatus.REQUIERE_CORRECCION.isDeletable)
        assertEquals("Cámara", AttachmentSource.CAMARA.label)
        assertEquals("Sintético", AttachmentSource.SINTETICO.label)
        assertEquals("Operador", UserRole.OPERADOR.label)
        assertEquals("Procesado", ProcessingStatus.PROCESADO.label)
        assertEquals("En revisión", ProcessingStatus.EN_REVISION.label)
    }

    @Test
    fun `ajustes por defecto y modos de la API simulada`() {
        val s = AppSettings()
        assertFalse(s.loaded)
        assertNull(s.userId)
        assertFalse(s.simulateOffline)
        assertEquals(MockApiMode.NORMAL, s.mockApiMode)
        assertEquals(3, MockApiMode.entries.size)
        MockApiMode.entries.forEach { assertTrue(it.label.isNotBlank() && it.description.isNotBlank()) }
    }

    @Test
    fun `eventos de la app se emiten con su tipo`() = runTest(UnconfinedTestDispatcher()) {
        val events = AppEvents()
        val recibidos = mutableListOf<MessageKind>()
        val job = launch { events.messages.take(4).toList().forEach { recibidos += it.kind } }
        assertTrue(events.info("i"))
        assertTrue(events.success("s"))
        assertTrue(events.warning("w"))
        assertTrue(events.error("e"))
        job.join()
        assertEquals(listOf(MessageKind.INFO, MessageKind.SUCCESS, MessageKind.WARNING, MessageKind.ERROR), recibidos)
    }

    @Test
    fun `politica de reintentos expone el maximo`() {
        assertEquals(3, RetryPolicy.maxAutoRetries)
        assertEquals(5_000L, RetryPolicy.MANUAL_COOLDOWN_MS)
        assertNotNull(RetryPolicy.nextDelaySeconds(2))
        assertNull(RetryPolicy.nextDelaySeconds(-1))
    }
}
