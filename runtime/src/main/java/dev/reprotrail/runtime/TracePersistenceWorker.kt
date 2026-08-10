package dev.reprotrail.runtime

import android.os.SystemClock
import dev.reprotrail.runtime.domain.model.StoredTraceAction
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

internal data class PersistAction(
    val sessionId: String,
    val action: TraceAction,
    val environmentJson: String,
)

internal class TracePersistenceWorker(
    private val repository: TraceSessionRepository,
    private val storage: ReproTrailStorageConfig,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<PersistenceCommand>(storage.maxPendingActions)
    private val droppedPendingActions = AtomicInteger()

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is PersistActionCommand -> persist(command.pending)
                    is CompleteSessionCommand -> complete(command)
                }
            }
        }
    }

    fun enqueue(action: PersistAction) {
        if (commands.trySend(PersistActionCommand(action)).isFailure) droppedPendingActions.incrementAndGet()
    }

    suspend fun completeSession(
        sessionId: String,
        startedElapsedMs: Long,
    ) {
        val completion = CompletableDeferred<Unit>()
        commands.send(
            CompleteSessionCommand(
                sessionId = sessionId,
                startedElapsedMs = startedElapsedMs,
                droppedActionCount = droppedPendingActions.getAndSet(0),
                completion = completion,
            ),
        )
        completion.await()
    }

    override fun close() {
        commands.close()
        scope.cancel()
    }

    private suspend fun persist(pending: PersistAction) {
        repository.updateEnvironment(pending.sessionId, pending.environmentJson)
        repository.appendAction(
            action =
                StoredTraceAction(
                    id = pending.action.id,
                    sessionId = pending.sessionId,
                    sequence = pending.action.sequence,
                    offsetMs = pending.action.offsetMs,
                    payloadJson = TraceJson.encodeAction(pending.action),
                ),
            maximumActionCount = storage.maxActionsPerSession,
        )
    }

    private suspend fun complete(command: CompleteSessionCommand) {
        runCatching {
            repository.recordDroppedActions(command.sessionId, command.droppedActionCount)
            repository.completeSession(
                sessionId = command.sessionId,
                endedAt = Instant.now().toString(),
                durationMs = max(0, SystemClock.elapsedRealtime() - command.startedElapsedMs),
            )
        }.fold(command.completion::complete, command.completion::completeExceptionally)
    }

    private sealed interface PersistenceCommand

    private data class PersistActionCommand(
        val pending: PersistAction,
    ) : PersistenceCommand

    private data class CompleteSessionCommand(
        val sessionId: String,
        val startedElapsedMs: Long,
        val droppedActionCount: Int,
        val completion: CompletableDeferred<Unit>,
    ) : PersistenceCommand
}
