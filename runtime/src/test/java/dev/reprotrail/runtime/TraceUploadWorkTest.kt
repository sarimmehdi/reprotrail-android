package dev.reprotrail.runtime

import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.reprotrail.runtime.domain.model.StoredTrace
import dev.reprotrail.runtime.domain.model.StoredTraceSession
import dev.reprotrail.runtime.domain.model.StoredTraceUploadState
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import dev.reprotrail.runtime.domain.repository.TraceUploadStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TraceUploadWorkTest {
    private var registeredCoordinator: TraceUploadCoordinator? = null

    @After
    fun unregisterCoordinator() {
        registeredCoordinator?.let { TraceUploadRuntimeRegistry.unregister(PROJECT_ID, it) }
    }

    @Test
    fun `worker retries while application graph is unavailable`() =
        runTest {
            val result = worker(runAttemptCount = 0, maximumAttempts = 5).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `worker stops retrying unavailable graph at configured bound`() =
        runTest {
            val result = worker(runAttemptCount = 4, maximumAttempts = 5).doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `worker delegates to registered project coordinator`() =
        runTest {
            val coordinator = mockk<TraceUploadCoordinator>()
            coEvery { coordinator.execute(SESSION_ID, 1, 5) } returns TraceUploadWorkResult.SUCCESS
            TraceUploadRuntimeRegistry.register(PROJECT_ID, coordinator)
            registeredCoordinator = coordinator

            val result = worker(runAttemptCount = 0, maximumAttempts = 5).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun `scheduler persists enqueue before submitting unique work`() =
        runTest {
            val workManager = mockk<WorkManager>()
            val traceRepository = mockk<TraceSessionRepository>()
            val stateRepository = mockk<TraceUploadStateRepository>(relaxUnitFun = true)
            val factory = TraceUploadWorkRequestFactory(PROJECT_ID, maximumAttempts = 5)
            coEvery { traceRepository.loadSession(SESSION_ID) } returns
                storedTrace(StoredTraceUploadState.NOT_SCHEDULED)
            every {
                workManager.enqueueUniqueWork(
                    any<String>(),
                    any<ExistingWorkPolicy>(),
                    any<OneTimeWorkRequest>(),
                )
            } returns mockk()
            val scheduler = TraceUploadWorkScheduler(workManager, factory, traceRepository, stateRepository)

            scheduler.enqueue(SESSION_ID)

            coVerify {
                stateRepository.updateUploadState(
                    SESSION_ID,
                    StoredTraceUploadState.ENQUEUED,
                    0,
                    null,
                    null,
                )
            }
            verify {
                workManager.enqueueUniqueWork(
                    "reprotrail-upload-$PROJECT_ID-$SESSION_ID",
                    ExistingWorkPolicy.KEEP,
                    any<OneTimeWorkRequest>(),
                )
            }
        }

    @Test
    fun `scheduler rejects duplicate active or successful upload`() =
        runTest {
            val workManager = mockk<WorkManager>(relaxed = true)
            val traceRepository = mockk<TraceSessionRepository>()
            val stateRepository = mockk<TraceUploadStateRepository>(relaxUnitFun = true)
            coEvery { traceRepository.loadSession(SESSION_ID) } returns
                storedTrace(StoredTraceUploadState.SUCCEEDED)
            val scheduler =
                TraceUploadWorkScheduler(
                    workManager,
                    TraceUploadWorkRequestFactory(PROJECT_ID, maximumAttempts = 5),
                    traceRepository,
                    stateRepository,
                )

            val failure = runCatching { scheduler.enqueue(SESSION_ID) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            coVerify(exactly = 0) { stateRepository.updateUploadState(any(), any(), any(), any(), any()) }
            verify(exactly = 0) {
                workManager.enqueueUniqueWork(
                    any<String>(),
                    any<ExistingWorkPolicy>(),
                    any<OneTimeWorkRequest>(),
                )
            }
        }

    private fun storedTrace(state: StoredTraceUploadState): StoredTrace {
        val session = mockk<StoredTraceSession>()
        every { session.uploadState } returns state
        val trace = mockk<StoredTrace>()
        every { trace.session } returns session
        return trace
    }

    private fun worker(
        runAttemptCount: Int,
        maximumAttempts: Int,
    ): TraceUploadWorker =
        TestListenableWorkerBuilder<TraceUploadWorker>(
            context = RuntimeEnvironment.getApplication(),
            inputData =
                workDataOf(
                    UPLOAD_PROJECT_ID_KEY to PROJECT_ID,
                    UPLOAD_SESSION_ID_KEY to SESSION_ID,
                    UPLOAD_MAXIMUM_ATTEMPTS_KEY to maximumAttempts,
                ),
            runAttemptCount = runAttemptCount,
        ).build()

    private companion object {
        const val PROJECT_ID = "33333333-3333-3333-3333-333333333333"
        const val SESSION_ID = "44444444-4444-4444-4444-444444444444"
    }
}
