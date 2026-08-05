package dev.reprotrail.runtime

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TraceJsonTest {
    @Test
    fun `minimal tap matches the canonical wire contract`() {
        val trace =
            TraceDocument(
                session =
                    TraceSession(
                        id = "018f1f4e-7b2a-7c81-9f8d-9d9dd7f3f441",
                        startedAt = "2026-08-05T10:15:30.000Z",
                    ),
                application = TraceApplication(packageName = "dev.reprotrail.fixture"),
                environment =
                    TraceEnvironment(
                        apiLevel = 36,
                        display = TraceDisplay(1080, 2400, 420, "portrait"),
                        locale = "en-US",
                        uiMode = "light",
                    ),
                privacy = TracePrivacy(),
                actions =
                    listOf(
                        TapAction(
                            id = "018f1f4e-8aef-7bb8-a846-e8c86a0d57af",
                            sequence = 0,
                            offsetMs = 1250,
                            target = TraceTarget(selectors = listOf(ReplayIdSelector("checkout.submit"))),
                        ),
                    ),
            )

        val expected = checkNotNull(javaClass.getResource("/minimal-tap.json")).readText()

        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(TraceJson.encode(trace)))
    }
}
