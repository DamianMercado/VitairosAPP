package cl.vitairos.captura.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.vitairos.captura.data.files.AttachmentStorage
import cl.vitairos.captura.data.files.ImportResult
import cl.vitairos.captura.data.files.PdfUtils
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.testutil.TestData
import cl.vitairos.captura.testutil.TestFilesProvider
import cl.vitairos.captura.testutil.assumeRutasUnix
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Validaciones objetivas de adjuntos (formato, tamaño, cantidad, PDF) y manejo de archivos. */
@RunWith(AndroidJUnit4::class)
class AttachmentRulesStorageTest {

    private lateinit var context: Context
    private lateinit var storage: AttachmentStorage
    private lateinit var origen: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = AttachmentStorage(context)
        origen = File(context.cacheDir, "origen").apply { mkdirs() }
    }

    private fun importar(uri: Uri, source: AttachmentSource = AttachmentSource.GALERIA, count: Int = 0) =
        runBlocking { storage.import("R-1", uri, source, count) }

    private fun motivo(r: ImportResult) = (r as? ImportResult.Rejected)?.reason
        ?: throw AssertionError("Se esperaba rechazo y se obtuvo: $r")

    private fun archivosDelReporte() = File(context.filesDir, "attachments/R-1").listFiles()?.toList().orEmpty()

    @Test
    fun `rechaza formatos no permitidos`() {
        val txt = TestData.texto(origen, "notas.txt")
        assertEquals(
            "Formato no permitido (text/plain). Solo se aceptan JPG, PNG o PDF.",
            motivo(importar(TestFilesProvider.registrar(context, txt, "text/plain"))),
        )
    }

    @Test
    fun `rechaza archivo vacio, mayor a 10 MB y un sexto adjunto`() {
        val vacio = File(origen, "vacio.jpg").apply { writeBytes(ByteArray(0)) }
        assertEquals("El archivo está vacío.", motivo(importar(TestFilesProvider.registrar(context, vacio, "image/jpeg"))))

        val pesado = TestData.texto(origen, "pesado.pdf", "%PDF-1.4")
        val uriPesado = TestFilesProvider.registrar(context, pesado, "application/pdf", tamano = 11L * 1024 * 1024)
        assertEquals("El archivo pesa 11.0 MB y supera el máximo de 10 MB.", motivo(importar(uriPesado)))

        val pdf = TestData.pdf(origen, "ok.pdf")
        assertEquals(
            "Máximo 5 adjuntos por reporte.",
            motivo(importar(TestFilesProvider.registrar(context, pdf, "application/pdf"), count = 5)),
        )
        assertTrue("nada se guarda cuando se rechaza", archivosDelReporte().isEmpty())
    }

    @Test
    fun `el selector de PDF solo acepta PDF`() {
        val jpg = TestData.imagen(origen, "foto.jpg")
        assertEquals(
            "Selecciona un archivo PDF.",
            motivo(importar(TestFilesProvider.registrar(context, jpg, "image/jpeg"), AttachmentSource.PDF)),
        )
    }

    @Test
    fun `un archivo con extension pdf pero sin cabecera PDF se rechaza y no queda guardado`() {
        val falso = TestData.pdf(origen, "falso.pdf", valido = false)
        // Sin tamaño informado por el proveedor: se mide leyendo el archivo
        val uri = TestFilesProvider.registrar(context, falso, null, tamano = null)
        assertEquals("El archivo no es un PDF válido.", motivo(importar(uri, AttachmentSource.PDF)))
        assertTrue(archivosDelReporte().isEmpty())
    }

    @Test
    fun `un PDF con cabecera valida se copia o se informa si no puede abrirse`() {
        val pdf = TestData.pdf(origen, "informe.pdf")
        when (val r = importar(TestFilesProvider.registrar(context, pdf, "application/pdf"), AttachmentSource.PDF)) {
            is ImportResult.Success -> {
                val att = r.attachment
                assertEquals("application/pdf", att.mimeType)
                assertEquals("informe.pdf", att.displayName)
                assertTrue(att.localPath!!.endsWith(".pdf"))
                assertEquals(pdf.length(), File(att.localPath!!).length())
                assertTrue(att.pageCount != null)
            }
            is ImportResult.Rejected ->
                assertEquals("No se pudo abrir el PDF (dañado o protegido con contraseña).", r.reason)
        }
    }

    @Test
    fun `un archivo que no puede leerse se rechaza sin cerrar la app`() {
        val inexistente = Uri.parse("content://no.existe/archivo.jpg")
        val r = importar(inexistente)
        assertTrue(r is ImportResult.Rejected)
    }

    @Test
    fun `utilidades de archivos y PDF`() {
        val dir = File(context.filesDir, "attachments/R-9").apply { mkdirs() }
        val f = File(dir, "a.jpg").apply { writeBytes(ByteArray(100)) }
        assertTrue(storage.fileExists(f.path))
        assertFalse(storage.fileExists(null))
        assertTrue(storage.totalStoredBytes() >= 100)
        storage.deleteFile(null)
        storage.deleteFile(f.path)
        assertFalse(f.exists())
        File(dir, "b.jpg").writeBytes(ByteArray(10))
        storage.deleteReportFiles("R-9")
        assertFalse(dir.exists())

        val noExiste = File(context.filesDir, "no_existe.pdf")
        assertNull(PdfUtils.pageCount(noExiste))
        assertNull(PdfUtils.renderPage(noExiste, 0, 400))
    }

    @Test
    fun `la camara usa un archivo temporal compartido por FileProvider`() {
        assumeRutasUnix()
        val uri = storage.createCameraTarget()
        assertEquals("content", uri.scheme)
        assertTrue(uri.authority!!.endsWith(".fileprovider"))
        assertTrue(uri.path!!.contains("captura_"))
        val tmp = File(context.cacheDir, "camera/temporal.jpg").apply { writeBytes(ByteArray(5)) }
        storage.clearCameraCache()
        assertFalse(tmp.exists())
    }
}
