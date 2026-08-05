package dev.reprotrail.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapGestureDetectorTest {
    private val detector = TapGestureDetector(touchSlopPx = 8f, maxTapDurationMs = 500)

    @Test
    fun `down then nearby up emits one tap at the release point`() {
        assertNull(detector.onEvent(PointerAction.DOWN, 100f, 200f, 1_000))

        assertEquals(TapPoint(104f, 203f), detector.onEvent(PointerAction.UP, 104f, 203f, 1_250))
    }

    @Test
    fun `movement beyond touch slop cancels the gesture`() {
        detector.onEvent(PointerAction.DOWN, 100f, 200f, 1_000)

        assertNull(detector.onEvent(PointerAction.MOVE, 109f, 200f, 1_050))
        assertNull(detector.onEvent(PointerAction.UP, 109f, 200f, 1_100))
    }

    @Test
    fun `cancel and overly long press do not emit taps`() {
        detector.onEvent(PointerAction.DOWN, 10f, 20f, 1_000)
        assertNull(detector.onEvent(PointerAction.CANCEL, 10f, 20f, 1_100))
        assertNull(detector.onEvent(PointerAction.UP, 10f, 20f, 1_150))

        detector.onEvent(PointerAction.DOWN, 10f, 20f, 2_000)
        assertNull(detector.onEvent(PointerAction.UP, 10f, 20f, 2_501))
    }

    @Test
    fun `up without a preceding down is ignored`() {
        assertNull(detector.onEvent(PointerAction.UP, 10f, 20f, 100))
    }
}
