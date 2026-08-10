package dev.reprotrail.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapGestureDetectorTest {
    private val detector = PointerGestureDetector(touchSlopPx = 8f, maxTapDurationMs = 500)

    @Test
    fun `down then nearby up emits one tap at the release point`() {
        assertNull(detector.onEvent(PointerAction.DOWN, 100f, 200f, 1_000))

        assertEquals(DetectedTap(104f, 203f), detector.onEvent(PointerAction.UP, 104f, 203f, 1_250))
    }

    @Test
    fun `movement beyond touch slop emits one swipe`() {
        detector.onEvent(PointerAction.DOWN, 100f, 200f, 1_000)

        assertNull(detector.onEvent(PointerAction.MOVE, 109f, 200f, 1_050))
        assertEquals(
            DetectedSwipe(startX = 100f, startY = 200f, endX = 180f, endY = 260f, durationMs = 200),
            detector.onEvent(PointerAction.UP, 180f, 260f, 1_200),
        )
    }

    @Test
    fun `stationary hold emits one long press`() {
        detector.onEvent(PointerAction.DOWN, 10f, 20f, 1_000)

        assertEquals(
            DetectedLongPress(x = 12f, y = 22f, durationMs = 750),
            detector.onEvent(PointerAction.UP, 12f, 22f, 1_750),
        )
    }

    @Test
    fun `cancel and gestures beyond the wire duration limit are ignored`() {
        detector.onEvent(PointerAction.DOWN, 10f, 20f, 1_000)
        assertNull(detector.onEvent(PointerAction.CANCEL, 10f, 20f, 1_100))
        assertNull(detector.onEvent(PointerAction.UP, 10f, 20f, 1_150))

        detector.onEvent(PointerAction.DOWN, 10f, 20f, 2_000)
        assertNull(detector.onEvent(PointerAction.UP, 10f, 20f, 62_001))
    }

    @Test
    fun `up without a preceding down is ignored`() {
        assertNull(detector.onEvent(PointerAction.UP, 10f, 20f, 100))
    }
}
