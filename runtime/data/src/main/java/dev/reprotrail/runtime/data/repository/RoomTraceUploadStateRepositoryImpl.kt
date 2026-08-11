package dev.reprotrail.runtime.data.repository

import dev.reprotrail.runtime.data.database.ReproTrailDatabase
import dev.reprotrail.runtime.domain.model.StoredTraceUploadState
import dev.reprotrail.runtime.domain.repository.TraceUploadStateRepository

/** Persists hosted-upload lifecycle in the shared private Room database. */
class RoomTraceUploadStateRepositoryImpl(
    private val database: ReproTrailDatabase,
) : TraceUploadStateRepository {
    override suspend fun updateUploadState(
        sessionId: String,
        state: StoredTraceUploadState,
        attemptCount: Int,
        failureReason: String?,
        uploadedAt: String?,
    ) = run {
        require(attemptCount >= 0) { "An upload attempt count cannot be negative." }
        database.traceUploadDao().updateUploadState(
            sessionId = sessionId,
            state = state.name,
            attemptCount = attemptCount,
            failureReason = failureReason,
            uploadedAt = uploadedAt,
        )
    }
}
