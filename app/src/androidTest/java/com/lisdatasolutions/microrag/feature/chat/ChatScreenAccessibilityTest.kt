package com.lisdatasolutions.microrag.feature.chat

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** F11 T058 (U3–U5, SC-008): the chat in every state, light and dark, in landscape and at 200 %. */
@RunWith(Parameterized::class)
class ChatScreenAccessibilityTest(
    private val name: String,
    private val state: ChatUiState,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { ChatScreen(state, ChatActions()) }
            }
        }
    }

    private fun checkAll() {
        compose.onAllNodes(isRoot()).let { roots -> repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() } }
        checkTouchTargets()
        checkSentences()
    }

    @Test
    fun passesChecks_andReadsAsSentences() {
        compose.enableAccessibilityChecks()
        show()

        checkAll()
    }

    /** The activity turns before anything is drawn, and is back upright before the next test starts. */
    @Test
    fun passesChecksInLandscape() {
        turn(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        try {
            compose.enableAccessibilityChecks()
            show()

            // The screen's root is the largest; a dialog has its own.
            val root = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy { it.size.width }.size
            assertWithMessage("$name: landscape").that(root.width).isGreaterThan(root.height)
            checkAll()
        } finally {
            turn(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        }
    }

    private fun turn(requested: Int, expected: Int) {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = requested }
        compose.waitUntil(TURN_TIMEOUT_MS) {
            var orientation = Configuration.ORIENTATION_UNDEFINED
            compose.activityRule.scenario.onActivity { orientation = it.resources.configuration.orientation }
            orientation == expected
        }
        compose.waitForIdle()
    }

    @Test
    fun fitsTheWidthWithTheFontAt200Percent() {
        show(fontScale = 2f)

        checkTouchTargets()
        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.width }
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
    }

    /**
     * U3, on what the list has laid out (it opens at the latest answer): a question bubble reads «Tú: pregunta»,
     * the assistant reads «MicroRAG» and never as the eyebrow's capitals, and a source row names its document.
     */
    private fun checkSentences() {
        val nodes = compose.onAllNodes(SemanticsMatcher("any") { true }).fetchSemanticsNodes()
        val descriptions = nodes.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        val texts = nodes.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        val activity = compose.activity
        state.turns.filter { it.question in texts }.forEach { turn ->
            assertWithMessage("$name: bubble read as a sentence").that(descriptions).contains(activity.getString(R.string.chat_you_sentence, turn.question))
        }
        val assistant = activity.getString(R.string.assistant_name)
        assertWithMessage("$name: the assistant is never read in capitals").that(texts).doesNotContain(assistant.uppercase())
        val prefix = activity.getString(R.string.chat_source_sentence, 0, "").substringBefore("0")
        descriptions.filter { it.startsWith(prefix) }.forEach { sentence ->
            assertWithMessage("$name: «$sentence» names its document")
                .that(state.turns.flatMap { it.sources }.any { sentence.contains(it.citation.documentName) }).isTrue()
        }
    }

    /** U4: 48 dp, except the citation chips inside the text (research R3): their 48 dp path is the source row. */
    private fun checkTouchTargets() {
        val minTouch = px(48) - 0.5f
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .filterNot { it.isTextLink() }
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    /** A link inside a text (LinkAnnotation): Compose marks it with a test-only property. */
    private fun SemanticsNode.isTextLink(): Boolean = config.any { it.key.name == "LinkTestMarker" }

    private fun px(dp: Int): Float = with(compose.density) { dp.dp.toPx() }

    companion object {
        private const val TURN_TIMEOUT_MS = 5_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={2}")
        fun parameters(): List<Array<Any>> = ChatPreviewData.all.flatMap { (name, state) ->
            listOf(arrayOf<Any>(name, state, false), arrayOf<Any>(name, state, true))
        }
    }
}
