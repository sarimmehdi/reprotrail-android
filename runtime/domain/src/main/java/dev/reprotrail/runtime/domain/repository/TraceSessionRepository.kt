package dev.reprotrail.runtime.domain.repository

import dev.reprotrail.runtime.domain.model.StoredTrace
import dev.reprotrail.runtime.domain.model.StoredTraceAction
import dev.reprotrail.runtime.domain.model.StoredTraceSession
import dev.reprotrail.runtime.domain.model.StoredTraceSessionState

/** Defines bounded durable trace-session operations available to the runtime facade. */
interface TraceSessionRepository {
    /** Persists a new session and prunes sessions beyond the retention bound. */
    suspend fun startSession(
        session: StoredTraceSession,
        retainedSessionCount: Int,
    )

    /** Persists an active or paused lifecycle transition. */
    suspend fun updateSessionState(
        sessionId: String,
        state: StoredTraceSessionState,
    )

    /** Persists the privacy-safe replay environment for a session. */
    suspend fun updateEnvironment(
        sessionId: String,
        environmentJson: String,
    )

    /** Atomically accepts an action when capacity remains or records one dropped action. */
    suspend fun appendAction(
        action: StoredTraceAction,
        maximumActionCount: Int,
    ): Boolean

    /** Records actions rejected before persistence by the bounded runtime queue. */
    suspend fun recordDroppedActions(
        sessionId: String,
        droppedActionCount: Int,
    )

    /** Finalizes session timing and makes the session eligible for export. */
    suspend fun completeSession(
        sessionId: String,
        endedAt: String,
        durationMs: Long,
    )

    /** Loads one session and its actions in canonical order. */
    suspend fun loadSession(sessionId: String): StoredTrace?

    /** Loads the newest completed session eligible for export. */
    suspend fun loadLatestCompletedSession(): StoredTrace?

    /** Deletes one session and every action owned by it. */
    suspend fun deleteSession(sessionId: String)

    /** Deletes every locally retained trace and action. */
    suspend fun deleteAllSessions()
}
