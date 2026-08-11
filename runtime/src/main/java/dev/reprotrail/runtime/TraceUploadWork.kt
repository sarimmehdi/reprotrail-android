package dev.reprotrail.runtime

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.reprotrail.runtime.domain.model.StoredTraceUploadState
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import dev.reprotrail.runtime.domain.repository.TraceUploadStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal const val UPLOAD_PROJECT_ID_KEY = "reprotrail.project_id"
internal const val UPLOAD_SESSION_ID_KEY = "reprotrail.session_id"
internal const val UPLOAD_MAXIMUM_ATTEMPTS_KEY = "reprotrail.maximum_attempts"

internal class TraceUploadWorkRequestFactory(
    private val projectId: String,
    private val maximumAttempts: Int,
) {
    init {
        require(projectId.isNotBlank()) { "An upload project ID is required." }
        require(maximumAttempts > 0) { "At least one upload attempt must be allowed." }
    }

    fun create(sessionId: String): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<TraceUploadWorker>()
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MINIMUM_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    UPLOAD_PROJECT_ID_KEY to projectId,
                    UPLOAD_SESSION_ID_KEY to sessionId,
                    UPLOAD_MAXIMUM_ATTEMPTS_KEY to maximumAttempts,
                ),
            ).build()

    fun uniqueWorkName(sessionId: String): String = "reprotrail-upload-$projectId-$sessionId"

    private companion object {
        const val MINIMUM_BACKOFF_SECONDS = 10L
    }
}

internal class TraceUploadWorkScheduler(
    private val workManager: WorkManager,
    private val requestFactory: TraceUploadWorkRequestFactory,
    private val traceRepository: TraceSessionRepository,
    private val stateRepository: TraceUploadStateRepository,
) {
    private val enqueueMutex = Mutex()

    suspend fun enqueue(sessionId: String): UUID =
        enqueueMutex.withLock {
            val state = checkNotNull(traceRepository.loadSession(sessionId)).session.uploadState
            check(state == StoredTraceUploadState.NOT_SCHEDULED || state == StoredTraceUploadState.REJECTED) {
                "The trace upload is already scheduled or completed."
            }
            stateRepository.updateUploadState(
                sessionId,
                StoredTraceUploadState.ENQUEUED,
                0,
                null,
                null,
            )
            val request = requestFactory.create(sessionId)
            workManager.enqueueUniqueWork(
                requestFactory.uniqueWorkName(sessionId),
                ExistingWorkPolicy.KEEP,
                request,
            )
            request.id
        }
}

/** Executes one bounded hosted trace-upload attempt scheduled by ReproTrail. */
public class TraceUploadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val input = readUploadInput() ?: return Result.failure()
        val attemptNumber = runAttemptCount + 1
        val coordinator = TraceUploadRuntimeRegistry.get(input.projectId)
        return if (coordinator == null) {
            boundedRetry(attemptNumber, input.maximumAttempts)
        } else {
            execute(coordinator, input, attemptNumber)
        }
    }

    private fun readUploadInput(): UploadWorkerInput? {
        val projectId = inputData.getString(UPLOAD_PROJECT_ID_KEY)
        val sessionId = inputData.getString(UPLOAD_SESSION_ID_KEY)
        val maximumAttempts = inputData.getInt(UPLOAD_MAXIMUM_ATTEMPTS_KEY, 0)
        return if (projectId == null || sessionId == null || maximumAttempts <= 0) {
            null
        } else {
            UploadWorkerInput(projectId, sessionId, maximumAttempts)
        }
    }

    private suspend fun execute(
        coordinator: TraceUploadCoordinator,
        input: UploadWorkerInput,
        attemptNumber: Int,
    ): Result =
        try {
            when (coordinator.execute(input.sessionId, attemptNumber, input.maximumAttempts)) {
                TraceUploadWorkResult.SUCCESS -> Result.success()
                TraceUploadWorkResult.RETRY -> Result.retry()
                TraceUploadWorkResult.FAILURE -> Result.failure()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            boundedRetry(attemptNumber, input.maximumAttempts)
        }

    private fun boundedRetry(
        attemptNumber: Int,
        maximumAttempts: Int,
    ): Result = if (attemptNumber >= maximumAttempts) Result.failure() else Result.retry()
}

private data class UploadWorkerInput(
    val projectId: String,
    val sessionId: String,
    val maximumAttempts: Int,
)

internal object TraceUploadRuntimeRegistry {
    private val coordinators = ConcurrentHashMap<String, TraceUploadCoordinator>()

    fun register(
        projectId: String,
        coordinator: TraceUploadCoordinator,
    ) {
        check(coordinators.putIfAbsent(projectId, coordinator) == null) {
            "Only one active ReproTrail uploader may use a project ID."
        }
    }

    fun unregister(
        projectId: String,
        coordinator: TraceUploadCoordinator,
    ) {
        coordinators.remove(projectId, coordinator)
    }

    fun get(projectId: String): TraceUploadCoordinator? = coordinators[projectId]
}
