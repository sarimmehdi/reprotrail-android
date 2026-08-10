package dev.reprotrail.runtime

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View

internal object EnvironmentCapture {
    fun from(
        context: Context,
        root: View,
    ): TraceEnvironment {
        val resources = context.resources
        val deviceClass =
            if (resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP) "tablet" else "phone"
        return TraceEnvironment(
            apiLevel = Build.VERSION.SDK_INT,
            deviceClass = deviceClass,
            display =
                TraceDisplay(
                    widthPx = root.width,
                    heightPx = root.height,
                    densityDpi = resources.displayMetrics.densityDpi,
                    orientation = orientation(root.width, root.height),
                ),
            locale = resources.configuration.locales[0].toLanguageTag(),
            uiMode = uiMode(resources.configuration),
            fontScale = resources.configuration.fontScale.toDouble(),
        )
    }

    private fun orientation(
        width: Int,
        height: Int,
    ): String =
        when {
            width > height -> "landscape"
            height > width -> "portrait"
            width == height -> "square"
            else -> "undefined"
        }

    private fun uiMode(configuration: Configuration): String =
        if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            "dark"
        } else {
            "light"
        }

    private const val TABLET_MIN_WIDTH_DP = 600
}
