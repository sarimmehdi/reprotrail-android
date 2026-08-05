package dev.reprotrail.runtime

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.max

internal class TraceCaptureRuntime(
    private val context: Context,
    private val configuration: ReproTrailConfig,
) {
    private val startedAt = Instant.now().toString()
    private val startedElapsedMs = SystemClock.elapsedRealtime()
    private val sessionId = newId()
    private val detector =
        TapGestureDetector(
            touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
            maxTapDurationMs = MAX_TAP_DURATION_MS,
        )
    private val actions = mutableListOf<TapAction>()
    private var environment: TraceEnvironment? = null

    @Synchronized
    fun capture(
        root: View,
        event: MotionEvent,
    ) {
        val action = event.toPointerAction() ?: return
        val tap = detector.onEvent(action, event.x, event.y, event.eventTime) ?: return
        environment = environment ?: environment(root)
        actions +=
            TapAction(
                id = newId(),
                sequence = actions.size,
                offsetMs = max(0, event.eventTime - startedElapsedMs),
                target = ViewTargetResolver.resolve(root, tap.x, tap.y),
            )
    }

    @Synchronized
    fun exportLatest(): File {
        check(actions.isNotEmpty()) { "Capture at least one tap before exporting a trace." }
        val document =
            TraceDocument(
                session = TraceSession(id = sessionId, startedAt = startedAt),
                application = TraceApplication(packageName = context.packageName),
                environment = checkNotNull(environment),
                privacy = TracePrivacy(policyVersion = configuration.policyVersion),
                actions = actions.toList(),
            )
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, EXPORT_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Could not create trace export directory." }
        return File(directory, LATEST_TRACE_FILE).apply { writeText(TraceJson.encode(document)) }
    }

    private fun environment(root: View): TraceEnvironment {
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

    private fun MotionEvent.toPointerAction(): PointerAction? {
        if (pointerCount > 1) return PointerAction.CANCEL
        return when (actionMasked) {
            MotionEvent.ACTION_DOWN -> PointerAction.DOWN
            MotionEvent.ACTION_MOVE -> PointerAction.MOVE
            MotionEvent.ACTION_UP -> PointerAction.UP
            MotionEvent.ACTION_CANCEL -> PointerAction.CANCEL
            else -> null
        }
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

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val MAX_TAP_DURATION_MS = 500L
        const val TABLET_MIN_WIDTH_DP = 600
        const val EXPORT_DIRECTORY = "reprotrail"
        const val LATEST_TRACE_FILE = "latest-trace.json"
    }
}
