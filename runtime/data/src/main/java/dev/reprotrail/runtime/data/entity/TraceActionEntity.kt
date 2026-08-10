package dev.reprotrail.runtime.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room representation of one canonical privacy-reviewed action. */
@Entity(tableName = "trace_actions")
data class TraceActionEntity(
    /** Stable action UUID. */
    @PrimaryKey(autoGenerate = false)
    val id: String,
    /** Owning trace-session UUID. */
    val sessionId: String,
    /** Canonical zero-based action order. */
    val sequence: Int,
    /** Monotonic offset from session start. */
    val offsetMs: Long,
    /** Canonical serialized action payload. */
    val payloadJson: String,
)
