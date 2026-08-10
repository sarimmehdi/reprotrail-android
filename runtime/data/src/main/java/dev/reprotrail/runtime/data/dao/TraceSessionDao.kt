package dev.reprotrail.runtime.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.reprotrail.runtime.data.entity.TraceSessionEntity

/** Performs atomic trace-session and action persistence operations. */
@Dao
interface TraceSessionDao {
    /** Inserts a newly started trace session. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: TraceSessionEntity)

    /** Persists a recording lifecycle transition. */
    @Query("UPDATE trace_sessions SET state = :state WHERE id = :sessionId")
    suspend fun updateSessionState(
        sessionId: String,
        state: String,
    )

    /** Stores the resolved replay environment. */
    @Query("UPDATE trace_sessions SET environmentJson = :environmentJson WHERE id = :sessionId")
    suspend fun updateEnvironment(
        sessionId: String,
        environmentJson: String,
    )

    /** Records one action rejected by the session bound. */
    @Query(
        "UPDATE trace_sessions SET droppedActionCount = droppedActionCount + 1 " +
            "WHERE id = :sessionId",
    )
    suspend fun incrementDroppedActionCount(sessionId: String)

    /** Finalizes timing and marks a session exportable. */
    @Query(
        "UPDATE trace_sessions SET state = 'COMPLETED', endedAt = :endedAt, " +
            "durationMs = :durationMs WHERE id = :sessionId",
    )
    suspend fun completeSession(
        sessionId: String,
        endedAt: String,
        durationMs: Long,
    )

    /** Loads one trace session by UUID. */
    @Query("SELECT * FROM trace_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun findSession(sessionId: String): TraceSessionEntity?

    /** Finds oldest session UUIDs beyond the retention bound. */
    @Query(
        "SELECT id FROM trace_sessions ORDER BY createdAtEpochMs DESC, id DESC " +
            "LIMIT -1 OFFSET :retainedSessionCount",
    )
    suspend fun findSessionIdsBeyondLimit(retainedSessionCount: Int): List<String>

    /** Loads the newest completed session metadata. */
    @Query(
        "SELECT * FROM trace_sessions WHERE state = 'COMPLETED' " +
            "ORDER BY createdAtEpochMs DESC, id DESC LIMIT 1",
    )
    suspend fun findLatestCompletedSession(): TraceSessionEntity?

    /** Deletes one trace-session row. */
    @Query("DELETE FROM trace_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    /** Deletes all retained session rows. */
    @Query("DELETE FROM trace_sessions")
    suspend fun deleteAllSessions()
}
