package dev.reprotrail.runtime.upload.domain.model

/** Describes whether durable work should finish, stop permanently, or retry an upload. */
sealed interface TraceUploadOutcome {
    /** The backend created the immutable trace. */
    data object Created : TraceUploadOutcome

    /** An identical prior request already created the immutable trace. */
    data object AlreadyStored : TraceUploadOutcome

    /** The backend rejected the request permanently and an unchanged retry must not run. */
    data class Rejected(
        /** Stable rejection category suitable for local state and host-facing diagnostics. */
        val reason: TraceUploadRejection,
    ) : TraceUploadOutcome

    /** A transient network, throttling, or server failure allows bounded retry. */
    data object RetryableFailure : TraceUploadOutcome
}
