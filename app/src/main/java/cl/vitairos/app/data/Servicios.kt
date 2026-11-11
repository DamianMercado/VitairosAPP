package cl.vitairos.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/** Preferencias simples (SharedPreferences privadas): sesión y modo demostración. */
class Preferencias(context: Context) {
    private val sp = context.getSharedPreferences("vitairos_prefs", Context.MODE_PRIVATE)

    var usuario: String?
        get() = sp.getString("usuario", null)
        set(v) { sp.edit().putString("usuario", v).apply() }

    var sinConexion: Boolean
        get() = sp.getBoolean("sin_conexion", false)
        set(v) { sp.edit().putBoolean("sin_conexion", v).apply() }

    var fallaServidor: Boolean
        get() = sp.getBoolean("falla_servidor", false)
        set(v) { sp.edit().putBoolean("falla_servidor", v).apply() }
}

/**
 * Fotos en almacenamiento PRIVADO de la app (filesDir/fotos). Se comprimen a JPEG
 * (lado mayor ≤ 1600 px, calidad 85 %) respetando la orientación EXIF.
 */
class Fotos(private val context: Context) : AlmacenFotos {
    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        private const val MAX_LADO = 1600
    }

    private val carpeta: File get() = File(context.filesDir, "fotos").apply { mkdirs() }
    private val carpetaCamara: File get() = File(context.cacheDir, "camera").apply { mkdirs() }

    fun nuevaUriCamara(): Uri {
        val f = File(carpetaCamara, "captura_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    }

    /** Copia y comprime la imagen. Devuelve la ruta guardada o un mensaje de error. */
    suspend fun importar(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(importarBloqueante(uri))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("No se pudo leer la imagen."))
        } finally {
            carpetaCamara.listFiles()?.forEach { it.delete() }
        }
    }

    private fun importarBloqueante(uri: Uri): String {
        val r = context.contentResolver
        var tamano = -1L
        r.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) tamano = c.getLong(0)
        }
        if (tamano == 0L) throw IllegalArgumentException("La imagen está vacía.")
        if (tamano > MAX_BYTES) throw IllegalArgumentException("La imagen supera el máximo de 10 MB.")

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0) throw IllegalArgumentException("El archivo no es una imagen válida.")
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= MAX_LADO) muestra *= 2
        val original = r.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: throw IllegalArgumentException("No se pudo leer la imagen.")

        val giro = try {
            r.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        val escala = MAX_LADO.toFloat() / max(original.width, original.height)
        val matriz = Matrix().apply {
            if (escala < 1f) postScale(escala, escala)
            if (giro != 0) postRotate(giro.toFloat())
        }
        val salida = if (matriz.isIdentity) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)
        val destino = File(carpeta, "${UUID.randomUUID()}.jpg")
        FileOutputStream(destino).use { salida.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (salida !== original) salida.recycle()
        original.recycle()
        return destino.absolutePath
    }

    /** Gira la foto 90° y devuelve la nueva ruta (archivo nuevo para refrescar la vista). */
    suspend fun rotar(ruta: String): String? = withContext(Dispatchers.IO) {
        val src = BitmapFactory.decodeFile(ruta) ?: return@withContext null
        val girada = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)
        val destino = File(carpeta, "${UUID.randomUUID()}.jpg")
        FileOutputStream(destino).use { girada.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (girada !== src) girada.recycle()
        src.recycle()
        File(ruta).delete()
        destino.absolutePath
    }

    override fun borrar(ruta: String?) {
        if (ruta != null) File(ruta).delete()
    }
}

/** API REST SIMULADA de VITAIROS: responde en el mismo teléfono, sin servidor real. */
class ApiSimulada(private val context: Context, private val prefs: Preferencias) : ServicioEnvio {

    override suspend fun enviar(@Suppress("UNUSED_PARAMETER") reporte: Reporte): Respuesta {
        delay(1_200) // latencia de red simulada
        if (prefs.sinConexion || !hayRed()) return Respuesta.SinConexion
        if (prefs.fallaServidor) return Respuesta.Falla("HTTP 500 · Error interno del servidor (simulado)")
        return Respuesta.Exito("VIA-" + UUID.randomUUID().toString().take(8).uppercase())
    }

    private fun hayRed(): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        false
    }
}
