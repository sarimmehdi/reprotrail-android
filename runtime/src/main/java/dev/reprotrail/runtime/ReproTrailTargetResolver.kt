package dev.reprotrail.runtime

import android.view.View

/** Optional UI-toolkit bridge that resolves an explicitly tagged target at one window coordinate. */
public fun interface ReproTrailTargetResolver {
    /** Returns a tagged target at the coordinate, or null when this resolver does not own it. */
    public fun resolve(
        root: View,
        x: Float,
        y: Float,
    ): ReproTrailTaggedTarget?
}

/** Toolkit-neutral pixel bounds relative to the supplied root View. */
public data class ReproTrailPixelBounds(
    /** Left edge in root-local pixels. */
    val left: Float,
    /** Top edge in root-local pixels. */
    val top: Float,
    /** Right edge in root-local pixels. */
    val right: Float,
    /** Bottom edge in root-local pixels. */
    val bottom: Float,
)

/** Explicitly tagged semantic target returned by an optional toolkit adapter. */
public data class ReproTrailTaggedTarget(
    /** Stable toolkit tag mapped to the canonical testTag selector. */
    val testTag: String,
    /** Optional diagnostic component identity. */
    val component: String? = null,
    /** Positive target bounds relative to the supplied root View. */
    val bounds: ReproTrailPixelBounds,
)
