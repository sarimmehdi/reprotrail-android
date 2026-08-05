package dev.reprotrail.runtime

internal enum class PointerAction {
    DOWN,
    MOVE,
    UP,
    CANCEL,
}

internal data class TapPoint(
    val x: Float,
    val y: Float,
)

internal class TapGestureDetector(
    private val touchSlopPx: Float,
    private val maxTapDurationMs: Long,
) {
    private var gesture: GestureStart? = null

    fun onEvent(
        action: PointerAction,
        x: Float,
        y: Float,
        eventTimeMs: Long,
    ): TapPoint? =
        when (action) {
            PointerAction.DOWN -> {
                gesture = GestureStart(x, y, eventTimeMs)
                null
            }
            PointerAction.MOVE -> {
                cancelWhenOutsideSlop(x, y)
                null
            }
            PointerAction.UP -> completeTap(x, y, eventTimeMs)
            PointerAction.CANCEL -> {
                gesture = null
                null
            }
        }

    private fun cancelWhenOutsideSlop(
        x: Float,
        y: Float,
    ) {
        val start = gesture ?: return
        if (distanceSquared(start.x, start.y, x, y) > touchSlopPx * touchSlopPx) gesture = null
    }

    private fun completeTap(
        x: Float,
        y: Float,
        eventTimeMs: Long,
    ): TapPoint? {
        val start = gesture ?: return null
        gesture = null
        val duration = eventTimeMs - start.eventTimeMs
        val stayedWithinSlop = distanceSquared(start.x, start.y, x, y) <= touchSlopPx * touchSlopPx
        return TapPoint(x, y).takeIf { duration in 0..maxTapDurationMs && stayedWithinSlop }
    }

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
    )
}
