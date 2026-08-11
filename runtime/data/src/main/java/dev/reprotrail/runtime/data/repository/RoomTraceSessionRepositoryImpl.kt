package dev.reprotrail.runtime.data.repository

import androidx.room.withTransaction
import dev.reprotrail.runtime.data.database.ReproTrailDatabase
import dev.reprotrail.runtime.data.entity.TraceActionEntity
import dev.reprotrail.runtime.data.entity.TraceSessionEntity
import dev.reprotrail.runtime.domain.model.StoredTrace
import dev.reprotrail.runtime.domain.model.StoredTraceAction
import dev.reprotrail.runtime.domain.model.StoredTraceSession
import dev.reprotrail.runtime.domain.model.StoredTraceSessionState
import dev.reprotrail.runtime.domain.model.StoredTraceUploadState
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository

/** Implements bounded trace-session persistence with atomic Room transactions. */
class RoomTraceSessionRepositoryImpl(
    private val database: ReproTrailDatabase,
) : TraceSessionRepository {
    override suspend fun startSession(
        session: StoredTraceSession,
        retainedSessionCount: Int,
    ) = database.withTransaction {
        require(retainedSessionCount > 0) { "At least one trace session must be retained." }
        val sessionDao = database.traceSessionDao()
        val actionDao = database.traceActionDao()
        sessionDao.insertSession(session.toEntity())
        sessionDao.findSessionIdsBeyondLimit(retainedSessionCount).forEach { expiredSessionId ->
            actionDao.deleteActionsForSession(expiredSessionId)
            sessionDao.deleteSessionById(expiredSessionId)
        }
    }

    override suspend fun updateSessionState(
        sessionId: String,
        state: StoredTraceSessionState,
    ) = database.traceSessionDao().updateSessionState(sessionId, state.name)

    override suspend fun updateEnvironment(
        sessionId: String,
        environmentJson: String,
    ) = database.traceSessionDao().updateEnvironment(sessionId, environmentJson)

    override suspend fun appendAction(
        action: StoredTraceAction,
        maximumActionCount: Int,
    ): Boolean =
        database.withTransaction {
            require(maximumActionCount > 0) { "A trace session must accept at least one action." }
            val sessionDao = database.traceSessionDao()
            val actionDao = database.traceActionDao()
            if (actionDao.countActions(action.sessionId) >= maximumActionCount) {
                sessionDao.incrementDroppedActionCountBy(action.sessionId, 1)
                false
            } else {
                actionDao.insertAction(action.toEntity())
                true
            }
        }

    override suspend fun recordDroppedActions(
        sessionId: String,
        droppedActionCount: Int,
    ) = run {
        require(droppedActionCount >= 0) { "A dropped action count cannot be negative." }
        if (droppedActionCount > 0) {
            database.traceSessionDao().incrementDroppedActionCountBy(sessionId, droppedActionCount)
        }
    }

    override suspend fun completeSession(
        sessionId: String,
        endedAt: String,
        durationMs: Long,
    ) = database.traceSessionDao().completeSession(sessionId, endedAt, durationMs)

    override suspend fun loadSession(sessionId: String): StoredTrace? =
        database.withTransaction {
            val session = database.traceSessionDao().findSession(sessionId) ?: return@withTransaction null
            StoredTrace(
                session = session.toDomain(),
                actions = database.traceActionDao().findActions(sessionId).map(TraceActionEntity::toDomain),
            )
        }

    override suspend fun loadLatestCompletedSession(): StoredTrace? =
        database.withTransaction {
            val session = database.traceSessionDao().findLatestCompletedSession() ?: return@withTransaction null
            StoredTrace(
                session = session.toDomain(),
                actions = database.traceActionDao().findActions(session.id).map(TraceActionEntity::toDomain),
            )
        }

    override suspend fun deleteSession(sessionId: String) =
        database.withTransaction {
            database.traceActionDao().deleteActionsForSession(sessionId)
            database.traceSessionDao().deleteSessionById(sessionId)
        }

    override suspend fun deleteAllSessions() =
        database.withTransaction {
            database.traceActionDao().deleteAllActions()
            database.traceSessionDao().deleteAllSessions()
        }
}

private fun StoredTraceSession.toEntity(): TraceSessionEntity =
    TraceSessionEntity(
        id = id,
        startedAt = startedAt,
        packageName = packageName,
        policyVersion = policyVersion,
        environmentJson = environmentJson,
        state = state.name,
        droppedActionCount = droppedActionCount,
        createdAtEpochMs = createdAtEpochMs,
        endedAt = endedAt,
        durationMs = durationMs,
        uploadState = uploadState.name,
        uploadAttemptCount = uploadAttemptCount,
        uploadFailureReason = uploadFailureReason,
        uploadedAt = uploadedAt,
    )

private fun TraceSessionEntity.toDomain(): StoredTraceSession =
    StoredTraceSession(
        id = id,
        startedAt = startedAt,
        packageName = packageName,
        policyVersion = policyVersion,
        environmentJson = environmentJson,
        state = StoredTraceSessionState.valueOf(state),
        droppedActionCount = droppedActionCount,
        createdAtEpochMs = createdAtEpochMs,
        endedAt = endedAt,
        durationMs = durationMs,
        uploadState = uploadState?.let(StoredTraceUploadState::valueOf) ?: StoredTraceUploadState.NOT_SCHEDULED,
        uploadAttemptCount = uploadAttemptCount ?: 0,
        uploadFailureReason = uploadFailureReason,
        uploadedAt = uploadedAt,
    )

private fun StoredTraceAction.toEntity(): TraceActionEntity =
    TraceActionEntity(
        id = id,
        sessionId = sessionId,
        sequence = sequence,
        offsetMs = offsetMs,
        payloadJson = payloadJson,
    )

private fun TraceActionEntity.toDomain(): StoredTraceAction =
    StoredTraceAction(
        id = id,
        sessionId = sessionId,
        sequence = sequence,
        offsetMs = offsetMs,
        payloadJson = payloadJson,
    )
