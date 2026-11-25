package cl.vitairos.captura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Transaction
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReportWithAttachments>>

    @Transaction
    @Query("SELECT * FROM reports WHERE id = :id")
    fun observe(id: String): Flow<ReportWithAttachments?>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun get(id: String): ReportEntity?

    @Query("SELECT * FROM reports WHERE status = 'EN_COLA' ORDER BY createdAt ASC")
    suspend fun getQueued(): List<ReportEntity>

    @Query("SELECT COUNT(*) FROM reports WHERE status = 'EN_COLA'")
    suspend fun countQueued(): Int

    @Upsert
    suspend fun upsert(report: ReportEntity)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun delete(id: String)

    /** Eliminación condicional: solo Borrador o En cola (nunca un reporte ya enviado o en envío). */
    @Query("DELETE FROM reports WHERE id = :id AND status IN ('BORRADOR', 'EN_COLA')")
    suspend fun deleteIfDeletable(id: String): Int

    /** "Sincronizar ahora": anula la espera de backoff de los reportes en cola. */
    @Query("UPDATE reports SET nextRetryAt = NULL WHERE status = 'EN_COLA'")
    suspend fun clearBackoff()

    /** Actualiza SOLO campos editables y SOLO si el estado lo permite (atómico, sin carreras con la sincronización). */
    @Query(
        """
        UPDATE reports SET reportTypeCode = :typeCode, reportTypeVersion = :typeVersion, processCode = :processCode,
            area = :area, shift = :shift, extraField = :extra, observations = :observations,
            qualityConfirmed = :qualityConfirmed, updatedAt = :now
        WHERE id = :id AND status IN ('BORRADOR', 'EN_COLA', 'ERROR')
        """
    )
    suspend fun updateEditableFields(
        id: String, typeCode: String, typeVersion: String, processCode: String, area: String?, shift: String?,
        extra: String?, observations: String, qualityConfirmed: Boolean, now: Long,
    ): Int

    /** Transición condicional de estado: devuelve filas afectadas (0 = el estado ya no era el esperado). */
    @Query(
        """
        UPDATE reports SET status = :to, lastMessage = :message, updatedAt = :now
        WHERE id = :id AND status IN (:from)
        """
    )
    suspend fun transition(id: String, from: List<String>, to: String, message: String?, now: Long): Int

    @Query(
        """
        UPDATE reports SET status = 'EN_COLA', autoRetryCount = 0, nextRetryAt = NULL, lastMessage = :message,
            updatedAt = :now
        WHERE id = :id AND status IN ('BORRADOR', 'EN_COLA', 'ERROR')
        """
    )
    suspend fun enqueue(id: String, message: String, now: Long): Int

    @Query(
        """
        UPDATE reports SET status = 'ENVIANDO', attemptCount = attemptCount + 1, lastAttemptAt = :now,
            nextRetryAt = NULL, lastMessage = :message, updatedAt = :now
        WHERE id = :id AND status = 'EN_COLA'
        """
    )
    suspend fun markSending(id: String, message: String, now: Long): Int

    @Query(
        """
        UPDATE reports SET status = 'SINCRONIZADO', syncedAt = :now, serverReceiptId = :receipt,
            lastMessage = :message, processingStatus = :processing, nextRetryAt = NULL, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun markSynced(id: String, receipt: String?, message: String, processing: String?, now: Long)

    @Query(
        """
        UPDATE reports SET status = :status, autoRetryCount = :autoRetryCount, nextRetryAt = :nextRetryAt,
            lastMessage = :message, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: String, status: String, autoRetryCount: Int, nextRetryAt: Long?, message: String, now: Long)

    @Query("UPDATE reports SET status = 'EN_COLA', lastMessage = :message, updatedAt = :now WHERE status = 'ENVIANDO'")
    suspend fun recoverInterrupted(message: String, now: Long): Int

    @Query("SELECT id FROM reports WHERE status = 'ENVIANDO'")
    suspend fun sendingIds(): List<String>

    @Query(
        """
        UPDATE reports SET status = 'REQUIERE_CORRECCION', supervisorNote = :note, lastMessage = :message, updatedAt = :now
        WHERE id = :id AND status = 'SINCRONIZADO'
        """
    )
    suspend fun markNeedsCorrection(id: String, note: String, message: String, now: Long): Int

    @Query("UPDATE reports SET processingStatus = :status, processingResultJson = :json, updatedAt = :now WHERE id = :id")
    suspend fun updateProcessing(id: String, status: String, json: String?, now: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE reportId = :reportId ORDER BY createdAt ASC")
    suspend fun forReport(reportId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE reportId = :reportId ORDER BY createdAt ASC")
    fun observeForReport(reportId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE attachments SET localPath = NULL WHERE reportId = :reportId")
    suspend fun clearLocalPaths(reportId: String)

    /** Otros reportes que ya contienen el mismo archivo (hash). */
    @Query("SELECT reportId FROM attachments WHERE sha256 = :sha AND reportId != :exceptReportId")
    suspend fun reportsWithHash(sha: String, exceptReportId: String): List<String>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: ReportEventEntity)

    @Query("SELECT * FROM report_events WHERE reportId = :reportId ORDER BY timestamp ASC, id ASC")
    fun observeForReport(reportId: String): Flow<List<ReportEventEntity>>
}
