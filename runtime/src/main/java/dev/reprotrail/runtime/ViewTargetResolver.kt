package dev.reprotrail.runtime

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

internal object ViewTargetResolver {
    fun resolve(
        root: View,
        x: Float,
        y: Float,
    ): TraceTarget {
        require(root.width > 0 && root.height > 0) { "The activity content must be laid out before capture." }
        val hit = deepestActionableView(root, x, y, originX = 0, originY = 0) ?: LocatedView(root, 0, 0)
        val selectors = buildSelectors(hit.view, x, y, root.width, root.height)
        return TraceTarget(
            component = hit.view.javaClass.name,
            bounds = normalizedBounds(hit, root.width, root.height),
            selectors = selectors,
        )
    }

    private fun deepestActionableView(
        view: View,
        x: Float,
        y: Float,
        originX: Int,
        originY: Int,
    ): LocatedView? {
        if (view.visibility != View.VISIBLE || view.alpha <= 0f || !contains(view, x, y, originX, originY)) return null
        var deepestChild: LocatedView? = null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                val childOriginX = originX + child.left - view.scrollX
                val childOriginY = originY + child.top - view.scrollY
                deepestChild = deepestActionableView(child, x, y, childOriginX, childOriginY)
                if (deepestChild != null) break
            }
        }
        return deepestChild ?: LocatedView(view, originX, originY).takeIf { isActionable(view) }
    }

    private fun contains(
        view: View,
        x: Float,
        y: Float,
        originX: Int,
        originY: Int,
    ): Boolean = x >= originX && y >= originY && x < originX + view.width && y < originY + view.height

    private fun isActionable(view: View): Boolean =
        view.isClickable ||
            view.isLongClickable ||
            view.id != View.NO_ID ||
            view.getTag(R.id.reprotrail_replay_id_tag) is String

    private fun buildSelectors(
        view: View,
        x: Float,
        y: Float,
        rootWidth: Int,
        rootHeight: Int,
    ): List<TraceSelector> =
        buildList {
            (view.getTag(R.id.reprotrail_replay_id_tag) as? String)
                ?.takeIf(String::isNotBlank)
                ?.let { add(ReplayIdSelector(it)) }
            resourceName(view)?.let { add(ResourceIdSelector(it)) }
            add(
                CoordinateSelector(
                    x = normalize(x, rootWidth),
                    y = normalize(y, rootHeight),
                ),
            )
        }

    private fun resourceName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceName(view.id)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    private fun normalizedBounds(
        hit: LocatedView,
        rootWidth: Int,
        rootHeight: Int,
    ): NormalizedBounds =
        NormalizedBounds(
            left = normalize(hit.originX.toFloat(), rootWidth),
            top = normalize(hit.originY.toFloat(), rootHeight),
            right = normalize((hit.originX + hit.view.width).toFloat(), rootWidth),
            bottom = normalize((hit.originY + hit.view.height).toFloat(), rootHeight),
        )

    private fun normalize(
        value: Float,
        size: Int,
    ): Double = min(1.0, max(0.0, value.toDouble() / size))

    private data class LocatedView(
        val view: View,
        val originX: Int,
        val originY: Int,
    )
}
