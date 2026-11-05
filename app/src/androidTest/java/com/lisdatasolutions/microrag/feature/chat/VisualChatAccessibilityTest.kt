package com.lisdatasolutions.microrag.feature.chat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import com.lisdatasolutions.microrag.feature.document.DocumentActions
import com.lisdatasolutions.microrag.feature.document.DocumentContent
import com.lisdatasolutions.microrag.feature.document.DocumentPreviewData
import com.lisdatasolutions.microrag.feature.document.DocumentScreen
import com.lisdatasolutions.microrag.feature.document.DocumentUiState
import com.lisdatasolutions.microrag.feature.document.PageImageState
import com.lisdatasolutions.microrag.feature.document.PageViewerScreen
import com.lisdatasolutions.microrag.feature.document.PreviewContent
import com.lisdatasolutions.microrag.feature.settings.PageDetailActions
import com.lisdatasolutions.microrag.feature.settings.PageDetailScreen
import com.lisdatasolutions.microrag.feature.settings.PageDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * F20 T046 (contracts/chat-pages-ui.md U17, SC-008): «Detalle de las páginas», a conversation with a cited page and
 * a cited image, the detail on a cited page and the viewer pass ATF, keep 48 dp targets, read their radio group as
 * such and fit the width with the font at 200 %, light and dark.
 */
@RunWith(Parameterized::class)
class VisualChatAccessibilityTest(
    private val name: String,
    private val dark: Boolean,
) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LisdataTheme(darkTheme = dark) { screens.getValue(name).invoke() }
            }
        }
    }

    @Test
    fun passesChecks_withRadioGroupsAndTouchTargets() {
        compose.enableAccessibilityChecks()
        show()

        compose.onAllNodes(isRoot()).let { roots -> repeat(roots.fetchSemanticsNodes().size) { roots[it].tryPerformAccessibilityChecks() } }
        checkTouchTargets()
        radios[name]?.let { expected ->
            val nodes = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes()
            assertWithMessage("$name: radio buttons").that(nodes).hasSize(expected)
            assertWithMessage("$name: one selected").that(nodes.count { it.config.getOrNull(SemanticsProperties.Selected) == true }).isEqualTo(1)
        }
    }

    @Test
    fun fitsTheWidthWithTheFontAt200Percent() {
        show(fontScale = 2f)

        checkTouchTargets()
        val width = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy { it.size.width * it.size.height }.size.width
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
    }

    private fun checkTouchTargets() {
        val minTouch = with(compose.density) { 48.dp.toPx() } - 0.5f
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    companion object {
        private val page = ImageBitmap(300, 424)

        private val withPages = ChatPreviewData.png.copy(
            turns = listOf(
                ChatPreviewData.completedTurn.copy(
                    sources = listOf(ChatPreviewData.manual, ChatPreviewData.reportPage, ChatPreviewData.poster),
                    chipTargets = listOf(ChatPreviewData.manual.target, ChatPreviewData.reportPage.target, ChatPreviewData.poster.target),
                ),
            ),
        )

        private val cited = PreviewContent.Page(1, 4, "Manual de procesos.pdf", image = false)

        private val screens: Map<String, @Composable () -> Unit> = linkedMapOf(
            "page-detail" to { PageDetailScreen(PageDetailUiState(saved = PageDetail.BALANCED, selected = PageDetail.BALANCED), PageDetailActions()) },
            "chat-with-pages" to { ChatScreen(withPages, ChatActions(loadThumbnail = { page })) },
            "document-cited-page" to {
                DocumentScreen(
                    DocumentUiState(content = DocumentContent.Loaded(DocumentPreviewData.manual), preview = cited),
                    DocumentActions(loadPage = { _, _ -> page }),
                )
            },
            "viewer" to { PageViewerScreen(page = 4, documentName = "Manual de procesos.pdf", image = false, state = PageImageState.Shown(page), onClose = {}) },
            "viewer-loading" to { PageViewerScreen(page = 4, documentName = "Manual de procesos.pdf", image = false, state = PageImageState.Loading, onClose = {}) },
        )

        private val radios = mapOf("page-detail" to 3)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={1}")
        fun parameters(): List<Array<Any>> = screens.keys.flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
}
