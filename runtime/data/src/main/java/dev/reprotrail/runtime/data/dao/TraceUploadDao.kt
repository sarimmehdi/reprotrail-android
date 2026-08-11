package dev.reprotrail.runtime.data.dao

import androidx.room.Dao
import androidx.room.Query

/** Persists hosted-upload lifecycle transitions for retained trace sessions. */
@Dao
interface TraceUploadDao {
    /** Atomically persists one hosted-upload lifecycle transition. */
    @Query(
        "UPDATE trace_sessions SET uploadState = :state, uploadAttemptCount = :attemptCount, " +
            "uploadFailureReason = :failureReason, uploadedAt = :uploadedAt WHERE id = :sessionId",
    )
    suspend fun updateUploadState(
        sessionId: String,
        state: String,
        attemptCount: Int,
        failureReason: String?,
        uploadedAt: String?,
    )
}
