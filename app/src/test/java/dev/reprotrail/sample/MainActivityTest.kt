package dev.reprotrail.sample

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityTest {
    @Test
    fun `tapping the fixture target exports and displays a trace path`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        layout(activity.window.decorView)
        val target = activity.findViewById<View>(R.id.capture_target)
        val position = IntArray(2).also(target::getLocationInWindow)
        val x = position[0] + target.width / 2f
        val y = position[1] + target.height / 2f

        activity.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, x, y, 1_000))
        activity.dispatchTouchEvent(event(MotionEvent.ACTION_UP, x, y, 1_100))
        waitForExport(activity)

        val status = activity.findViewById<TextView>(R.id.capture_status).text.toString()
        assertTrue(status.contains("Captured tap 1"))
        assertTrue(status.contains("latest-trace.json"))
    }

    private fun event(
        action: Int,
        x: Float,
        y: Float,
        eventTime: Long,
    ): MotionEvent = MotionEvent.obtain(1_000, eventTime, action, x, y, 0)

    private fun layout(root: View) {
        root.measure(exactly(1_080), exactly(1_920))
        root.layout(0, 0, 1_080, 1_920)
    }

    private fun waitForExport(activity: MainActivity) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            val status = activity.findViewById<TextView>(R.id.capture_status).text.toString()
            if (status.contains("latest-trace.json")) return
            Thread.sleep(10)
        }
    }

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
