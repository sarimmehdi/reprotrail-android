package dev.reprotrail.runtime.upload.data.http

import dev.reprotrail.runtime.upload.domain.TraceUploader
import dev.reprotrail.runtime.upload.domain.credential.IngestCredentialProvider
import dev.reprotrail.runtime.upload.domain.model.TraceUpload
import dev.reprotrail.runtime.upload.domain.model.TraceUploadOutcome
import dev.reprotrail.runtime.upload.domain.model.TraceUploadRejection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException

/** Retrofit transport for the authenticated, idempotent hosted trace-ingestion endpoint. */
class HttpTraceUploader(
    baseUrl: String,
    private val credentialProvider: IngestCredentialProvider,
) : TraceUploader {
    private val api =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .build()
            .create(TraceIngestApi::class.java)

    override suspend fun upload(trace: TraceUpload): TraceUploadOutcome =
        try {
            api
                .upload(
                    projectId = trace.projectId,
                    authorization = "Bearer ${credentialProvider.getCredential()}",
                    idempotencyKey = trace.sessionId,
                    content = trace.content.toRequestBody(JSON_MEDIA_TYPE),
                ).toOutcome()
        } catch (_: IOException) {
            TraceUploadOutcome.RetryableFailure
        }
}

private interface TraceIngestApi {
    @POST("v1/projects/{projectId}/traces")
    suspend fun upload(
        @Path("projectId") projectId: String,
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body content: okhttp3.RequestBody,
    ): Response<Unit>
}

private fun Response<Unit>.toOutcome(): TraceUploadOutcome =
    when (code()) {
        HTTP_CREATED -> TraceUploadOutcome.Created
        HTTP_OK -> TraceUploadOutcome.AlreadyStored
        HTTP_BAD_REQUEST -> TraceUploadOutcome.Rejected(TraceUploadRejection.INVALID_REQUEST)
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> TraceUploadOutcome.Rejected(TraceUploadRejection.UNAUTHORIZED)
        HTTP_CONFLICT -> TraceUploadOutcome.Rejected(TraceUploadRejection.IDEMPOTENCY_CONFLICT)
        HTTP_PAYLOAD_TOO_LARGE -> TraceUploadOutcome.Rejected(TraceUploadRejection.PAYLOAD_TOO_LARGE)
        HTTP_UNPROCESSABLE_CONTENT -> TraceUploadOutcome.Rejected(TraceUploadRejection.UNPROCESSABLE_TRACE)
        HTTP_TOO_MANY_REQUESTS -> TraceUploadOutcome.RetryableFailure
        in HTTP_CLIENT_ERROR_MIN..HTTP_CLIENT_ERROR_MAX ->
            TraceUploadOutcome.Rejected(TraceUploadRejection.UNKNOWN_CLIENT_ERROR)
        else -> TraceUploadOutcome.RetryableFailure
    }

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private const val HTTP_OK = 200
private const val HTTP_CREATED = 201
private const val HTTP_CLIENT_ERROR_MIN = 400
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_CONFLICT = 409
private const val HTTP_PAYLOAD_TOO_LARGE = 413
private const val HTTP_UNPROCESSABLE_CONTENT = 422
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_CLIENT_ERROR_MAX = 499
