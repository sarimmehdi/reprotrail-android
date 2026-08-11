package dev.reprotrail.runtime.upload.domain.model

/** Permanent backend rejection categories that must not be retried automatically. */
enum class TraceUploadRejection {
    /** Represents the invalid request choice. */
    INVALID_REQUEST,

    /** Represents the unauthorized choice. */
    UNAUTHORIZED,

    /** Represents the idempotency conflict choice. */
    IDEMPOTENCY_CONFLICT,

    /** Represents the payload too large choice. */
    PAYLOAD_TOO_LARGE,

    /** Represents the unprocessable trace choice. */
    UNPROCESSABLE_TRACE,

    /** Represents the unknown client error choice. */
    UNKNOWN_CLIENT_ERROR,
}
