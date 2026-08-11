package dev.reprotrail.runtime

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.room.Room
import androidx.work.WorkManager
import dev.reprotrail.runtime.data.database.ReproTrailDatabase
import dev.reprotrail.runtime.data.repository.RoomTraceSessionRepositoryImpl
import dev.reprotrail.runtime.data.repository.RoomTraceUploadStateRepositoryImpl
import dev.reprotrail.runtime.domain.model.StoredTraceUploadState
import dev.reprotrail.runtime.domain.repository.TraceSessionRepository
import dev.reprotrail.runtime.domain.repository.TraceUploadStateRepository
import dev.reprotrail.runtime.upload.data.http.HttpTraceUploader
import dev.reprotrail.runtime.upload.domain.TraceUploader
import dev.reprotrail.runtime.upload.domain.credential.IngestCredentialProvider
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configures privacy policy metadata for one recorder instance.
 *
 * @property policyVersion host-defined version of the capture policy applied to every exported trace.
 * @property storage bounded local persistence configuration.
 * @property privacy host-controlled privacy safety switches.
 * @property targetResolvers optional UI-toolkit bridges such as the Compose adapter.
 * @property upload optional explicit hosted-ingestion configuration.
 */
public data class ReproTrailConfig(
    val policyVersion: String,
    val storage: ReproTrailStorageConfig = ReproTrailStorageConfig(),
    val privacy: ReproTrailPrivacyConfig = ReproTrailPrivacyConfig(),
    val targetResolvers: List<ReproTrailTargetResolver> = emptyList(),
    val upload: ReproTrailUploadConfig? = null,
)

/** Supplies a current ingest credential without coupling the host to ReproTrail's internal DI graph. */
public fun interface ReproTrailIngestCredentialProvider {
    /** Returns the current raw project-scoped ingest credential only when an attempt begins. */
    public suspend fun getCredential(): String
}

/** Configures explicit hosted upload of completed privacy-reviewed traces. */
public data class ReproTrailUploadConfig(
    /** Absolute backend base URL. HTTPS is required unless local development opts out explicitly. */
    val baseUrl: String,
    /** Backend project UUID that scopes credentials and immutable trace storage. */
    val projectId: String,
    /** Host-owned just-in-time credential bridge. Its result is never persisted by ReproTrail. */
    val credentialProvider: ReproTrailIngestCredentialProvider,
    /** Maximum number of worker attempts, including the first request. */
    val maximumAttempts: Int = DEFAULT_MAXIMUM_UPLOAD_ATTEMPTS,
    /** Explicit local-development escape hatch for cleartext HTTP. Never enable in production. */
    val allowInsecureHttp: Boolean = false,
) {
    init {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
        require(uri?.isAbsolute == true && !uri.host.isNullOrBlank()) { "An absolute upload base URL is required." }
        require(uri.scheme == HTTPS_SCHEME || (allowInsecureHttp && uri.scheme == HTTP_SCHEME)) {
            "Hosted upload requires HTTPS unless insecure HTTP is explicitly allowed."
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "The upload base URL must not contain credentials, a query, or a fragment."
        }
        require(runCatching { UUID.fromString(projectId) }.isSuccess) { "The upload project ID must be a UUID." }
        require(maximumAttempts in 1..MAXIMUM_UPLOAD_ATTEMPTS) {
            "Maximum upload attempts must be between 1 and $MAXIMUM_UPLOAD_ATTEMPTS."
        }
    }

    internal val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"

    private companion object {
        const val DEFAULT_MAXIMUM_UPLOAD_ATTEMPTS = 5
        const val MAXIMUM_UPLOAD_ATTEMPTS = 10
        const val HTTP_SCHEME = "http"
        const val HTTPS_SCHEME = "https"
    }
}

/** Public durable hosted-upload lifecycle. */
public enum class ReproTrailUploadState {
    /** The trace has not been scheduled. */
    NOT_SCHEDULED,

    /** Durable work is waiting for its network constraint or retry delay. */
    ENQUEUED,

    /** A worker has begun its current attempt. */
    UPLOADING,

    /** The backend created or had already created the identical immutable trace. */
    SUCCEEDED,

    /** The request was rejected permanently or exhausted bounded retries. */
    REJECTED,
}

/** Snapshot of the latest completed trace's durable hosted-upload lifecycle. */
public data class ReproTrailUploadStatus(
    /** Stable trace session UUID. */
    val sessionId: String,
    /** Last locally persisted upload lifecycle state. */
    val state: ReproTrailUploadState,
    /** Number of attempts that reached a worker. */
    val attemptCount: Int,
    /** Stable safe rejection category, without backend response content. */
    val failureReason: String?,
    /** RFC 3339 successful ingestion time, when available. */
    val uploadedAt: String?,
)

/** Identifies newly enqueued unique work and its immutable trace session. */
public data class ReproTrailUploadTicket(
    /** Stable trace session UUID used as the backend idempotency key. */
    val sessionId: String,
    /** WorkManager identifier for optional host-side observation. */
    val workId: UUID,
)

/** Configures privacy controls that are evaluated before an action can be persisted. */
public data class ReproTrailPrivacyConfig(
    /** Whether a host may start or resume capture when this recorder is created. */
    val captureEnabledAtStartup: Boolean = true,
    /** Exact visible strings approved for text or content-description selectors. */
    val visibleSelectorAllowlist: Set<String> = emptySet(),
) {
    init {
        require(visibleSelectorAllowlist.all { it.isNotBlank() && it.length <= MAX_VISIBLE_SELECTOR_LENGTH }) {
            "Visible selector allowlist entries must contain between 1 and 1,000 non-blank characters."
        }
    }

    private companion object {
        const val MAX_VISIBLE_SELECTOR_LENGTH = 1_000
    }
}

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
@Suppress("TooManyFunctions")
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

    /** Whether privacy policy currently permits capture to start or resume. */
    public val isCaptureEnabled: Boolean
        get() = graph.captureRuntime.isCaptureEnabled

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

    /** Applies a host or remote privacy kill switch without silently resuming capture. */
    public suspend fun setCaptureEnabled(enabled: Boolean) {
        checkOpen()
        graph.captureRuntime.setCaptureEnabled(enabled)
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

    /** Enqueues the newest completed trace for explicit, bounded hosted upload. */
    public suspend fun enqueueLatestTraceUpload(): ReproTrailUploadTicket {
        checkOpen()
        val scheduler =
            checkNotNull(graph.uploadScheduler) {
                "Hosted upload is not configured for this ReproTrail recorder."
            }
        val trace =
            checkNotNull(graph.traceRepository.loadLatestCompletedSession()) {
                "Complete at least one trace session before scheduling upload."
            }
        return ReproTrailUploadTicket(trace.session.id, scheduler.enqueue(trace.session.id))
    }

    /** Returns durable upload state for the newest completed trace, if one exists. */
    public suspend fun latestTraceUploadStatus(): ReproTrailUploadStatus? {
        checkOpen()
        return graph.traceRepository.loadLatestCompletedSession()?.session?.let { session ->
            ReproTrailUploadStatus(
                sessionId = session.id,
                state = session.uploadState.toPublicState(),
                attemptCount = session.uploadAttemptCount,
                failureReason = session.uploadFailureReason,
                uploadedAt = session.uploadedAt,
            )
        }
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
                    single<TraceUploadStateRepository> { RoomTraceUploadStateRepositoryImpl(database) }
                    single { TraceCaptureRuntime(get(), get(), get()) }
                    configuration.upload?.let { upload ->
                        single<TraceUploader> {
                            HttpTraceUploader(
                                baseUrl = upload.normalizedBaseUrl,
                                credentialProvider =
                                    IngestCredentialProvider {
                                        upload.credentialProvider.getCredential().also { credential ->
                                            require(
                                                credential.isNotBlank(),
                                            ) { "The ingest credential must not be blank." }
                                        }
                                    },
                            )
                        }
                        single {
                            TraceUploadCoordinator(
                                projectId = upload.projectId,
                                traceRepository = get(),
                                stateRepository = get(),
                                uploader = get(),
                                contentEncoder = TraceContentEncoder { TraceJson.encode(it.toTraceDocument()) },
                            )
                        }
                        single { TraceUploadWorkRequestFactory(upload.projectId, upload.maximumAttempts) }
                        single { TraceUploadWorkScheduler(WorkManager.getInstance(context), get(), get(), get()) }
                    }
                },
            )
        }

    private val registeredUploadCoordinator: TraceUploadCoordinator? =
        configuration.upload?.let { upload ->
            application.koin.get<TraceUploadCoordinator>().also { coordinator ->
                TraceUploadRuntimeRegistry.register(upload.projectId, coordinator)
            }
        }

    val configuration: ReproTrailConfig
        get() = application.koin.get()

    val captureRuntime: TraceCaptureRuntime
        get() = application.koin.get()

    val traceRepository: TraceSessionRepository
        get() = application.koin.get()

    val uploadScheduler: TraceUploadWorkScheduler?
        get() = configuration.upload?.let { application.koin.get() }

    override fun close() {
        configuration.upload?.let { upload ->
            registeredUploadCoordinator?.let { TraceUploadRuntimeRegistry.unregister(upload.projectId, it) }
        }
        captureRuntime.close()
        database.close()
        application.close()
    }
}

private fun StoredTraceUploadState.toPublicState(): ReproTrailUploadState = ReproTrailUploadState.valueOf(name)
