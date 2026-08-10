package dev.reprotrail.runtime

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReproTrailHostCompatibilityTest {
    @Test
    fun `host without DI creates and owns the public facade`() {
        val recorder = ReproTrail.create(application(), config("no-di"))

        assertEquals("no-di", recorder.configuration.policyVersion)

        recorder.close()
    }

    @Test
    fun `manual composition root creates and owns the public facade`() {
        val root = ManualCompositionRoot(application())

        assertEquals("manual", root.reproTrail.configuration.policyVersion)

        root.close()
    }

    @Test
    fun `Hilt provider depends only on the public facade`() {
        val recorder = ReproTrailHiltModule.provideReproTrail(application())

        assertEquals("hilt", recorder.configuration.policyVersion)

        recorder.close()
    }

    @Test
    fun `host Koin can own the facade without sharing its private graph`() {
        val hostApplication =
            koinApplication {
                modules(
                    module {
                        single { ReproTrail.create(application(), config("host-koin")) }
                    },
                )
            }
        val recorder = hostApplication.koin.get<ReproTrail>()

        assertEquals("host-koin", recorder.configuration.policyVersion)

        recorder.close()
        hostApplication.close()
    }

    private fun application(): Application = RuntimeEnvironment.getApplication()

    private class ManualCompositionRoot(
        context: Context,
    ) : AutoCloseable {
        val reproTrail: ReproTrail = ReproTrail.create(context, config("manual"))

        override fun close() {
            reproTrail.close()
        }
    }

    @Module
    @InstallIn(ActivityRetainedComponent::class)
    private object ReproTrailHiltModule {
        @Provides
        @ActivityRetainedScoped
        fun provideReproTrail(
            @ApplicationContext context: Context,
        ): ReproTrail = ReproTrail.create(context, config("hilt"))
    }

    private companion object {
        fun config(policyVersion: String): ReproTrailConfig = ReproTrailConfig(policyVersion)
    }
}
