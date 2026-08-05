package dev.reprotrail.runtime

import android.content.Context
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
