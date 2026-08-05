package dev.reprotrail.runtime

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import kotlinx.serialization.json.Json
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
    fun `activity touch stream exports one semantic tap trace`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = FrameLayout(activity)
        val button = Button(activity).apply { id = R.id.reprotrail_test_target }
        content.addView(button, frame(width = 100, height = 80, left = 50, top = 120))
        activity.setContentView(content)
        layout(activity.window.decorView)
        ReproTrail.setReplayId(button, "checkout.submit")
        val runtime = ReproTrail.create(activity, ReproTrailConfig(policyVersion = "test-policy"))

        runtime.captureTouchEvent(activity, event(MotionEvent.ACTION_DOWN, 75f, 150f, 1_000))
        runtime.captureTouchEvent(activity, event(MotionEvent.ACTION_UP, 75f, 150f, 1_100))

        val output = runtime.exportLatestTrace()
        val document = Json.parseToJsonElement(output.readText()).jsonObject
        val actions = document.getValue("actions").jsonArray
        val action = actions.single().jsonObject
        val target = action.getValue("target").jsonObject
        val selectors = target.getValue("selectors").jsonArray
        val privacy = document.getValue("privacy").jsonObject
        val replayId =
            selectors
                .first()
                .jsonObject
                .getValue("value")
                .jsonPrimitive
                .content

        assertTrue(output.path.contains("reprotrail"))
        assertEquals("1.0.0-alpha.1", document.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("test-policy", privacy.getValue("policyVersion").jsonPrimitive.content)
        assertEquals(1, actions.size)
        assertEquals("tap", action.getValue("type").jsonPrimitive.content)
        assertEquals("checkout.submit", replayId)

        runtime.close()
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
