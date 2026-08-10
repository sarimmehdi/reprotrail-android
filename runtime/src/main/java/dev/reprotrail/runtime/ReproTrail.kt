package dev.reprotrail.runtime

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.room.Room
import dev.reprotrail.runtime.data.database.ReproTrailDatabase
import dev.reprotrail.runtime.data.repository.RoomTraceSessionRepositoryImpl
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configures privacy policy metadata for one recorder instance.
 *
 * @property policyVersion host-defined version of the capture policy applied to every exported trace.
 * @property storage bounded local persistence configuration.
 */
public data class ReproTrailConfig(
    val policyVersion: String,
    val storage: ReproTrailStorageConfig = ReproTrailStorageConfig(),
)

/** Bounds local trace retention and the recorder's non-blocking persistence queue. */
public data class ReproTrailStorageConfig(
    /** Maximum number of sessions retained in the private Room database. */
    val maxRetainedSessions: Int = 10,
    /** Maximum number of durable actions accepted by one session. */
    val maxActionsPerSession: Int = 500,
    /** Maximum number of privacy-safe actions awaiting background persistence. */
    val maxPendingActions: Int = 64,
) {
    init {
        require(maxRetainedSessions > 0) { "At least one trace session must be retained." }
        require(maxActionsPerSession > 0) { "A trace session must accept at least one action." }
        require(maxPendingActions > 0) { "The pending action queue must accept at least one action." }
    }
}

/** Public recording lifecycle states. */
public enum class ReproTrailRecordingState {
    /** No session accepts actions. */
    IDLE,

    /** The active session accepts eligible actions. */
    RECORDING,

    /** The active session remains durable but ignores actions. */
    PAUSED,
}

/**
 * DI-neutral entry point for an isolated ReproTrail recorder instance.
 *
 * Create and close this facade independently of any dependency injection framework used by the host application.
 */
public class ReproTrail private constructor(
    private val graph: IsolatedRuntimeGraph,
) : AutoCloseable {
    /** Configuration owned by this recorder instance. */
    public val configuration: ReproTrailConfig
        get() = graph.configuration

    private val closed = AtomicBoolean(false)

    /** Current opt-in recording state. */
    public val recordingState: ReproTrailRecordingState
        get() = graph.captureRuntime.recordingState

    /** Starts a new durable trace session and returns its stable identifier. */
    public suspend fun startRecording(): String {
        checkOpen()
        return graph.captureRuntime.startRecording()
    }

    /** Pauses action capture for the active session. */
    public suspend fun pauseRecording() {
        checkOpen()
        graph.captureRuntime.pauseRecording()
    }

    /** Resumes action capture for the paused session. */
    public suspend fun resumeRecording() {
        checkOpen()
        graph.captureRuntime.resumeRecording()
    }

    /** Drains pending actions and durably completes the active session. */
    public suspend fun stopRecording() {
        checkOpen()
        graph.captureRuntime.stopRecording()
    }

    /** Observes an Activity touch event without consuming or redispatching it. */
    public fun captureTouchEvent(
        activity: Activity,
        event: MotionEvent,
    ) {
        checkOpen()
        graph.captureRuntime.capture(activity.window.decorView, event)
    }

    /** Exports the newest durably completed trace to the app-specific trace directory. */
    public suspend fun exportLatestTrace(): File {
        checkOpen()
        return graph.captureRuntime.exportLatestTrace()
    }

    /** Deletes every locally retained trace. */
    public suspend fun deleteAllTraces() {
        checkOpen()
        graph.captureRuntime.deleteAllTraces()
    }

    /** Releases only this recorder's private dependency graph. */
    override fun close() {
        if (closed.compareAndSet(false, true)) graph.close()
    }

    private fun checkOpen() {
        check(!closed.get()) { "This ReproTrail recorder is closed." }
    }

    /** Creates isolated recorder instances. */
    public companion object {
        /** Creates a recorder without reading or changing the host application's DI container. */
        public fun create(
            context: Context,
            configuration: ReproTrailConfig,
        ): ReproTrail = ReproTrail(IsolatedRuntimeGraph(context.applicationContext, configuration))

        /** Assigns a stable, privacy-reviewed semantic identity to a host View. */
        public fun setReplayId(
            view: View,
            replayId: String,
        ) {
            require(replayId.isNotBlank() && replayId.length <= MAX_REPLAY_ID_LENGTH) {
                "A replay ID must contain between 1 and 255 non-blank characters."
            }
            view.setTag(R.id.reprotrail_replay_id_tag, replayId)
        }

        private const val MAX_REPLAY_ID_LENGTH = 255
    }
}

private class IsolatedRuntimeGraph(
    context: Context,
    configuration: ReproTrailConfig,
) : AutoCloseable {
    private val database =
        Room.databaseBuilder(context, ReproTrailDatabase::class.java, ReproTrailDatabase.DATABASE_NAME).build()

    private val application: KoinApplication =
        koinApplication {
            allowOverride(false)
            modules(
                module {
                    single<Context> { context }
                    single { configuration }
                    single<TraceSessionRepository> { RoomTraceSessionRepositoryImpl(database) }
                    single { TraceCaptureRuntime(get(), get(), get()) }
                },
            )
        }

    val configuration: ReproTrailConfig
        get() = application.koin.get()

    val captureRuntime: TraceCaptureRuntime
        get() = application.koin.get()

    override fun close() {
        captureRuntime.close()
        database.close()
        application.close()
    }
}
