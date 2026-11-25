package cl.vitairos.captura.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import cl.vitairos.captura.data.local.AttachmentEntity
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.domain.AttachmentRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max

sealed interface ImportResult {
    data class Success(val attachment: AttachmentEntity) : ImportResult
    data class Rejected(val reason: String) : ImportResult
}

/**
 * Gestión de adjuntos en almacenamiento PRIVADO de la app (filesDir/attachments/<reportId>/).
 * - Valida formato (JPG/PNG/PDF), tamaño (≤10 MB), archivo vacío y máximo 5 por reporte.
 * - Comprime imágenes (lado mayor ≤ 2048 px, JPEG 85 %) respetando la orientación EXIF.
 * - Calcula SHA-256 del contenido original para detectar reenvíos/duplicados accidentales.
 * - Heurística opcional de calidad (oscuridad / desenfoque): solo ADVIERTE.
 */
class AttachmentStorage(private val context: Context) {

    companion object {
        const val MAX_DIMENSION = 2048
        const val JPEG_QUALITY = 85
    }

    private val resolver get() = context.contentResolver
    private val rootDir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }
    private fun reportDir(reportId: String) = File(rootDir, reportId).apply { mkdirs() }
    private val cameraDir: File get() = File(context.cacheDir, "camera").apply { mkdirs() }

    /** Archivo temporal (cache privada) + Uri de FileProvider para la app de cámara. */
    fun createCameraTarget(): Uri {
        val file = File(cameraDir, "captura_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun clearCameraCache() {
        cameraDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun import(reportId: String, uri: Uri, source: AttachmentSource, currentCount: Int): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                importBlocking(reportId, uri, source, currentCount)
            } catch (e: Exception) {
                ImportResult.Rejected("No se pudo importar el archivo (${e.javaClass.simpleName}).")
            } finally {
                if (source == AttachmentSource.CAMARA) clearCameraCache()
            }
        }

    private fun importBlocking(reportId: String, uri: Uri, source: AttachmentSource, currentCount: Int): ImportResult {
        var name: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        val rawMime = if (source == AttachmentSource.CAMARA) "image/jpeg" else resolver.getType(uri)
        val mime = AttachmentRules.normalizeMime(rawMime, name)
        if (size < 0) size = measure(uri)

        // Las fotos de cámara se validan tras la compresión (las originales pueden superar 10 MB).
        val sizeToValidate = if (source == AttachmentSource.CAMARA) size.coerceAtMost(AttachmentRules.MAX_BYTES) else size
        AttachmentRules.validate(mime, sizeToValidate, currentCount)?.let { return ImportResult.Rejected(it) }
        if (source == AttachmentSource.PDF && mime != "application/pdf") {
            return ImportResult.Rejected("Selecciona un archivo PDF.")
        }

        val id = UUID.randomUUID().toString()
        val sha = resolver.openInputStream(uri)?.use { sha256(it) }
            ?: return ImportResult.Rejected("No se pudo leer el archivo.")

        val stored: File
        var pageCount: Int? = null
        var warning: String? = null

        if (mime == "application/pdf") {
            stored = File(reportDir(reportId), "$id.pdf")
            val input = resolver.openInputStream(uri) ?: return ImportResult.Rejected("No se pudo leer el PDF.")
            input.use { inp -> FileOutputStream(stored).use { out -> inp.copyTo(out) } }
            if (!hasPdfHeader(stored)) {
                stored.delete()
                return ImportResult.Rejected("El archivo no es un PDF válido.")
            }
            pageCount = pdfPageCount(stored)
            if (pageCount == null) {
                stored.delete()
                return ImportResult.Rejected("No se pudo abrir el PDF (dañado o protegido con contraseña).")
            }
        } else {
            val bitmap = decodeScaled(uri)
                ?: return ImportResult.Rejected("No se pudo leer la imagen; el archivo puede estar dañado.")
            val isPng = mime == "image/png"
            stored = File(reportDir(reportId), if (isPng) "$id.png" else "$id.jpg")
            writeBitmap(bitmap, stored, isPng)
            warning = QualityCheck.analyze(bitmap)
            bitmap.recycle()
            if (stored.length() > AttachmentRules.MAX_BYTES) {
                stored.delete()
                return ImportResult.Rejected("La imagen supera 10 MB incluso después de optimizarla.")
            }
        }

        val displayName = name ?: when (source) {
            AttachmentSource.CAMARA -> "foto_${System.currentTimeMillis()}.jpg"
            else -> stored.name
        }
        return ImportResult.Success(
            AttachmentEntity(
                id = id,
                reportId = reportId,
                displayName = displayName,
                mimeType = mime!!,
                source = source,
                originalSizeBytes = size,
                storedSizeBytes = stored.length(),
                localPath = stored.absolutePath,
                sha256 = sha,
                pageCount = pageCount,
                qualityWarning = warning,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** Guarda un documento sintético generado por la app (datos de prueba, nunca reales). */
    suspend fun saveSynthetic(reportId: String, bitmap: Bitmap, fileName: String): AttachmentEntity =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val file = File(reportDir(reportId), "$id.jpg")
            writeBitmap(bitmap, file, png = false)
            val sha = file.inputStream().use { sha256(it) }
            AttachmentEntity(
                id = id, reportId = reportId, displayName = fileName, mimeType = "image/jpeg",
                source = AttachmentSource.SINTETICO, originalSizeBytes = file.length(),
                storedSizeBytes = file.length(), localPath = file.absolutePath, sha256 = sha,
                createdAt = System.currentTimeMillis(),
            )
        }

    /** Rotación 90° (mejora deseable). Escribe un archivo nuevo para invalidar cachés de imagen. */
    suspend fun rotate(attachment: AttachmentEntity): AttachmentEntity? = withContext(Dispatchers.IO) {
        val path = attachment.localPath ?: return@withContext null
        if (!attachment.mimeType.startsWith("image/")) return@withContext null
        val src = BitmapFactory.decodeFile(path) ?: return@withContext null
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)
        val isPng = attachment.mimeType == "image/png"
        val old = File(path)
        val newFile = File(old.parentFile, "${attachment.id}_${System.currentTimeMillis()}.${if (isPng) "png" else "jpg"}")
        writeBitmap(rotated, newFile, isPng)
        if (rotated !== src) rotated.recycle()
        src.recycle()
        old.delete()
        attachment.copy(localPath = newFile.absolutePath, storedSizeBytes = newFile.length())
    }

    fun deleteFile(path: String?) {
        if (path != null) File(path).delete()
    }

    /** Elimina los archivos de un reporte (solo al eliminar un borrador o reporte en cola). */
    fun deleteReportFiles(reportId: String) {
        File(rootDir, reportId).deleteRecursively()
    }

    fun fileExists(path: String?): Boolean = path != null && File(path).exists()

    fun totalStoredBytes(): Long = rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // ---------------------------------------------------------------- utilidades

    private fun measure(uri: Uri): Long =
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
            }
            total
        } ?: 0L

    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        val longest = max(decoded.width, decoded.height)
        val scale = if (longest > MAX_DIMENSION) MAX_DIMENSION.toFloat() / longest else 1f
        if (scale >= 1f && rotation == 0) return decoded

        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (out !== decoded) decoded.recycle()
        return out
    }

    private fun writeBitmap(bitmap: Bitmap, file: File, png: Boolean) {
        FileOutputStream(file).use { out ->
            bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else JPEG_QUALITY, out)
        }
    }

    private fun hasPdfHeader(file: File): Boolean = file.inputStream().use { input ->
        val header = ByteArray(5)
        input.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Utilidades de PDF con el PdfRenderer nativo de Android (sin dependencias externas). */
object PdfUtils {
    fun pageCount(file: File): Int? = try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            renderer.pageCount
        } finally {
            renderer.close()
        }
    } catch (e: Exception) {
        null
    }

    fun renderPage(file: File, pageIndex: Int, targetWidth: Int): Bitmap? = try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            if (pageIndex !in 0 until renderer.pageCount) {
                null
            } else {
                val page = renderer.openPage(pageIndex)
                try {
                    val width = targetWidth.coerceIn(200, 2000)
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
        }
    } catch (e: Exception) {
        null
    }
}

private fun pdfPageCount(file: File): Int? = PdfUtils.pageCount(file)

/** Heurística simple de calidad (mejora opcional). Solo advierte; el usuario decide repetir o confirmar. */
object QualityCheck {
    fun analyze(src: Bitmap): String? {
        if (src.width <= 2 || src.height <= 2) return null
        val w = 256
        val h = (src.height * w.toFloat() / src.width).toInt().coerceIn(3, 1024)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val lum = DoubleArray(px.size) { i ->
            val c = px[i]
            0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
        }
        val mean = lum.average()
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = lum[i - 1] + lum[i + 1] + lum[i - w] + lum[i + w] - 4 * lum[i]
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val variance = if (n > 0) sumSq / n - (sum / n) * (sum / n) else 0.0

        val warnings = buildList {
            if (mean < 60) add("Imagen oscura")
            if (mean > 245) add("Imagen sobreexpuesta")
            if (variance < 40) add("Posible desenfoque")
        }
        return warnings.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
