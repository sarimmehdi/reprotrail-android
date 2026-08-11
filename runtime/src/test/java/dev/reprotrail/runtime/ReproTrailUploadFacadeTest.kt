package dev.reprotrail.runtime

import android.app.Application
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReproTrailUploadFacadeTest {
    @Test
    fun `upload config requires secure transport by default`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReproTrailUploadConfig(
                baseUrl = "http://example.test/",
                projectId = PROJECT_ID,
                credentialProvider = ReproTrailIngestCredentialProvider { TOKEN },
            )
        }
    }

    @Test
    fun `local development can explicitly allow insecure transport`() {
        val config = uploadConfig()

        assertEquals("http://example.test/", config.baseUrl)
        assertEquals(5, config.maximumAttempts)
    }

    @Test
    fun `unconfigured recorder rejects upload scheduling`() =
        runTest {
            val recorder = ReproTrail.create(application(), ReproTrailConfig(policyVersion = "test-policy"))

            assertThrows(IllegalStateException::class.java) {
                runTest { recorder.enqueueLatestTraceUpload() }
            }

            recorder.close()
        }

    @Test
    fun `completed trace is exposed as enqueued durable work`() =
        runTest {
            WorkManagerTestInitHelper.initializeTestWorkManager(application())
            val recorder =
                ReproTrail.create(
                    application(),
                    ReproTrailConfig(
                        policyVersion = "test-policy",
                        upload = uploadConfig(),
                    ),
                )
            val sessionId = recorder.startRecording()
            recorder.stopRecording()

            val ticket = recorder.enqueueLatestTraceUpload()
            val status = recorder.latestTraceUploadStatus()

            assertEquals(sessionId, ticket.sessionId)
            assertNotNull(WorkManager.getInstance(application()).getWorkInfoById(ticket.workId).get())
            assertEquals(sessionId, status?.sessionId)
            assertEquals(ReproTrailUploadState.ENQUEUED, status?.state)
            assertEquals(0, status?.attemptCount)

            recorder.close()
        }

    private fun uploadConfig(): ReproTrailUploadConfig =
        ReproTrailUploadConfig(
            baseUrl = "http://example.test/",
            projectId = PROJECT_ID,
            credentialProvider = ReproTrailIngestCredentialProvider { TOKEN },
            allowInsecureHttp = true,
        )

    private fun application(): Application = RuntimeEnvironment.getApplication()

    private companion object {
        const val PROJECT_ID = "55555555-5555-5555-5555-555555555555"
        const val TOKEN = "rt_ingest_test.secret"
    }
}
