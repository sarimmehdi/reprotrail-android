package dev.reprotrail.runtime.upload.domain.model

/** Immutable trace content and tenant identifiers required for one idempotent hosted upload. */
data class TraceUpload(
    /** Backend project UUID that scopes authentication and storage. */
    val projectId: String,
    /** Trace session UUID reused as the backend idempotency key. */
    val sessionId: String,
    /** Canonical privacy-reviewed trace JSON sent without mutation. */
    val content: String,
)
