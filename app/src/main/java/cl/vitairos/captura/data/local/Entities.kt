package cl.vitairos.captura.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import cl.vitairos.captura.data.model.AttachmentSource
import cl.vitairos.captura.data.model.ReportStatus

/**
 * Reporte operacional. Campos automáticos: id, folio, createdAt, usuario, dispositivo, estado y
 * timestamps de sincronización. Campos manuales: tipo, proceso, área, turno, campo condicional,
 * observaciones y adjuntos (cuestionario §5).
 */
@Entity(
    tableName = "reports",
    indices = [Index("status"), Index("createdBy"), Index("parentReportId")],
)
data class ReportEntity(
    @PrimaryKey val id: String,                 // UUID: referencia principal anti-duplicados
    val folio: String,                          // código legible: VTR-AAMMDD-XXXX
    val reportTypeCode: String,                 // "" mientras es borrador incompleto
    val reportTypeVersion: String,
    val processCode: String,
    val area: String?,
    val shift: String?,
    val extraField: String?,                    // campo condicional según tipo
    val observations: String,
    val createdBy: String,
    val createdByName: String,
    val createdByRole: String,
    val deviceInstallId: String,                // identificador técnico NO sensible de la instalación
    val createdAt: Long,
    val updatedAt: Long,
    val status: ReportStatus,
    val qualityConfirmed: Boolean = false,
    // --- Trazabilidad de sincronización ---
    val attemptCount: Int = 0,                  // intentos totales (históricos)
    val autoRetryCount: Int = 0,                // reintentos automáticos usados en el ciclo actual
    val lastAttemptAt: Long? = null,
    val nextRetryAt: Long? = null,
    val syncedAt: Long? = null,
    val serverReceiptId: String? = null,
    val lastMessage: String? = null,
    // --- Versionado / corrección ---
    val version: Int = 1,
    val parentReportId: String? = null,
    val supervisorNote: String? = null,
    // --- Procesamiento VITAIROS Industrial AI (SIMULADO) ---
    val processingStatus: String? = null,
    val processingResultJson: String? = null,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = ["reportId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("reportId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val displayName: String,
    val mimeType: String,
    val source: AttachmentSource,
    val originalSizeBytes: Long,
    val storedSizeBytes: Long,
    val localPath: String?,          // ruta privada; se conserva también tras sincronizar
    val sha256: String,              // hash del contenido original (detección de duplicados)
    val pageCount: Int? = null,      // solo PDF
    val qualityWarning: String? = null,
    val createdAt: Long,
)

/** Bitácora de trazabilidad: cada cambio de estado o intento queda registrado. */
@Entity(
    tableName = "report_events",
    foreignKeys = [ForeignKey(
        entity = ReportEntity::class,
        parentColumns = ["id"],
        childColumns = ["reportId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("reportId")],
)
data class ReportEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: String,
    val timestamp: Long,
    val status: ReportStatus,
    val message: String,
    val attempt: Int? = null,
)

data class ReportWithAttachments(
    @Embedded val report: ReportEntity,
    @Relation(parentColumn = "id", entityColumn = "reportId")
    val attachments: List<AttachmentEntity>,
)
