package cl.vitairos.captura.data.model

/**
 * Estados de sincronización acordados con VITAIROS (cuestionario §6).
 * Se persisten por nombre en Room. La UI siempre los muestra con texto + ícono (nunca solo color).
 */
enum class ReportStatus(val label: String, val description: String) {
    BORRADOR("Borrador", "Guardado en el dispositivo; aún no se envía."),
    EN_COLA("En cola", "Listo para enviarse cuando exista conexión."),
    ENVIANDO("Enviando", "Transmitiendo a la API de VITAIROS (mock)."),
    SINCRONIZADO("Sincronizado", "Recibido por VITAIROS; queda como registro de trazabilidad."),
    ERROR("Error", "El envío falló. Corrige o usa Reintentar."),
    REQUIERE_CORRECCION("Requiere corrección", "El supervisor solicitó corregir; genera una nueva versión.");

    /** Se puede editar mientras no haya comenzado efectivamente su envío. */
    val isEditable: Boolean get() = this == BORRADOR || this == EN_COLA || this == ERROR

    /** Se puede eliminar (con confirmación) solo antes de sincronizar. */
    val isDeletable: Boolean get() = this == BORRADOR || this == EN_COLA

    /** Pendiente de llegar a VITAIROS. */
    val isPending: Boolean get() = this == EN_COLA || this == ENVIANDO || this == ERROR

    companion object {
        val editableNames: List<String> = entries.filter { it.isEditable }.map { it.name }
    }
}

enum class AttachmentSource(val label: String) {
    CAMARA("Cámara"),
    GALERIA("Galería"),
    PDF("PDF"),
    SINTETICO("Sintético"),
}

enum class UserRole(val label: String) {
    OPERADOR("Operador"),
    SUPERVISOR("Supervisor"),
}

/** Estado de procesamiento posterior en VITAIROS Industrial AI. SIEMPRE simulado en este MVP. */
enum class ProcessingStatus(val label: String) {
    EN_REVISION("En revisión"),
    PROCESADO("Procesado"),
}
