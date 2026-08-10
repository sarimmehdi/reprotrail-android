package dev.reprotrail.runtime

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReproTrailCaptureTest {
    @Test
    fun `completed tap trace survives recorder recreation`() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val content = FrameLayout(activity)
            val button = Button(activity).apply { id = R.id.reprotrail_test_target }
            content.addView(button, frame(width = 100, height = 80, left = 50, top = 120))
            activity.setContentView(content)
            layout(activity.window.decorView)
            ReproTrail.setReplayId(button, "checkout.submit")
            activity.deleteDatabase("reprotrail.db")
            val runtime =
                ReproTrail.create(
                    activity,
                    ReproTrailConfig(
                        policyVersion = "test-policy",
                        storage = ReproTrailStorageConfig(maxRetainedSessions = 2, maxActionsPerSession = 10),
                    ),
                )

            runtime.captureTouchEvent(activity, event(MotionEvent.ACTION_DOWN, 75f, 150f, 900))
            runtime.captureTouchEvent(activity, event(MotionEvent.ACTION_UP, 75f, 150f, 950))
            assertEquals(ReproTrailRecordingState.IDLE, runtime.recordingState)

            val sessionId = runtime.startRecording()
            runtime.captureTouchEvent(activity, event(MotionEvent.ACTION_DOWN, 75f, 150f, 1_000))
            runtime.captureTouchEvent(activity, event(MotionEvent.ACTION_UP, 75f, 150f, 1_100))
            runtime.stopRecording()

            val output = runtime.exportLatestTrace()
            val document = assertTapTrace(output, sessionId)
            assertEquals(ReproTrailRecordingState.IDLE, runtime.recordingState)

            runtime.close()

            val recovered = ReproTrail.create(activity, ReproTrailConfig(policyVersion = "test-policy"))
            val recoveredDocument = Json.parseToJsonElement(recovered.exportLatestTrace().readText())
            assertEquals(document, recoveredDocument)
            recovered.deleteAllTraces()
            assertTrue(runCatching { recovered.exportLatestTrace() }.isFailure)
            recovered.close()
            activity.deleteDatabase("reprotrail.db")
        }

    private fun assertTapTrace(
        output: java.io.File,
        sessionId: String,
    ): JsonElement {
        val document = Json.parseToJsonElement(output.readText()).jsonObject
        val actions = document.getValue("actions").jsonArray
        val action = actions.single().jsonObject
        val selectors =
            action
                .getValue("target")
                .jsonObject
                .getValue("selectors")
                .jsonArray
        val privacy = document.getValue("privacy").jsonObject
        val replayId =
            selectors
                .first()
                .jsonObject
                .getValue("value")
                .jsonPrimitive.content

        assertTrue(output.path.contains("reprotrail"))
        assertEquals("1.0.0-alpha.1", document.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("test-policy", privacy.getValue("policyVersion").jsonPrimitive.content)
        assertEquals(1, actions.size)
        assertEquals("tap", action.getValue("type").jsonPrimitive.content)
        assertEquals("checkout.submit", replayId)
        assertEquals(
            sessionId,
            document
                .getValue("session")
                .jsonObject
                .getValue("id")
                .jsonPrimitive.content,
        )
        return document
    }

    @Test
    fun `storage bounds reject unsafe values`() {
        assertTrue(runCatching { ReproTrailStorageConfig(maxRetainedSessions = 0) }.isFailure)
        assertTrue(runCatching { ReproTrailStorageConfig(maxActionsPerSession = 0) }.isFailure)
        assertTrue(runCatching { ReproTrailStorageConfig(maxPendingActions = 0) }.isFailure)
    }

    private fun event(
        action: Int,
        x: Float,
        y: Float,
        eventTime: Long,
    ): MotionEvent = MotionEvent.obtain(1_000, eventTime, action, x, y, 0)

    private fun layout(root: View) {
        root.measure(exactly(400), exactly(800))
        root.layout(0, 0, 400, 800)
    }

    private fun frame(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
    ): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(width, height).apply {
            leftMargin = left
            topMargin = top
        }

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
