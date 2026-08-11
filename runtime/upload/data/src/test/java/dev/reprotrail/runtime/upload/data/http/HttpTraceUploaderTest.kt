package dev.reprotrail.runtime.upload.data.http

import dev.reprotrail.runtime.upload.domain.credential.IngestCredentialProvider
import dev.reprotrail.runtime.upload.domain.model.TraceUpload
import dev.reprotrail.runtime.upload.domain.model.TraceUploadOutcome
import dev.reprotrail.runtime.upload.domain.model.TraceUploadRejection
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HttpTraceUploaderTest {
    private lateinit var server: MockWebServer

    private val credentialProvider = IngestCredentialProvider { "rt_ingest_test.secret" }

    private fun uploader(): HttpTraceUploader =
        HttpTraceUploader(
            baseUrl = server.url("/").toString(),
            credentialProvider = credentialProvider,
        )

    private fun upload(): TraceUpload =
        TraceUpload(
            projectId = "11111111-1111-1111-1111-111111111111",
            sessionId = "22222222-2222-2222-2222-222222222222",
            content = "{\"schemaVersion\":1}",
        )

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `created upload sends the authenticated immutable ingest request`() =
        runTest {
            // Given
            server.enqueue(MockResponse().setResponseCode(201))
            // When
            val outcome = uploader().upload(upload())
            val request = server.takeRequest()
            // Then
            assertEquals(TraceUploadOutcome.Created, outcome)
            assertEquals("POST", request.method)
            assertEquals("/v1/projects/11111111-1111-1111-1111-111111111111/traces", request.path)
            assertEquals("Bearer rt_ingest_test.secret", request.getHeader("Authorization"))
            assertEquals("22222222-2222-2222-2222-222222222222", request.getHeader("Idempotency-Key"))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals("{\"schemaVersion\":1}", request.body.readUtf8())
        }

    @Test
    fun `identical retry is a successful existing upload`() =
        runTest {
            // Given
            server.enqueue(MockResponse().setResponseCode(200))
            // When
            val outcome = uploader().upload(upload())
            // Then
            assertEquals(TraceUploadOutcome.AlreadyStored, outcome)
        }

    @Test
    fun `client responses preserve their terminal rejection reason`() =
        runTest {
            // Given
            val expected =
                listOf(
                    400 to TraceUploadRejection.INVALID_REQUEST,
                    401 to TraceUploadRejection.UNAUTHORIZED,
                    409 to TraceUploadRejection.IDEMPOTENCY_CONFLICT,
                    413 to TraceUploadRejection.PAYLOAD_TOO_LARGE,
                    422 to TraceUploadRejection.UNPROCESSABLE_TRACE,
                )
            expected.forEach { (status, _) -> server.enqueue(MockResponse().setResponseCode(status)) }
            // When
            val outcomes = expected.map { uploader().upload(upload()) }
            // Then
            assertEquals(
                expected.map { (_, reason) -> TraceUploadOutcome.Rejected(reason) },
                outcomes,
            )
        }

    @Test
    fun `unknown client response remains terminal`() =
        runTest {
            // Given
            server.enqueue(MockResponse().setResponseCode(418))
            // When
            val outcome = uploader().upload(upload())
            // Then
            assertEquals(
                TraceUploadOutcome.Rejected(TraceUploadRejection.UNKNOWN_CLIENT_ERROR),
                outcome,
            )
        }

    @Test
    fun `server failure is retryable`() =
        runTest {
            // Given
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setResponseCode(503))
            // When
            val rateLimited = uploader().upload(upload())
            val unavailable = uploader().upload(upload())
            // Then
            assertEquals(TraceUploadOutcome.RetryableFailure, rateLimited)
            assertEquals(TraceUploadOutcome.RetryableFailure, unavailable)
        }
}
