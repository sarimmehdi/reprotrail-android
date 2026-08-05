package dev.reprotrail.runtime

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReproTrailIsolationTest {
    @After
    fun cleanUpHostKoin() {
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    @Test
    fun `sdk lifecycle never registers a global Koin context`() {
        assertNull(GlobalContext.getOrNull())

        val runtime = ReproTrail.create(application(), ReproTrailConfig(policyVersion = "test-policy"))

        assertEquals("test-policy", runtime.configuration.policyVersion)
        assertNull(GlobalContext.getOrNull())

        runtime.close()

        assertNull(GlobalContext.getOrNull())
    }

    @Test
    fun `host Koin remains usable while isolated sdk instances come and go`() {
        val host = startKoin { modules(module { single { HostDependency("host") } }) }.koin
        val first = ReproTrail.create(application(), ReproTrailConfig(policyVersion = "first"))
        val second = ReproTrail.create(application(), ReproTrailConfig(policyVersion = "second"))

        assertNotNull(GlobalContext.getOrNull())
        assertEquals("host", host.get<HostDependency>().value)
        assertEquals("first", first.configuration.policyVersion)
        assertEquals("second", second.configuration.policyVersion)

        first.close()
        assertEquals("host", host.get<HostDependency>().value)
        second.close()
        assertEquals("host", host.get<HostDependency>().value)
    }

    private fun application(): Application = RuntimeEnvironment.getApplication()

    private data class HostDependency(
        val value: String,
    )
}
