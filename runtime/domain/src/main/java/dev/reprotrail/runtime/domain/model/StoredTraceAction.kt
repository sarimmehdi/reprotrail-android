package dev.reprotrail.runtime.domain.model

/** Privacy-reviewed canonical action payload stored for one trace session. */
data class StoredTraceAction(
    /** Action UUID preserved in the exported wire contract. */
    val id: String,
    /** Owning trace-session UUID. */
    val sessionId: String,
    /** Zero-based action order within the owning session. */
    val sequence: Int,
    /** Monotonic offset from session start. */
    val offsetMs: Long,
    /** Canonical JSON representation of the privacy-approved action. */
    val payloadJson: String,
)
