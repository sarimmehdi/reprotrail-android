package dev.reprotrail.runtime

import dev.reprotrail.runtime.domain.model.StoredTrace
import dev.reprotrail.runtime.domain.model.StoredTraceUploadState
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import dev.reprotrail.runtime.domain.repository.TraceUploadStateRepository
import dev.reprotrail.runtime.upload.domain.TraceUploader
import dev.reprotrail.runtime.upload.domain.model.TraceUpload
import dev.reprotrail.runtime.upload.domain.model.TraceUploadOutcome
import kotlinx.coroutines.CancellationException
import java.time.Instant

internal fun interface TraceContentEncoder {
    fun encode(trace: StoredTrace): String
}

internal enum class TraceUploadWorkResult {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal class TraceUploadCoordinator(
    private val projectId: String,
    private val traceRepository: TraceSessionRepository,
    private val stateRepository: TraceUploadStateRepository,
    private val uploader: TraceUploader,
    private val contentEncoder: TraceContentEncoder,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun execute(
        sessionId: String,
        attemptNumber: Int,
        maximumAttempts: Int,
    ): TraceUploadWorkResult {
        require(attemptNumber > 0) { "An upload attempt number must be positive." }
        require(maximumAttempts > 0) { "At least one upload attempt must be allowed." }
        val trace = traceRepository.loadSession(sessionId)
        return if (trace == null) {
            TraceUploadWorkResult.SUCCESS
        } else {
            executeTrace(trace, sessionId, attemptNumber, maximumAttempts)
        }
    }

    private suspend fun executeTrace(
        trace: StoredTrace,
        sessionId: String,
        attemptNumber: Int,
        maximumAttempts: Int,
    ): TraceUploadWorkResult {
        stateRepository.updateUploadState(
            sessionId,
            StoredTraceUploadState.UPLOADING,
            attemptNumber,
            null,
            null,
        )
        val content = encodeOrNull(trace) ?: return rejectInvalidTrace(sessionId, attemptNumber)
        val outcome =
            try {
                uploader.upload(TraceUpload(projectId, sessionId, content))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                TraceUploadOutcome.RetryableFailure
            }
        return persistOutcome(sessionId, attemptNumber, maximumAttempts, outcome)
    }

    private fun encodeOrNull(trace: StoredTrace): String? =
        try {
            contentEncoder.encode(trace)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }

    private suspend fun persistOutcome(
        sessionId: String,
        attemptNumber: Int,
        maximumAttempts: Int,
        outcome: TraceUploadOutcome,
    ): TraceUploadWorkResult =
        when (outcome) {
            TraceUploadOutcome.Created,
            TraceUploadOutcome.AlreadyStored,
            -> {
                stateRepository.updateUploadState(
                    sessionId,
                    StoredTraceUploadState.SUCCEEDED,
                    attemptNumber,
                    null,
                    now().toString(),
                )
                TraceUploadWorkResult.SUCCESS
            }
            is TraceUploadOutcome.Rejected -> {
                stateRepository.updateUploadState(
                    sessionId,
                    StoredTraceUploadState.REJECTED,
                    attemptNumber,
                    outcome.reason.name,
                    null,
                )
                TraceUploadWorkResult.FAILURE
            }
            TraceUploadOutcome.RetryableFailure -> persistRetry(sessionId, attemptNumber, maximumAttempts)
        }

    private suspend fun persistRetry(
        sessionId: String,
        attemptNumber: Int,
        maximumAttempts: Int,
    ): TraceUploadWorkResult {
        val exhausted = attemptNumber >= maximumAttempts
        stateRepository.updateUploadState(
            sessionId,
            if (exhausted) StoredTraceUploadState.REJECTED else StoredTraceUploadState.ENQUEUED,
            attemptNumber,
            if (exhausted) RETRY_EXHAUSTED else null,
            null,
        )
        return if (exhausted) TraceUploadWorkResult.FAILURE else TraceUploadWorkResult.RETRY
    }

    private suspend fun rejectInvalidTrace(
        sessionId: String,
        attemptNumber: Int,
    ): TraceUploadWorkResult {
        stateRepository.updateUploadState(
            sessionId,
            StoredTraceUploadState.REJECTED,
            attemptNumber,
            INVALID_LOCAL_TRACE,
            null,
        )
        return TraceUploadWorkResult.FAILURE
    }

    private companion object {
        const val RETRY_EXHAUSTED = "RETRY_EXHAUSTED"
        const val INVALID_LOCAL_TRACE = "INVALID_LOCAL_TRACE"
    }
}
