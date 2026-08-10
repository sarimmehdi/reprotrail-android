package dev.reprotrail.runtime.domain.model

/** Durable lifecycle state of a locally captured trace session. */
enum class StoredTraceSessionState {
    /** Represents the active choice. */
    ACTIVE,

    /** Represents the paused choice. */
    PAUSED,

    /** Represents the completed choice. */
    COMPLETED,
}
