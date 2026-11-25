package cl.vitairos.captura.data.model

/**
 * Catálogo CERRADO de tipos de reporte, procesos, áreas y turnos.
 *
 * Es un catálogo de demostración: VITAIROS entregará/validará el listado oficial antes de la
 * validación final (cuestionario §4). Para reemplazarlo basta con editar este archivo.
 */
data class ReportType(
    val code: String,
    val name: String,
    val version: String,
    val description: String,
    val requiresArea: Boolean,
    val requiresShift: Boolean,
    val extraField: ExtraField? = null,
)

/** Campo condicional según tipo (variación simple demostrable, cuestionario §5). */
data class ExtraField(
    val label: String,
    val options: List<String>? = null,   // null = texto libre corto
    val hint: String = "",
)

data class OperationalProcess(val code: String, val name: String)

object Catalog {

    val reportTypes: List<ReportType> = listOf(
        ReportType(
            code = "TURNO", name = "Reporte de turno", version = "v1.2",
            description = "Resumen operacional entregado al cierre del turno.",
            requiresArea = true, requiresShift = true,
        ),
        ReportType(
            code = "MANT", name = "Orden de mantención", version = "v2.0",
            description = "Registro de intervención o mantención de un equipo.",
            requiresArea = true, requiresShift = false,
            extraField = ExtraField(label = "Código de equipo", hint = "Ej: CAEX-114"),
        ),
        ReportType(
            code = "INSP", name = "Checklist de inspección", version = "v1.1",
            description = "Formulario de inspección pre-uso o de área.",
            requiresArea = true, requiresShift = true,
        ),
        ReportType(
            code = "INC", name = "Reporte de incidente", version = "v1.0",
            description = "Registro documental de un incidente operacional.",
            requiresArea = true, requiresShift = true,
            extraField = ExtraField(label = "Nivel de severidad", options = listOf("Bajo", "Medio", "Alto", "Crítico")),
        ),
        ReportType(
            code = "PROD", name = "Parte de producción", version = "v1.0",
            description = "Registro de producción o tonelaje del turno.",
            requiresArea = false, requiresShift = true,
        ),
    )

    val processes: List<OperationalProcess> = listOf(
        OperationalProcess("EXT", "Extracción mina"),
        OperationalProcess("CHA", "Chancado"),
        OperationalProcess("MOL", "Molienda"),
        OperationalProcess("FLO", "Flotación"),
        OperationalProcess("MAN", "Mantención mecánica"),
        OperationalProcess("ELE", "Mantención eléctrica"),
        OperationalProcess("TRA", "Transporte y despacho"),
        OperationalProcess("SSO", "Seguridad y salud ocupacional"),
    )

    val areas: List<String> = listOf(
        "Rajo Norte",
        "Planta concentradora",
        "Taller de mantención",
        "Bodega central",
        "Patio de despacho",
    )

    val shifts: List<String> = listOf(
        "Turno A · Día (08:00–20:00)",
        "Turno B · Noche (20:00–08:00)",
    )

    fun type(code: String?): ReportType? = reportTypes.firstOrNull { it.code == code }
    fun process(code: String?): OperationalProcess? = processes.firstOrNull { it.code == code }

    fun typeName(code: String?): String = type(code)?.name ?: "Sin tipo"
    fun processName(code: String?): String = process(code)?.name ?: "Sin proceso"
}
