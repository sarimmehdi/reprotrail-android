package dev.reprotrail.runtime.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import dev.reprotrail.runtime.ReproTrailTargetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ReproTrailComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun explicitlyTaggedComposeNodeResolvesAtItsWindowCoordinate() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Button(onClick = {}, modifier = Modifier.testTag(TEST_TAG)) {
                    Text("Checkout")
                }
            }
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(TEST_TAG).fetchSemanticsNode()
        val bounds = node.boundsInWindow
        val root = composeRule.activity.window.decorView
        val rootLocation = IntArray(2).also(root::getLocationInWindow)

        val target =
            resolver().resolve(
                root,
                bounds.center.x - rootLocation[0],
                bounds.center.y - rootLocation[1],
            )

        assertNotNull(target)
        assertEquals(TEST_TAG, target?.testTag)
        assertEquals(IntSize(bounds.width.toInt(), bounds.height.toInt()), target?.bounds?.size())
    }

    private fun resolver(): ReproTrailTargetResolver = ReproTrailCompose.targetResolver()

    private fun dev.reprotrail.runtime.ReproTrailPixelBounds.size(): IntSize =
        IntSize((right - left).toInt(), (bottom - top).toInt())

    private companion object {
        const val TEST_TAG = "checkout.compose-submit"
    }
}
