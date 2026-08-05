package dev.reprotrail.runtime

import android.content.Context
import android.view.View
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configures privacy policy metadata for one recorder instance.
 *
 * @property policyVersion host-defined version of the capture policy applied to every exported trace.
 */
public data class ReproTrailConfig(
    val policyVersion: String,
)

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

    /** Releases only this recorder's private dependency graph. */
    override fun close() {
        if (closed.compareAndSet(false, true)) graph.close()
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
    private val application: KoinApplication =
        koinApplication {
            allowOverride(false)
            modules(
                module {
                    single<Context> { context }
                    single { configuration }
                },
            )
        }

    val configuration: ReproTrailConfig
        get() = application.koin.get()

    override fun close() {
        application.close()
    }
}
