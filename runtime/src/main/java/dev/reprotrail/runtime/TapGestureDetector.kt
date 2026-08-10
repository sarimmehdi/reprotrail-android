package dev.reprotrail.runtime

internal enum class PointerAction {
    DOWN,
    MOVE,
    UP,
    CANCEL,
}

internal sealed interface DetectedGesture

internal data class DetectedTap(
    val x: Float,
    val y: Float,
) : DetectedGesture

internal data class DetectedLongPress(
    val x: Float,
    val y: Float,
    val durationMs: Long,
) : DetectedGesture

internal data class DetectedSwipe(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
) : DetectedGesture

internal class PointerGestureDetector(
    private val touchSlopPx: Float,
    private val maxTapDurationMs: Long,
    private val maxGestureDurationMs: Long = 60_000,
) {
    private var gesture: GestureStart? = null

    fun onEvent(
        action: PointerAction,
        x: Float,
        y: Float,
        eventTimeMs: Long,
    ): DetectedGesture? =
        when (action) {
            PointerAction.DOWN -> {
                gesture = GestureStart(x, y, eventTimeMs)
                null
            }
            PointerAction.MOVE -> {
                recordMovement(x, y)
                null
            }
            PointerAction.UP -> completeGesture(x, y, eventTimeMs)
            PointerAction.CANCEL -> {
                gesture = null
                null
            }
        }

    private fun recordMovement(
        x: Float,
        y: Float,
    ) {
        val start = gesture ?: return
        if (movedOutsideSlop(start, x, y)) start.movedBeyondSlop = true
    }

    private fun completeGesture(
        x: Float,
        y: Float,
        eventTimeMs: Long,
    ): DetectedGesture? {
        val start = gesture
        gesture = null
        return start?.let { classifyGesture(it, x, y, eventTimeMs) }
    }

    private fun classifyGesture(
        start: GestureStart,
        x: Float,
        y: Float,
        eventTimeMs: Long,
    ): DetectedGesture? {
        val duration = eventTimeMs - start.eventTimeMs
        if (duration !in 0..maxGestureDurationMs) return null
        val moved = start.movedBeyondSlop || movedOutsideSlop(start, x, y)
        return when {
            moved && duration > 0 && (start.x != x || start.y != y) ->
                DetectedSwipe(start.x, start.y, x, y, duration)
            !moved && duration <= maxTapDurationMs -> DetectedTap(x, y)
            !moved && duration > maxTapDurationMs -> DetectedLongPress(x, y, duration)
            else -> null
        }
    }

    private fun movedOutsideSlop(
        start: GestureStart,
        x: Float,
        y: Float,
    ): Boolean = distanceSquared(start.x, start.y, x, y) > touchSlopPx * touchSlopPx

    private fun distanceSquared(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): Float {
        val deltaX = endX - startX
        val deltaY = endY - startY
        return deltaX * deltaX + deltaY * deltaY
    }

    private data class GestureStart(
        val x: Float,
        val y: Float,
        val eventTimeMs: Long,
        var movedBeyondSlop: Boolean = false,
    )
}
