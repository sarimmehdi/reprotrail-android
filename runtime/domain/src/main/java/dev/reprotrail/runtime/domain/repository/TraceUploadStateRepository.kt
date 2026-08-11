package dev.reprotrail.runtime.domain.repository

import dev.reprotrail.runtime.domain.model.StoredTraceUploadState

/** Persists hosted-upload lifecycle independently from trace capture operations. */
interface TraceUploadStateRepository {
    /** Atomically stores the latest hosted-upload lifecycle and safe diagnostic metadata. */
    suspend fun updateUploadState(
        sessionId: String,
        state: StoredTraceUploadState,
        attemptCount: Int,
        failureReason: String?,
        uploadedAt: String?,
    )
}
