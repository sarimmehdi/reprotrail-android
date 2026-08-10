package dev.reprotrail.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object TraceJson {
    private val json =
        Json {
            prettyPrint = true
            explicitNulls = false
            encodeDefaults = true
            classDiscriminator = "type"
        }

    fun encode(document: TraceDocument): String = json.encodeToString(document)

    fun encodeAction(action: TraceAction): String = json.encodeToString(action)

    fun decodeAction(value: String): TraceAction = json.decodeFromString(value)

    fun encodeEnvironment(environment: TraceEnvironment): String = json.encodeToString(environment)

    fun decodeEnvironment(value: String): TraceEnvironment = json.decodeFromString(value)
}

@Serializable
internal data class TraceDocument(
    val schemaVersion: String = "1.0.0-alpha.1",
    val session: TraceSession,
    val application: TraceApplication,
    val environment: TraceEnvironment,
    val privacy: TracePrivacy,
    val actions: List<TraceAction>,
)

@Serializable
internal data class TraceSession(
    val id: String,
    val startedAt: String,
    val endedAt: String? = null,
    val durationMs: Long? = null,
    val recorder: TraceRecorderIdentity = TraceRecorderIdentity(),
)

@Serializable
internal data class TraceRecorderIdentity(
    val name: String = "reprotrail-android",
    val version: String = "1.0.0-alpha.1",
)

@Serializable
internal data class TraceApplication(
    val packageName: String,
)

@Serializable
internal data class TraceEnvironment(
    val platform: String = "android",
    val apiLevel: Int,
    val deviceClass: String = "phone",
    val display: TraceDisplay,
    val locale: String,
    val uiMode: String,
    val fontScale: Double = 1.0,
)

@Serializable
internal data class TraceDisplay(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val orientation: String,
)

@Serializable
internal data class TracePrivacy(
    val captureMode: String = "internal",
    val policyVersion: String = "fixture-policy-1",
    val textCapture: String = "disabled",
    val selectorText: String = "disabled",
    val consent: TraceConsent = TraceConsent(),
)

@Serializable
internal data class TraceConsent(
    val status: String = "not_required",
)

@Serializable
internal sealed interface TraceAction {
    val id: String
    val sequence: Int
    val offsetMs: Long
}

@Serializable
@SerialName("tap")
internal data class TapAction(
    override val id: String,
    override val sequence: Int,
    override val offsetMs: Long,
    val target: TraceTarget,
) : TraceAction

@Serializable
@SerialName("longPress")
internal data class LongPressAction(
    override val id: String,
    override val sequence: Int,
    override val offsetMs: Long,
    val durationMs: Long,
    val target: TraceTarget,
) : TraceAction

@Serializable
@SerialName("swipe")
internal data class SwipeAction(
    override val id: String,
    override val sequence: Int,
    override val offsetMs: Long,
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val durationMs: Long,
) : TraceAction

@Serializable
internal data class NormalizedPoint(
    val x: Double,
    val y: Double,
)

@Serializable
internal data class TraceTarget(
    val component: String? = null,
    val bounds: NormalizedBounds? = null,
    val selectors: List<TraceSelector>,
)

@Serializable
internal data class NormalizedBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

@Serializable
internal sealed interface TraceSelector

@Serializable
@SerialName("replayId")
internal data class ReplayIdSelector(
    val value: String,
    val source: String = "host_provided",
) : TraceSelector

@Serializable
@SerialName("resourceId")
internal data class ResourceIdSelector(
    val value: String,
    val source: String = "captured",
) : TraceSelector

@Serializable
@SerialName("text")
internal data class TextSelector(
    val value: String,
    val match: String = "exact",
    val classification: String = "allowlisted",
    val source: String = "captured",
) : TraceSelector

@Serializable
@SerialName("contentDescription")
internal data class ContentDescriptionSelector(
    val value: String,
    val match: String = "exact",
    val classification: String = "allowlisted",
    val source: String = "captured",
) : TraceSelector

@Serializable
@SerialName("coordinate")
internal data class CoordinateSelector(
    val x: Double,
    val y: Double,
    val reference: String = "window",
    val source: String = "captured",
) : TraceSelector
