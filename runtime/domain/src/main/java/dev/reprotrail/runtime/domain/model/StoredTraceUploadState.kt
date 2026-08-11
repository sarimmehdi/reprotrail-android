package dev.reprotrail.runtime.domain.model

/** Durable local lifecycle of a hosted trace upload. */
enum class StoredTraceUploadState {
    /** Represents the not scheduled choice. */
    NOT_SCHEDULED,

    /** Represents the enqueued choice. */
    ENQUEUED,

    /** Represents the uploading choice. */
    UPLOADING,

    /** Represents the succeeded choice. */
    SUCCEEDED,

    /** Represents the rejected choice. */
    REJECTED,
}
