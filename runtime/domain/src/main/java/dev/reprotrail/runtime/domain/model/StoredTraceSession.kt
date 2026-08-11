package dev.reprotrail.runtime.domain.model

/** Metadata required to recover and export one locally captured trace session. */
data class StoredTraceSession(
    /** Stable UUID assigned when recording starts. */
    val id: String,
    /** RFC 3339 wall-clock time at which capture began. */
    val startedAt: String,
    /** Android application package that produced the session. */
    val packageName: String,
    /** Host privacy-policy version applied before persistence. */
    val policyVersion: String,
    /** Serialized replay environment captured after the first eligible action. */
    val environmentJson: String?,
    /** Current recording lifecycle state. */
    val state: StoredTraceSessionState,
    /** Number of eligible actions rejected by the configured session bound. */
    val droppedActionCount: Int,
    /** Local ordering key used only for deterministic retention. */
    val createdAtEpochMs: Long,
    /** RFC 3339 wall-clock time at which capture completed. */
    val endedAt: String?,
    /** Monotonic elapsed duration of a completed session. */
    val durationMs: Long?,
    /** Last durable hosted-upload lifecycle state. */
    val uploadState: StoredTraceUploadState,
    /** Number of hosted upload attempts that reached the worker. */
    val uploadAttemptCount: Int,
    /** Stable terminal rejection category, without backend content or credentials. */
    val uploadFailureReason: String?,
    /** RFC 3339 time at which hosted ingestion became durable. */
    val uploadedAt: String?,
)
