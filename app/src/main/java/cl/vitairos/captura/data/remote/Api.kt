package cl.vitairos.captura.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Contrato REST del ingreso de reportes. En el MVP lo responde [MockApiInterceptor]
 * (no hay servidor ni credenciales reales). Cambiando la URL base y quitando el interceptor
 * podría apuntarse a un endpoint de prueba de VITAIROS.
 */
interface VitairosApi {

    /** Una sola petición multipart: parte "metadata" (JSON) + partes "files" (adjuntos). */
    @Multipart
    @POST("api/v1/reports")
    suspend fun uploadReport(
        @Part("metadata") metadata: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): Response<UploadResponse>

    /** Estado de procesamiento en VITAIROS Industrial AI — SIMULADO. */
    @GET("api/v1/reports/{id}/processing")
    suspend fun processing(@Path("id") reportId: String): Response<ProcessingResponse>
}

// ---- DTOs (nullable para robustez con Gson) ----

data class ReportMetadataDto(
    val reportId: String,
    val folio: String,
    val reportType: String,
    val reportTypeVersion: String,
    val process: String,
    val area: String?,
    val shift: String?,
    val extraField: String?,
    val observations: String,
    val userId: String,
    val userName: String,
    val userRole: String,
    val deviceInstallId: String,
    val createdAt: String,
    val attempt: Int,
    val version: Int,
    val parentReportId: String?,
    val attachments: List<AttachmentMetaDto>,
)

data class AttachmentMetaDto(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class UploadResponse(
    val receiptId: String?,
    val reportId: String?,
    val status: String?,
    val message: String?,
    val duplicate: Boolean?,
    val processingStatus: String?,
)

data class ProcessingResponse(
    val reportId: String?,
    val status: String?,
    val simulated: Boolean?,
    val message: String?,
    val extractedFields: Map<String, String>?,
)

data class ErrorResponse(
    val status: String?,
    val message: String?,
)
