package cl.vitairos.captura.data.files

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Genera documentos SINTÉTICOS (formulario ficticio) para probar la cola con ≥10 reportes
 * sin usar documentación real ni confidencial (cuestionario §4 y §6).
 */
object SyntheticDocs {

    fun render(folio: String, typeName: String, processName: String, area: String?, shift: String?, operator: String): Bitmap {
        val w = 900
        val h = 1270
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 29, 22) }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(8, 29, 22); textSize = 38f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 30); textSize = 24f }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 60, 60); textSize = 22f; typeface = Typeface.MONOSPACE
        }
        val line = Paint().apply { color = Color.rgb(170, 182, 183); strokeWidth = 2f }
        val copper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(221, 122, 82) }

        // Encabezado
        c.drawRect(0f, 0f, w.toFloat(), 120f, dark)
        c.drawCircle(70f, 60f, 18f, copper)
        val headerText = Paint(title).apply { color = Color.rgb(251, 247, 241); textSize = 34f }
        c.drawText("DOCUMENTO SINTÉTICO DE PRUEBA", 110f, 72f, headerText)

        var y = 190f
        c.drawText(typeName.uppercase(Locale.ROOT), 50f, y, title); y += 50f
        c.drawText("Folio: $folio", 50f, y, mono); y += 36f
        val now = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.forLanguageTag("es-CL")).format(Date())
        c.drawText("Fecha: $now", 50f, y, mono); y += 50f

        val rows = listOf(
            "Proceso" to processName,
            "Área / faena" to (area ?: "—"),
            "Turno" to (shift ?: "—"),
            "Responsable (ficticio)" to operator,
        )
        rows.forEach { (k, v) ->
            c.drawLine(50f, y, (w - 50).toFloat(), y, line); y += 40f
            c.drawText(k, 60f, y, text)
            c.drawText(v, 360f, y, text); y += 20f
        }
        c.drawLine(50f, y, (w - 50).toFloat(), y, line); y += 60f

        c.drawText("Mediciones (valores aleatorios de prueba)", 50f, y, title.apply { textSize = 28f }); y += 40f
        repeat(8) { i ->
            c.drawLine(50f, y, (w - 50).toFloat(), y, line); y += 38f
            c.drawText("Ítem ${i + 1}", 60f, y, text)
            c.drawText("%.2f".format(Locale.US, Random.nextDouble(10.0, 999.0)), 360f, y, mono)
            c.drawText(if (Random.nextBoolean()) "OK" else "Revisar", 640f, y, text)
            y += 16f
        }
        c.drawLine(50f, y, (w - 50).toFloat(), y, line)

        // Firma ficticia + marca de agua
        c.drawText("Firma: ____________________", 50f, (h - 120).toFloat(), text)
        val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 221, 122, 82); textSize = 90f; typeface = Typeface.DEFAULT_BOLD
        }
        c.save()
        c.rotate(-30f, w / 2f, h / 2f)
        c.drawText("SINTÉTICO", 170f, h / 2f, watermark)
        c.restore()
        return bmp
    }
}
