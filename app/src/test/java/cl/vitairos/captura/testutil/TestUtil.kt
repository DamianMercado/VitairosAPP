package cl.vitairos.captura.testutil

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.local.ReportEntity
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.data.model.Catalog
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.DemoUsers
import cl.vitairos.captura.data.model.ReportStatus
import cl.vitairos.captura.data.repo.EditableFields
import cl.vitairos.captura.data.repo.ReportRepository
import cl.vitairos.captura.sync.AppEvents
import cl.vitairos.captura.sync.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.Assume
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Collections
import java.util.UUID
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.random.Random

/** Datos FICTICIOS reutilizados por las pruebas (nunca datos reales de VITAIROS). */
object TestData {
    val operador: DemoUser = DemoUsers.byId("u-op-01")!!
    val operador2: DemoUser = DemoUsers.byId("u-op-02")!!
    val supervisor: DemoUser = DemoUsers.byId("u-sup-01")!!

    fun campos(
        tipo: String = "TURNO",
        proceso: String = "EXT",
        observaciones: String = "Observación de prueba",
        extra: String? = null,
    ) = EditableFields(tipo, proceso, Catalog.areas[0], Catalog.shifts[0], extra, observaciones, qualityConfirmed = true)

    /**
     * Imagen real en formato PNG (escala de grises) generada sin depender de Android ni de java.awt.
     * El nombre puede terminar en .jpg: el tipo MIME lo informa el proveedor, igual que en un teléfono.
     * @param gris valor fijo 0-255; si es null, cada píxel es aleatorio (imagen con detalle, nítida).
     */
    fun imagen(
        dir: File,
        nombre: String,
        ancho: Int = 120,
        alto: Int = 90,
        gris: Int? = null,
        semilla: Int = 1,
    ): File {
        dir.mkdirs()
        val rnd = Random(semilla)
        val file = File(dir, nombre)
        file.writeBytes(png(ancho, alto) { _, _ -> gris ?: rnd.nextInt(0, 256) })
        return file
    }

    /** Codificador PNG mínimo: firma + IHDR + IDAT (zlib) + IEND. */
    private fun png(ancho: Int, alto: Int, pixel: (Int, Int) -> Int): ByteArray {
        val filas = ByteArrayOutputStream()
        for (y in 0 until alto) {
            filas.write(0) // filtro "None"
            for (x in 0 until ancho) filas.write(pixel(x, y))
        }
        val deflater = Deflater()
        deflater.setInput(filas.toByteArray())
        deflater.finish()
        val comprimido = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (!deflater.finished()) comprimido.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()

        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(tipo: String, contenido: ByteArray) {
            val t = tipo.toByteArray(Charsets.US_ASCII)
            data.writeInt(contenido.size)
            data.write(t)
            data.write(contenido)
            val crc = CRC32()
            crc.update(t)
            crc.update(contenido)
            data.writeInt(crc.value.toInt())
        }
        // ancho, alto, profundidad 8 bits, tipo de color 0 (grises), compresión, filtro, entrelazado
        chunk("IHDR", ByteBuffer.allocate(13).putInt(ancho).putInt(alto).put(8).put(0).put(0).put(0).put(0).array())
        chunk("IDAT", comprimido.toByteArray())
        chunk("IEND", ByteArray(0))
        data.flush()
        return out.toByteArray()
    }

    fun pdf(dir: File, nombre: String, valido: Boolean = true): File {
        dir.mkdirs()
        val file = File(dir, nombre)
        if (valido) {
            file.writeText(
                "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                    "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                    "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n" +
                    "trailer<</Root 1 0 R>>\n%%EOF\n"
            )
        } else {
            file.writeText("esto no es un pdf")
        }
        return file
    }

    fun texto(dir: File, nombre: String, contenido: String = "notas de prueba"): File {
        dir.mkdirs()
        return File(dir, nombre).apply { writeText(contenido) }
    }

    /** Adjunto con un archivo real en disco, listo para insertar con el repositorio. */
    fun adjunto(reportId: String, archivo: File, sha: String = UUID.randomUUID().toString(), mime: String = "image/jpeg") =
        AttachmentEntity(
            id = UUID.randomUUID().toString(),
            reportId = reportId,
            displayName = archivo.name,
            mimeType = mime,
            source = AttachmentSource.GALERIA,
            originalSizeBytes = archivo.length(),
            storedSizeBytes = archivo.length(),
            localPath = archivo.absolutePath,
            sha256 = sha,
            createdAt = System.currentTimeMillis(),
        )
}

/** Crea un reporte y lo lleva al estado pedido usando solo la API pública del repositorio. */
suspend fun ReportRepository.crearEnEstado(
    usuario: DemoUser,
    estado: ReportStatus,
    campos: EditableFields = TestData.campos(),
    archivo: File? = null,
): ReportEntity {
    val r = createDraft(usuario, campos)
    if (archivo != null) addAttachment(TestData.adjunto(r.id, archivo))
    if (estado == ReportStatus.BORRADOR) return get(r.id)!!
    enqueue(r.id)
    if (estado == ReportStatus.EN_COLA) return get(r.id)!!
    markSending(r.id, 1)
    when (estado) {
        ReportStatus.ENVIANDO -> Unit
        ReportStatus.ERROR -> markError(r.id, "Falló el envío (prueba)", 1, 3)
        ReportStatus.SINCRONIZADO -> markSynced(r.id, "VIA-TEST0001", "Recibido.", "EN_REVISION", 1)
        ReportStatus.REQUIERE_CORRECCION -> {
            markSynced(r.id, "VIA-TEST0001", "Recibido.", "EN_REVISION", 1)
            markNeedsCorrection(r.id, "Falta la firma", TestData.supervisor)
        }
        else -> Unit
    }
    return get(r.id)!!
}

/**
 * FileProvider (cámara) solo reconoce rutas con «/», como en Android. Al ejecutar las pruebas en
 * Windows las rutas usan «\», así que esas pruebas puntuales se omiten allí (se ejecutan en Linux/macOS/CI).
 */
fun assumeRutasUnix() = Assume.assumeTrue("FileProvider requiere rutas tipo Unix", File.separatorChar == '/')

/** Ejecuta las tareas pendientes del hilo principal (Robolectric en modo PAUSED). */
fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

/** Espera activa (con el hilo principal procesando tareas) hasta que se cumpla la condición. */
fun waitUntil(timeoutMs: Long = 8_000, descripcion: String = "condición", condicion: () -> Boolean) {
    val fin = System.currentTimeMillis() + timeoutMs
    while (true) {
        idleMain()
        if (condicion()) return
        if (System.currentTimeMillis() > fin) throw AssertionError("Tiempo agotado esperando: $descripcion")
        Thread.sleep(10)
    }
}

/** Mantiene suscrito un Flow (necesario para los stateIn con WhileSubscribed de los ViewModels). */
fun <T> Flow<T>.suscribir(): Job = CoroutineScope(Dispatchers.Default).launch { collect { } }

/** Registra los mensajes emitidos por [AppEvents] para verificarlos en las pruebas. */
class EventRecorder(events: AppEvents) {
    private val lista = Collections.synchronizedList(mutableListOf<UiMessage>())
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    init {
        scope.launch { events.messages.collect { lista += it } }
    }

    val mensajes: List<UiMessage> get() = synchronized(lista) { lista.toList() }
    val textos: List<String> get() = mensajes.map { it.text }
    /** true si algún mensaje contiene [fragmento] (espera hasta [timeoutMs] a que llegue). */
    fun contiene(fragmento: String, timeoutMs: Long = 3_000): Boolean {
        val fin = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (textos.any { it.contains(fragmento) }) return true
            if (System.currentTimeMillis() > fin) return false
            idleMain()
            Thread.sleep(10)
        }
    }
    fun detener() = scope.cancel()
}

/** Ejecuta onCleared() del ViewModel como lo haría Android al cerrar la pantalla. */
fun ViewModel.limpiar() {
    val store = ViewModelStore()
    val vm = this
    ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
    })[ViewModel::class.java]
    store.clear()
}

/**
 * ContentProvider de prueba que simula archivos elegidos en la Galería o el selector de PDF:
 * entrega nombre, tamaño y tipo MIME. El contenido se registra con [registrar].
 */
class TestFilesProvider : ContentProvider() {
    data class Archivo(val file: File, val mime: String?, val nombre: String?, val tamano: Long?)

    companion object {
        const val AUTHORITY = "cl.vitairos.captura.testfiles"
        val archivos = Collections.synchronizedMap(mutableMapOf<Uri, Archivo>())
        private var resolverActual: Any? = null

        /** Registra un archivo y devuelve su Uri content://. */
        fun registrar(
            context: Context,
            file: File,
            mime: String?,
            nombre: String? = file.name,
            tamano: Long? = file.length(),
        ): Uri {
            // Cada prueba de Robolectric crea una aplicación nueva: se instala el provider una vez por prueba.
            if (resolverActual !== context.contentResolver) {
                archivos.clear()
                Robolectric.setupContentProvider(TestFilesProvider::class.java, AUTHORITY)
                resolverActual = context.contentResolver
            }
            val uri = Uri.parse("content://$AUTHORITY/${UUID.randomUUID()}/${file.name}")
            archivos[uri] = Archivo(file, mime, nombre, tamano)
            shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
            return uri
        }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val a = archivos[uri] ?: return null
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        cursor.addRow(arrayOf<Any?>(a.nombre, a.tamano))
        return cursor
    }

    override fun getType(uri: Uri): String? = archivos[uri]?.mime
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
