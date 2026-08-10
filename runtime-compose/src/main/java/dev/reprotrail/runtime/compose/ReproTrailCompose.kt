package dev.reprotrail.runtime.compose

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import dev.reprotrail.runtime.ReproTrailPixelBounds
import dev.reprotrail.runtime.ReproTrailTaggedTarget
import dev.reprotrail.runtime.ReproTrailTargetResolver

/** Optional bridge from explicit Compose test tags to ReproTrail's canonical testTag selector. */
public object ReproTrailCompose {
    /** Returns a stateless resolver suitable for [dev.reprotrail.runtime.ReproTrailConfig.targetResolvers]. */
    public fun targetResolver(): ReproTrailTargetResolver = ComposeSemanticsTargetResolver
}

private object ComposeSemanticsTargetResolver : ReproTrailTargetResolver {
    override fun resolve(
        root: View,
        x: Float,
        y: Float,
    ): ReproTrailTaggedTarget? {
        val rootLocation = IntArray(2).also(root::getLocationInWindow)
        val pointInWindow = Offset(x + rootLocation[0], y + rootLocation[1])
        return root
            .descendantsAndSelf()
            .mapNotNull(View::semanticsOwnerOrNull)
            .flatMap { it.getAllSemanticsNodes(mergingEnabled = false).asSequence() }
            .mapNotNull { it.taggedAt(pointInWindow, rootLocation) }
            .minByOrNull { it.bounds.area() }
    }
}

private fun View.semanticsOwnerOrNull(): SemanticsOwner? =
    runCatching {
        javaClass.methods
            .firstOrNull { method ->
                method.parameterCount == 0 && SemanticsOwner::class.java.isAssignableFrom(method.returnType)
            }?.invoke(this) as? SemanticsOwner
    }.getOrNull()

private fun View.descendantsAndSelf(): Sequence<View> =
    sequence {
        yield(this@descendantsAndSelf)
        if (this@descendantsAndSelf is ViewGroup) {
            repeat(childCount) { index -> yieldAll(getChildAt(index).descendantsAndSelf()) }
        }
    }

private fun SemanticsNode.taggedAt(
    pointInWindow: Offset,
    rootLocation: IntArray,
): ReproTrailTaggedTarget? {
    val tag = config.getOrNull(SemanticsProperties.TestTag)
    val bounds = boundsInWindow
    return if (tag != null && !bounds.isEmpty && bounds.contains(pointInWindow)) {
        ReproTrailTaggedTarget(
            testTag = tag,
            component = "compose.SemanticsNode",
            bounds =
                ReproTrailPixelBounds(
                    left = bounds.left - rootLocation[0],
                    top = bounds.top - rootLocation[1],
                    right = bounds.right - rootLocation[0],
                    bottom = bounds.bottom - rootLocation[1],
                ),
        )
    } else {
        null
    }
}

private fun ReproTrailPixelBounds.area(): Float = (right - left) * (bottom - top)
