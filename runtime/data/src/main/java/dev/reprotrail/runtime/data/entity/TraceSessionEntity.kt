package dev.reprotrail.runtime.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room representation of recoverable trace-session metadata. */
@Entity(tableName = "trace_sessions")
data class TraceSessionEntity(
    /** Stable trace-session UUID. */
    @PrimaryKey(autoGenerate = false)
    val id: String,
    /** RFC 3339 capture start time. */
    val startedAt: String,
    /** Package that produced the trace. */
    val packageName: String,
    /** Privacy policy applied before storage. */
    val policyVersion: String,
    /** Serialized replay environment, when capture has resolved it. */
    val environmentJson: String? = null,
    /** Persisted name of the recording lifecycle state. */
    val state: String,
    /** Eligible actions rejected after reaching the configured bound. */
    val droppedActionCount: Int,
    /** Deterministic local retention ordering key. */
    val createdAtEpochMs: Long,
    /** RFC 3339 completion time. */
    val endedAt: String? = null,
    /** Monotonic duration of a completed session. */
    val durationMs: Long? = null,
)
