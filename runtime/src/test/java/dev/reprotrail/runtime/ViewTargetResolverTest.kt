package dev.reprotrail.runtime

import android.app.Application
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewTargetResolverTest {
    @Test
    fun `deepest actionable view produces ordered semantic selectors and bounds`() {
        val root = rootView()
        val button =
            Button(application()).apply {
                id = R.id.reprotrail_test_target
                setTag(R.id.reprotrail_replay_id_tag, "checkout.submit")
            }
        root.addView(button, frame(width = 100, height = 80, left = 50, top = 120))
        layout(root)

        val target = ViewTargetResolver.resolve(root, x = 75f, y = 150f)

        assertEquals(Button::class.java.name, target.component)
        assertEquals(
            listOf("replayId", "resourceId", "coordinate"),
            target.selectors.map(::selectorType),
        )
        assertEquals("checkout.submit", (target.selectors[0] as ReplayIdSelector).value)
        assertTrue(
            (target.selectors[1] as ResourceIdSelector).value.matches(
                Regex("^[A-Za-z_][A-Za-z0-9_.]*:id/reprotrail_test_target$"),
            ),
        )
        assertEquals(NormalizedBounds(0.125, 0.15, 0.375, 0.25), target.bounds)
    }

    @Test
    fun `view without semantic identity uses only a normalized coordinate`() {
        val root = rootView()
        val targetView = View(application()).apply { isClickable = true }
        root.addView(targetView, frame(width = 200, height = 200, left = 100, top = 300))
        layout(root)

        val target = ViewTargetResolver.resolve(root, x = 200f, y = 400f)

        assertEquals(1, target.selectors.size)
        val coordinate = target.selectors.single() as CoordinateSelector
        assertEquals(0.5, coordinate.x, 0.0)
        assertEquals(0.5, coordinate.y, 0.0)
        assertTrue(target.selectors.none { it is ReplayIdSelector || it is ResourceIdSelector })
    }

    @Test
    fun `visible selectors require exact host allowlisting and preserve ranking`() {
        val root = rootView()
        val label =
            TextView(application()).apply {
                id = R.id.reprotrail_test_target
                text = "Pay now"
                contentDescription = "Submit payment"
                isClickable = true
            }
        root.addView(label, frame(width = 200, height = 100, left = 100, top = 200))
        layout(root)

        val blocked = ViewTargetResolver.resolve(root, 150f, 250f)
        val allowed =
            ViewTargetResolver.resolve(
                root,
                150f,
                250f,
                visibleSelectorAllowlist = setOf("Pay now", "Submit payment"),
            )

        assertEquals(listOf("resourceId", "coordinate"), blocked.selectors.map(::selectorType))
        assertEquals(
            listOf("resourceId", "text", "contentDescription", "coordinate"),
            allowed.selectors.map(::selectorType),
        )
        assertEquals("Pay now", (allowed.selectors[1] as TextSelector).value)
        assertEquals("Submit payment", (allowed.selectors[2] as ContentDescriptionSelector).value)
    }

    @Test
    fun `explicit target resolver can contribute a ranked test tag`() {
        val root = rootView()
        layout(root)
        val resolver =
            ReproTrailTargetResolver { _, _, _ ->
                ReproTrailTaggedTarget(
                    testTag = "checkout.compose-submit",
                    component = "compose.Button",
                    bounds = ReproTrailPixelBounds(left = 40f, top = 80f, right = 360f, bottom = 160f),
                )
            }

        val target = ViewTargetResolver.resolve(root, 200f, 120f, targetResolvers = listOf(resolver))

        assertEquals("compose.Button", target.component)
        assertEquals(listOf("testTag", "coordinate"), target.selectors.map(::selectorType))
        assertEquals("checkout.compose-submit", (target.selectors.first() as TestTagSelector).value)
        assertEquals(NormalizedBounds(0.1, 0.1, 0.9, 0.2), target.bounds)
    }

    private fun rootView(): FrameLayout = FrameLayout(application())

    private fun layout(root: FrameLayout) {
        root.measure(exactly(400), exactly(800))
        root.layout(0, 0, 400, 800)
    }

    private fun frame(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
    ): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(width, height).apply {
            leftMargin = left
            topMargin = top
        }

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun application(): Application = RuntimeEnvironment.getApplication()

    private fun selectorType(selector: TraceSelector): String =
        when (selector) {
            is ReplayIdSelector -> "replayId"
            is ResourceIdSelector -> "resourceId"
            is TestTagSelector -> "testTag"
            is TextSelector -> "text"
            is ContentDescriptionSelector -> "contentDescription"
            is CoordinateSelector -> "coordinate"
        }
}
