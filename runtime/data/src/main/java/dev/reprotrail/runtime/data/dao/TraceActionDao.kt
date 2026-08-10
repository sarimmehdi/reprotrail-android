package dev.reprotrail.runtime.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.reprotrail.runtime.data.entity.TraceActionEntity

/** Reads and writes bounded canonical actions for trace sessions. */
@Dao
interface TraceActionDao {
    /** Counts actions currently retained for one session. */
    @Query("SELECT COUNT(*) FROM trace_actions WHERE sessionId = :sessionId")
    suspend fun countActions(sessionId: String): Int

    /** Inserts one accepted canonical action. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAction(action: TraceActionEntity)

    /** Loads one session's actions in canonical order. */
    @Query("SELECT * FROM trace_actions WHERE sessionId = :sessionId ORDER BY sequence ASC")
    suspend fun findActions(sessionId: String): List<TraceActionEntity>

    /** Deletes actions owned by one trace session. */
    @Query("DELETE FROM trace_actions WHERE sessionId = :sessionId")
    suspend fun deleteActionsForSession(sessionId: String)

    /** Deletes all retained action rows. */
    @Query("DELETE FROM trace_actions")
    suspend fun deleteAllActions()
}
