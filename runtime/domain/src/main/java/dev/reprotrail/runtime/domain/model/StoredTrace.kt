package dev.reprotrail.runtime.domain.model

/** Recovered session metadata and its ordered durable action sequence. */
data class StoredTrace(
    /** Recovered metadata for the trace session. */
    val session: StoredTraceSession,
    /** Actions ordered by their canonical sequence. */
    val actions: List<StoredTraceAction>,
)
