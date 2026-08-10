package dev.reprotrail.runtime

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import dev.reprotrail.runtime.domain.model.StoredTraceSession
import dev.reprotrail.runtime.domain.model.StoredTraceSessionState
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.max

internal class TraceCaptureRuntime(
    private val context: Context,
    private val configuration: ReproTrailConfig,
    private val repository: TraceSessionRepository,
) : AutoCloseable {
    private val lifecycleMutex = Mutex()
    private val stateLock = Any()
    private val worker = TracePersistenceWorker(repository, configuration.storage)
    private var activeSession: ActiveSession? = null

    @Volatile
    var recordingState: ReproTrailRecordingState = ReproTrailRecordingState.IDLE
        private set

    suspend fun startRecording(): String =
        lifecycleMutex.withLock {
            check(recordingState == ReproTrailRecordingState.IDLE) { "A trace session is already active." }
            val session = newSession()
            repository.startSession(
                session =
                    StoredTraceSession(
                        id = session.id,
                        startedAt = session.startedAt,
                        packageName = context.packageName,
                        policyVersion = configuration.policyVersion,
                        environmentJson = null,
                        state = StoredTraceSessionState.ACTIVE,
                        droppedActionCount = 0,
                        createdAtEpochMs = System.currentTimeMillis(),
                        endedAt = null,
                        durationMs = null,
                    ),
                retainedSessionCount = configuration.storage.maxRetainedSessions,
            )
            synchronized(stateLock) {
                activeSession = session
                recordingState = ReproTrailRecordingState.RECORDING
            }
            session.id
        }

    suspend fun pauseRecording() =
        lifecycleMutex.withLock {
            val session = requireSession(ReproTrailRecordingState.RECORDING)
            synchronized(stateLock) { recordingState = ReproTrailRecordingState.PAUSED }
            repository.updateSessionState(session.id, StoredTraceSessionState.PAUSED)
        }

    suspend fun resumeRecording() =
        lifecycleMutex.withLock {
            val session = requireSession(ReproTrailRecordingState.PAUSED)
            repository.updateSessionState(session.id, StoredTraceSessionState.ACTIVE)
            synchronized(stateLock) { recordingState = ReproTrailRecordingState.RECORDING }
        }

    suspend fun stopRecording() =
        lifecycleMutex.withLock {
            val session =
                synchronized(stateLock) {
                    check(recordingState != ReproTrailRecordingState.IDLE) { "No trace session is active." }
                    checkNotNull(activeSession).also {
                        activeSession = null
                        recordingState = ReproTrailRecordingState.IDLE
                    }
                }
            worker.completeSession(session.id, session.startedElapsedMs)
        }

    fun capture(
        root: View,
        event: MotionEvent,
    ) {
        val pending =
            synchronized(stateLock) {
                val session = activeSession.takeIf { recordingState == ReproTrailRecordingState.RECORDING }
                val pointerAction = event.toPointerAction()
                if (session == null || pointerAction == null) {
                    null
                } else {
                    session.detector.onEvent(pointerAction, event.x, event.y, event.eventTime)?.let { tap ->
                        PersistAction(
                            sessionId = session.id,
                            action =
                                TapAction(
                                    id = newId(),
                                    sequence = session.nextSequence++,
                                    offsetMs = max(0, event.eventTime - session.startedElapsedMs),
                                    target = ViewTargetResolver.resolve(root, tap.x, tap.y),
                                ),
                            environmentJson = TraceJson.encodeEnvironment(EnvironmentCapture.from(context, root)),
                        )
                    }
                }
            }
        pending?.let(worker::enqueue)
    }

    suspend fun exportLatestTrace(): File {
        val trace =
            checkNotNull(repository.loadLatestCompletedSession()) {
                "Complete at least one trace session before exporting."
            }
        val environment =
            checkNotNull(trace.session.environmentJson) {
                "The latest completed trace does not contain an eligible action."
            }
        val document =
            TraceDocument(
                session =
                    TraceSession(
                        id = trace.session.id,
                        startedAt = trace.session.startedAt,
                        endedAt = trace.session.endedAt,
                        durationMs = trace.session.durationMs,
                    ),
                application = TraceApplication(packageName = trace.session.packageName),
                environment = TraceJson.decodeEnvironment(environment),
                privacy = TracePrivacy(policyVersion = trace.session.policyVersion),
                actions = trace.actions.map { TraceJson.decodeAction(it.payloadJson) },
            )
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, EXPORT_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Could not create trace export directory." }
        return File(directory, LATEST_TRACE_FILE).apply { writeText(TraceJson.encode(document)) }
    }

    suspend fun deleteAllTraces() {
        check(recordingState == ReproTrailRecordingState.IDLE) { "Stop recording before deleting traces." }
        repository.deleteAllSessions()
    }

    override fun close() {
        worker.close()
    }

    private fun requireSession(requiredState: ReproTrailRecordingState): ActiveSession =
        synchronized(stateLock) {
            check(recordingState == requiredState) { "Recording state must be $requiredState." }
            checkNotNull(activeSession)
        }

    private fun newSession(): ActiveSession =
        ActiveSession(
            id = newId(),
            startedAt = Instant.now().toString(),
            startedElapsedMs = SystemClock.elapsedRealtime(),
            detector =
                TapGestureDetector(
                    touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
                    maxTapDurationMs = MAX_TAP_DURATION_MS,
                ),
        )

    private data class ActiveSession(
        val id: String,
        val startedAt: String,
        val startedElapsedMs: Long,
        val detector: TapGestureDetector,
        var nextSequence: Int = 0,
    )

    private companion object {
        const val MAX_TAP_DURATION_MS = 500L
        const val EXPORT_DIRECTORY = "reprotrail"
        const val LATEST_TRACE_FILE = "latest-trace.json"
    }
}

private fun MotionEvent.toPointerAction(): PointerAction? {
    if (pointerCount > 1) return PointerAction.CANCEL
    return when (actionMasked) {
        MotionEvent.ACTION_DOWN -> PointerAction.DOWN
        MotionEvent.ACTION_MOVE -> PointerAction.MOVE
        MotionEvent.ACTION_UP -> PointerAction.UP
        MotionEvent.ACTION_CANCEL -> PointerAction.CANCEL
        else -> null
    }
}

private fun newId(): String = UUID.randomUUID().toString()
