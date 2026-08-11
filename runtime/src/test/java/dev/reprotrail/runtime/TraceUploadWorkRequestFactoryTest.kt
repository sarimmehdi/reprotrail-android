package dev.reprotrail.runtime

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration

class TraceUploadWorkRequestFactoryTest {
    private val factory = TraceUploadWorkRequestFactory(PROJECT_ID, maximumAttempts = 5)

    @Test
    fun `request requires network and uses bounded exponential retry metadata`() {
        val request = factory.create(SESSION_ID)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(Duration.ofSeconds(10).toMillis(), request.workSpec.backoffDelayDuration)
        assertEquals(PROJECT_ID, request.workSpec.input.getString(UPLOAD_PROJECT_ID_KEY))
        assertEquals(SESSION_ID, request.workSpec.input.getString(UPLOAD_SESSION_ID_KEY))
        assertEquals(5, request.workSpec.input.getInt(UPLOAD_MAXIMUM_ATTEMPTS_KEY, 0))
    }

    @Test
    fun `request data never contains credentials or trace content`() {
        val keys =
            factory
                .create(SESSION_ID)
                .workSpec.input.keyValueMap.keys

        assertEquals(
            setOf(UPLOAD_PROJECT_ID_KEY, UPLOAD_SESSION_ID_KEY, UPLOAD_MAXIMUM_ATTEMPTS_KEY),
            keys,
        )
        assertFalse(keys.any { it.contains("token", ignoreCase = true) })
        assertFalse(keys.any { it.contains("content", ignoreCase = true) })
    }

    @Test
    fun `unique name is stable for the same tenant and trace`() {
        assertEquals(
            "reprotrail-upload-$PROJECT_ID-$SESSION_ID",
            factory.uniqueWorkName(SESSION_ID),
        )
    }

    private companion object {
        const val PROJECT_ID = "11111111-1111-1111-1111-111111111111"
        const val SESSION_ID = "22222222-2222-2222-2222-222222222222"
    }
}
