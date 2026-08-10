package dev.reprotrail.runtime

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReproTrailGestureCaptureTest {
    @Test
    fun `long press and swipe use canonical durable wire shapes`() =
        runTest {
            val activity = activity()
            activity.deleteDatabase("reprotrail.db")
            val runtime = ReproTrail.create(activity, ReproTrailConfig(policyVersion = "gesture-policy"))
            runtime.startRecording()

            touch(runtime, activity, Touch(MotionEvent.ACTION_DOWN, 75f, 150f, 1_000))
            touch(runtime, activity, Touch(MotionEvent.ACTION_UP, 75f, 150f, 1_750))
            touch(runtime, activity, Touch(MotionEvent.ACTION_DOWN, 40f, 80f, 2_000))
            touch(runtime, activity, Touch(MotionEvent.ACTION_MOVE, 200f, 400f, 2_100))
            touch(runtime, activity, Touch(MotionEvent.ACTION_UP, 360f, 720f, 2_300))
            runtime.stopRecording()

            val actions =
                Json
                    .parseToJsonElement(runtime.exportLatestTrace().readText())
                    .jsonObject
                    .getValue("actions")
                    .jsonArray
            assertEquals(listOf("longPress", "swipe"), actions.map { it.jsonObject.type() })
            assertEquals(
                750L,
                actions[0]
                    .jsonObject
                    .getValue("durationMs")
                    .jsonPrimitive.content
                    .toLong(),
            )
            val swipe = actions[1].jsonObject
            assertEquals(
                0.1,
                swipe
                    .getValue("start")
                    .jsonObject
                    .getValue("x")
                    .jsonPrimitive.double,
                0.0,
            )
            assertEquals(
                0.9,
                swipe
                    .getValue("end")
                    .jsonObject
                    .getValue("y")
                    .jsonPrimitive.double,
                0.0,
            )

            runtime.close()
            activity.deleteDatabase("reprotrail.db")
        }

    private fun activity(): Activity {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        root.addView(
            Button(activity),
            FrameLayout.LayoutParams(100, 80).apply {
                leftMargin = 50
                topMargin = 120
            },
        )
        activity.setContentView(root)
        activity.window.decorView.measure(exactly(400), exactly(800))
        activity.window.decorView.layout(0, 0, 400, 800)
        return activity
    }

    private fun touch(
        runtime: ReproTrail,
        activity: Activity,
        touch: Touch,
    ) {
        runtime.captureTouchEvent(
            activity,
            MotionEvent.obtain(1_000, touch.eventTime, touch.action, touch.x, touch.y, 0),
        )
    }

    private data class Touch(
        val action: Int,
        val x: Float,
        val y: Float,
        val eventTime: Long,
    )

    private fun kotlinx.serialization.json.JsonObject.type(): String = getValue("type").jsonPrimitive.content

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
