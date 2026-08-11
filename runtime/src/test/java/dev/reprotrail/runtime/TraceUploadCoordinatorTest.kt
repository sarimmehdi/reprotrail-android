package dev.reprotrail.runtime

import dev.reprotrail.runtime.domain.model.StoredTrace
import dev.reprotrail.runtime.domain.model.StoredTraceUploadState
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import dev.reprotrail.runtime.domain.repository.TraceUploadStateRepository
import dev.reprotrail.runtime.upload.domain.TraceUploader
import dev.reprotrail.runtime.upload.domain.model.TraceUpload
import dev.reprotrail.runtime.upload.domain.model.TraceUploadOutcome
import dev.reprotrail.runtime.upload.domain.model.TraceUploadRejection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TraceUploadCoordinatorTest {
    private val traceRepository = mockk<TraceSessionRepository>()
    private val stateRepository = mockk<TraceUploadStateRepository>(relaxUnitFun = true)
    private val uploader = mockk<TraceUploader>()
    private val storedTrace = mockk<StoredTrace>()
    private val encodedContent = "{\"schemaVersion\":\"1.0.0-alpha.1\"}"
    private val coordinator =
        TraceUploadCoordinator(
            projectId = PROJECT_ID,
            traceRepository = traceRepository,
            stateRepository = stateRepository,
            uploader = uploader,
            contentEncoder = TraceContentEncoder { encodedContent },
            now = { Instant.parse(UPLOADED_AT) },
        )

    @Test
    fun `created trace becomes durably succeeded`() =
        runTest {
            val captured = slot<TraceUpload>()
            coEvery { traceRepository.loadSession(SESSION_ID) } returns storedTrace
            coEvery { uploader.upload(capture(captured)) } returns TraceUploadOutcome.Created

            val result = coordinator.execute(SESSION_ID, attemptNumber = 1, maximumAttempts = 5)

            assertEquals(TraceUploadWorkResult.SUCCESS, result)
            assertEquals(TraceUpload(PROJECT_ID, SESSION_ID, encodedContent), captured.captured)
            coVerifyOrder {
                stateRepository.updateUploadState(
                    SESSION_ID,
                    StoredTraceUploadState.UPLOADING,
                    1,
                    null,
                    null,
                )
                stateRepository.updateUploadState(
                    SESSION_ID,
                    StoredTraceUploadState.SUCCEEDED,
                    1,
                    null,
                    UPLOADED_AT,
                )
            }
        }

    @Test
    fun `idempotent existing trace is also successful`() =
        runTest {
            coEvery { traceRepository.loadSession(SESSION_ID) } returns storedTrace
            coEvery { uploader.upload(any()) } returns TraceUploadOutcome.AlreadyStored

            val result = coordinator.execute(SESSION_ID, attemptNumber = 2, maximumAttempts = 5)

            assertEquals(TraceUploadWorkResult.SUCCESS, result)
            coVerify {
                stateRepository.updateUploadState(
                    SESSION_ID,
                    StoredTraceUploadState.SUCCEEDED,
                    2,
                    null,
                    UPLOADED_AT,
                )
            }
        }

    @Test
    fun `transient failure remains enqueued below retry bound`() =
        runTest {
            coEvery { traceRepository.loadSession(SESSION_ID) } returns storedTrace
            coEvery { uploader.upload(any()) } returns TraceUploadOutcome.RetryableFailure

            val result = coordinator.execute(SESSION_ID, attemptNumber = 2, maximumAttempts = 5)

            assertEquals(TraceUploadWorkResult.RETRY, result)
            coVerify {
                stateRepository.updateUploadState(
                    SESSION_ID,
                    StoredTraceUploadState.ENQUEUED,
                    2,
                    null,
                    null,
                )
            }
        }

    @Test
    fun `transient failure stops at retry bound`() =
        runTest {
            coEvery { traceRepository.loadSession(SESSION_ID) } returns storedTrace
            coEvery { uploader.upload(any()) } returns TraceUploadOutcome.RetryableFailure

            val result = coordinator.execute(SESSION_ID, attemptNumber = 5, maximumAttempts = 5)

            assertEquals(TraceUploadWorkResult.FAILURE, result)
            coVerify {
                stateRepository.updateUploadState(
                    SESSION_ID,
                    StoredTraceUploadState.REJECTED,
                    5,
                    "RETRY_EXHAUSTED",
                    null,
                )
            }
        }

    @Test
    fun `terminal rejection is persisted without backend content`() =
        runTest {
            coEvery { traceRepository.loadSession(SESSION_ID) } returns storedTrace
            coEvery { uploader.upload(any()) } returns
                TraceUploadOutcome.Rejected(TraceUploadRejection.UNAUTHORIZED)

            val result = coordinator.execute(SESSION_ID, attemptNumber = 1, maximumAttempts = 5)

            assertEquals(TraceUploadWorkResult.FAILURE, result)
            coVerify {
                stateRepository.updateUploadState(
                    SESSION_ID,
                    StoredTraceUploadState.REJECTED,
                    1,
                    "UNAUTHORIZED",
                    null,
                )
            }
        }

    @Test
    fun `deleted trace completes work without uploading`() =
        runTest {
            coEvery { traceRepository.loadSession(SESSION_ID) } returns null

            val result = coordinator.execute(SESSION_ID, attemptNumber = 1, maximumAttempts = 5)

            assertEquals(TraceUploadWorkResult.SUCCESS, result)
            coVerify(exactly = 0) { uploader.upload(any()) }
            coVerify(exactly = 0) { stateRepository.updateUploadState(any(), any(), any(), any(), any()) }
        }

    private companion object {
        const val PROJECT_ID = "11111111-1111-1111-1111-111111111111"
        const val SESSION_ID = "22222222-2222-2222-2222-222222222222"
        const val UPLOADED_AT = "2026-08-11T20:00:00Z"
    }
}
