package cl.vitairos.captura.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.vitairos.captura.data.files.AttachmentStorage
import cl.vitairos.captura.data.files.ImportResult
import cl.vitairos.captura.data.files.QualityCheck
import cl.vitairos.captura.data.files.SyntheticDocs
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.testutil.TestData
import cl.vitairos.captura.testutil.TestFilesProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Importación de imágenes (compresión, orientación, hash, calidad) con gráficos nativos de Robolectric. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentStorageTest {

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

    private fun exito(r: ImportResult) = (r as? ImportResult.Success)?.attachment
        ?: throw AssertionError("Se esperaba éxito y se obtuvo: $r")

    private fun rechazo(r: ImportResult) = (r as? ImportResult.Rejected)?.reason
        ?: throw AssertionError("Se esperaba rechazo y se obtuvo: $r")

    @Test
    fun `importa un JPG de la galeria en almacenamiento privado con hash`() {
        val jpg = TestData.imagen(origen, "faena.jpg")
        val att = exito(importar(TestFilesProvider.registrar(context, jpg, "image/jpeg")))
        assertEquals("R-1", att.reportId)
        assertEquals("faena.jpg", att.displayName)
        assertEquals("image/jpeg", att.mimeType)
        assertEquals(AttachmentSource.GALERIA, att.source)
        assertEquals(jpg.length(), att.originalSizeBytes)
        assertEquals(64, att.sha256.length)
        val guardado = File(att.localPath!!)
        assertTrue(guardado.exists())
        assertTrue(guardado.path.contains("attachments${File.separator}R-1"))
        assertTrue(guardado.name.endsWith(".jpg"))
        assertEquals(guardado.length(), att.storedSizeBytes)
        assertTrue(storage.fileExists(att.localPath))
        assertTrue(storage.totalStoredBytes() >= guardado.length())
        assertNull("una imagen con detalle no genera advertencia", att.qualityWarning)
    }

    @Test
    fun `el mismo archivo produce el mismo hash y un PNG se guarda como PNG`() {
        val png = TestData.imagen(origen, "plano.png")
        val a = exito(importar(TestFilesProvider.registrar(context, png, "image/png")))
        val b = exito(importar(TestFilesProvider.registrar(context, png, null, nombre = "copia.png")))
        assertEquals(a.sha256, b.sha256)
        assertNotEquals(a.id, b.id)
        assertEquals("image/png", b.mimeType)
        assertTrue(a.localPath!!.endsWith(".png"))
    }

    @Test
    fun `imagenes grandes se reducen a 2048 px por el lado mayor`() {
        val grande = TestData.imagen(origen, "panoramica.jpg", ancho = 4400, alto = 300)
        val att = exito(importar(TestFilesProvider.registrar(context, grande, "image/jpeg")))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(att.localPath, bounds)
        assertEquals(AttachmentStorage.MAX_DIMENSION, maxOf(bounds.outWidth, bounds.outHeight))
    }

    @Test
    fun `foto de camara sin nombre recibe uno automatico y se limpia la cache`() {
        val camaraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val foto = TestData.imagen(camaraDir, "captura_1.jpg")
        val tamano = foto.length()
        val uri = Uri.fromFile(foto)
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { foto.inputStream() }
        val att = exito(importar(uri, AttachmentSource.CAMARA))
        assertTrue(att.displayName.startsWith("foto_"))
        assertEquals(AttachmentSource.CAMARA, att.source)
        assertEquals(tamano, att.originalSizeBytes)
        assertFalse("la foto temporal se elimina", foto.exists())
    }

    @Test
    fun `imagen oscura recibe advertencia de calidad`() {
        val oscura = TestData.imagen(origen, "oscura.jpg", gris = 10)
        val att = exito(importar(TestFilesProvider.registrar(context, oscura, "image/jpeg")))
        assertTrue(att.qualityWarning!!.contains("Imagen oscura"))
    }

    @Test
    fun `una imagen danada se rechaza`() {
        val falsa = TestData.texto(origen, "rota.jpg", "no es una imagen")
        val motivo = rechazo(importar(TestFilesProvider.registrar(context, falsa, "image/jpeg")))
        assertEquals("No se pudo leer la imagen; el archivo puede estar dañado.", motivo)
    }

    @Test
    fun `rotar crea un archivo nuevo girado 90 grados`() = runBlocking {
        val jpg = TestData.imagen(origen, "horizontal.jpg", ancho = 160, alto = 80)
        val att = exito(importar(TestFilesProvider.registrar(context, jpg, "image/jpeg")))
        val rotado = storage.rotate(att)!!
        assertNotEquals(att.localPath, rotado.localPath)
        assertFalse(File(att.localPath!!).exists())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(rotado.localPath, bounds)
        assertEquals(80, bounds.outWidth)
        assertEquals(160, bounds.outHeight)
        assertEquals(File(rotado.localPath!!).length(), rotado.storedSizeBytes)

        assertNull("un PDF no se rota", storage.rotate(att.copy(mimeType = "application/pdf")))
        assertNull(storage.rotate(att.copy(localPath = null)))
    }

    @Test
    fun `analisis de calidad detecta oscuridad, sobreexposicion y desenfoque`() {
        fun plano(color: Int) = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        assertEquals("Imagen oscura · Posible desenfoque", QualityCheck.analyze(plano(Color.BLACK)))
        assertEquals("Imagen sobreexpuesta · Posible desenfoque", QualityCheck.analyze(plano(Color.WHITE)))
        assertEquals("Posible desenfoque", QualityCheck.analyze(plano(Color.GRAY)))
        assertNull("muy pequeña para analizar", QualityCheck.analyze(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)))

        val ajedrez = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (x in 0 until 256) for (y in 0 until 256) {
            ajedrez.setPixel(x, y, if ((x + y) % 2 == 0) Color.rgb(40, 40, 40) else Color.rgb(220, 220, 220))
        }
        assertNull("imagen nítida y bien expuesta", QualityCheck.analyze(ajedrez))
    }

    @Test
    fun `documento sintetico y su guardado`() = runBlocking {
        val bmp = SyntheticDocs.render("VTR-261124-TEST", "Reporte de turno", "Chancado", null, null, "Camila Rojas")
        assertEquals(900, bmp.width)
        assertEquals(1270, bmp.height)
        val att = storage.saveSynthetic("R-2", bmp, "sintetico.jpg")
        assertEquals(AttachmentSource.SINTETICO, att.source)
        assertEquals("image/jpeg", att.mimeType)
        assertTrue(File(att.localPath!!).length() > 0)
        assertNotNull(BitmapFactory.decodeFile(att.localPath))
    }
}
