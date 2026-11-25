package cl.vitairos.captura.domain

import cl.vitairos.captura.data.model.Catalog

/**
 * Reglas de negocio puras (sin Android) → fácilmente testeables con JUnit.
 */

/** Política de reintentos automáticos (cuestionario §6): 3 reintentos con backoff 5 s, 15 s, 30 s. */
object RetryPolicy {
    val backoffSeconds: List<Int> = listOf(5, 15, 30)
    val maxAutoRetries: Int get() = backoffSeconds.size

    /** Espera mínima entre dos reintentos MANUALES del mismo reporte (antirrebote del botón). */
    const val MANUAL_COOLDOWN_MS: Long = 5_000L

    /**
     * @param retriesUsed reintentos automáticos ya consumidos en el ciclo actual.
     * @return segundos de espera antes del próximo reintento, o null si se agotaron (→ Error + reintento manual).
     */
    fun nextDelaySeconds(retriesUsed: Int): Int? = backoffSeconds.getOrNull(retriesUsed)
}

/** Validaciones objetivas de adjuntos: si fallan, se impide continuar (cuestionario §4). */
object AttachmentRules {
    const val MAX_FILES = 5
    const val MAX_BYTES: Long = 10L * 1024 * 1024
    val allowedMimeTypes = setOf("image/jpeg", "image/png", "application/pdf")

    fun normalizeMime(mime: String?, fileName: String?): String? {
        val m = mime?.lowercase()?.trim()
        if (m == "image/jpg" || m == "image/pjpeg") return "image/jpeg"
        if (!m.isNullOrBlank() && m != "application/octet-stream") return m
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            else -> m
        }
    }

    /** @return mensaje de rechazo o null si es válido. */
    fun validate(mime: String?, sizeBytes: Long, currentCount: Int): String? = when {
        currentCount >= MAX_FILES -> "Máximo $MAX_FILES adjuntos por reporte."
        mime == null || mime !in allowedMimeTypes ->
            "Formato no permitido${mime?.let { " ($it)" } ?: ""}. Solo se aceptan JPG, PNG o PDF."
        sizeBytes <= 0L -> "El archivo está vacío."
        sizeBytes > MAX_BYTES -> "El archivo pesa ${formatBytes(sizeBytes)} y supera el máximo de 10 MB."
        else -> null
    }
}

/** Datos mínimos del formulario para validar. */
data class ReportForm(
    val typeCode: String?,
    val processCode: String?,
    val area: String?,
    val shift: String?,
    val extra: String?,
    val observations: String,
    val attachmentCount: Int,
    val qualityConfirmed: Boolean,
)

object ReportValidation {
    const val MAX_OBSERVATIONS = 500

    /** Paso 1 (contexto). Devuelve mapa campo → mensaje. */
    fun validateContext(f: ReportForm): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        val type = Catalog.type(f.typeCode)
        if (type == null) errors["type"] = "Selecciona el tipo de reporte."
        if (Catalog.process(f.processCode) == null) errors["process"] = "Selecciona el proceso asociado."
        if (type != null) {
            if (type.requiresArea && f.area.isNullOrBlank()) errors["area"] = "El área/faena es obligatoria para ${type.name}."
            if (type.requiresShift && f.shift.isNullOrBlank()) errors["shift"] = "El turno es obligatorio para ${type.name}."
            val extra = type.extraField
            if (extra != null && f.extra.isNullOrBlank()) errors["extra"] = "${extra.label} es obligatorio."
        }
        if (f.observations.length > MAX_OBSERVATIONS) errors["observations"] = "Máximo $MAX_OBSERVATIONS caracteres."
        return errors
    }

    /** Validación completa antes de dejar el reporte en cola. */
    fun validateForQueue(f: ReportForm): Map<String, String> {
        val errors = LinkedHashMap(validateContext(f))
        if (f.attachmentCount < 1) errors["attachments"] = "Adjunta al menos un documento."
        if (!f.qualityConfirmed) errors["quality"] = "Confirma que revisaste la vista previa de los documentos."
        return errors
    }
}

/** «Sin archivos adjuntos», «1 archivo adjunto», «3 archivos adjuntos». */
fun attachmentCountLabel(count: Int): String = when (count) {
    0 -> "Sin archivos adjuntos"
    1 -> "1 archivo adjunto"
    else -> "$count archivos adjuntos"
}

/**
 * Etiqueta del separador cronológico del inicio: «Hoy», «Ayer», «Hace N días» (hasta 6)
 * y, para fechas anteriores, la fecha en formato dd-MM-yyyy.
 */
fun dayGroupLabel(date: java.time.LocalDate, today: java.time.LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> "Hoy"
        days == 1L -> "Ayer"
        days < 7L -> "Hace $days días"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    }
}

/**
 * Antirrebote (cooldown) para acciones de reintento: una misma clave solo se acepta
 * una vez cada [windowMs] milisegundos. Evita la saturación de clics y peticiones simultáneas.
 */
class Cooldown(private val windowMs: Long = 5_000L) {
    private val lastAccepted = mutableMapOf<String, Long>()

    /** @return true si la acción puede ejecutarse ahora (y registra el uso). */
    @Synchronized
    fun tryAcquire(key: String, nowMs: Long): Boolean {
        val last = lastAccepted[key]
        if (last != null && nowMs - last < windowMs) return false
        lastAccepted[key] = nowMs
        return true
    }

    /** Instante (ms) hasta el cual la clave sigue bloqueada, o null si está libre. */
    @Synchronized
    fun blockedUntil(key: String, nowMs: Long): Long? =
        lastAccepted[key]?.plus(windowMs)?.takeIf { it > nowMs }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
