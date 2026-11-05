package com.lisdatasolutions.microrag.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.feature.document.DocumentActions
import com.lisdatasolutions.microrag.feature.document.DocumentContent
import com.lisdatasolutions.microrag.feature.document.DocumentPreviewData
import com.lisdatasolutions.microrag.feature.document.DocumentScreen
import com.lisdatasolutions.microrag.feature.document.DocumentUiState
import com.lisdatasolutions.microrag.feature.document.PreviewContent
import com.lisdatasolutions.microrag.feature.ingest.IngestActions
import com.lisdatasolutions.microrag.feature.ingest.IngestPreviewData
import com.lisdatasolutions.microrag.feature.ingest.IngestScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * F19 T048 (contracts/multimodal-ui.md §4, SC-008): Ajustes with the mode in its stages, «Modo al importar PDF»,
 * the Ingesta's mode sheet and the detail of a document with pages pass ATF, keep 48 dp targets, read their radio
 * groups as such and fit the width with the font at 200 %, light and dark.
 */
@RunWith(Parameterized::class)
class MultimodalAccessibilityTest(
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
        switches[name]?.let { expected ->
            val nodes = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).fetchSemanticsNodes()
            assertWithMessage("$name: the mode's switch").that(nodes).hasSize(1)
            // U2 and TalkBack: the switch is read with its stage, which is its subtitle.
            val text = nodes.single().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ").orEmpty()
            val (res, args) = expected
            assertWithMessage("$name: switch read with its stage").that(text).contains(compose.activity.getString(res, *args))
        }
    }

    @Test
    fun fitsTheWidthWithTheFontAt200Percent() {
        show(fontScale = 2f)

        checkTouchTargets()
        val width = screenRoot().size.width
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            assertWithMessage("$name: ${node.config} fits the width").that(node.positionInRoot.x + node.size.width).isAtMost(width + 1f)
        }
    }

    private fun checkTouchTargets() {
        val minTouch = px(48) - 0.5f
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f && it.size.height - it.boundsInRoot.height <= 1f }
            .forEach { node ->
                val bounds = node.touchBoundsInRoot
                assertWithMessage("$name: touch target ${bounds.width}x${bounds.height}px for ${node.config}")
                    .that(bounds.width >= minTouch && bounds.height >= minTouch).isTrue()
            }
    }

    /** The activity's root: a sheet or a dialog adds a smaller root of its own window. */
    private fun screenRoot() = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy { it.size.width * it.size.height }

    private fun px(dp: Int): Float = with(compose.density) { dp.dp.toPx() }

    companion object {
        private val settings = SettingsPreviewData.png

        private fun settingsWith(mode: MultimodalUiState): @Composable () -> Unit = {
            SettingsScreen(settings, SettingsActions(), multimodal = mode)
        }

        private val withPages = DocumentPreviewData.manual.copy(indexMode = IndexMode.BOTH, pagesStored = 48)

        private val screens: Map<String, @Composable () -> Unit> = linkedMapOf(
            "settings-off" to settingsWith(MultimodalUiState()),
            "settings-downloading" to settingsWith(MultimodalUiState(stage = MultimodalStage.DOWNLOADING, percent = 45)),
            "settings-paused" to settingsWith(MultimodalUiState(stage = MultimodalStage.DOWNLOADING, paused = true)),
            "settings-no-space" to settingsWith(
                MultimodalUiState(stage = MultimodalStage.DOWNLOADING, error = MultimodalError.NO_SPACE, missingBytes = 210L * 1024 * 1024),
            ),
            "settings-validating" to settingsWith(MultimodalUiState(stage = MultimodalStage.VALIDATING)),
            "settings-failed" to settingsWith(MultimodalUiState(error = MultimodalError.IDENTITY_MISMATCH)),
            "settings-on" to settingsWith(MultimodalUiState(stage = MultimodalStage.ON)),
            "settings-enable-dialog" to settingsWith(MultimodalUiState(dialog = MultimodalDialog.ENABLE)),
            "import-mode" to { ImportModeScreen(ImportModeUiState(saved = IndexMode.BOTH, selected = IndexMode.BOTH), ImportModeActions()) },
            "ingest-mode-sheet" to {
                IngestScreen(IngestPreviewData.png.copy(multimodalOn = true, indexMode = IndexMode.BOTH, choosingMode = true), IngestActions())
            },
            "document-with-pages" to {
                DocumentScreen(
                    DocumentUiState(content = DocumentContent.Loaded(withPages), preview = PreviewContent.None, multimodalOn = true),
                    DocumentActions(),
                )
            },
        )

        private val radios = mapOf("import-mode" to 3, "ingest-mode-sheet" to 3)

        /** The stage each switch is read with, as a string resource and its arguments. */
        private val switches: Map<String, Pair<Int, Array<Any>>> = mapOf(
            "settings-off" to (R.string.multimodal_subtitle to emptyArray()),
            "settings-downloading" to (R.string.multimodal_stage_downloading to arrayOf(45)),
            "settings-paused" to (R.string.multimodal_stage_paused to emptyArray()),
            "settings-validating" to (R.string.multimodal_stage_validating to emptyArray()),
            "settings-on" to (R.string.multimodal_subtitle to emptyArray()),
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={1}")
        fun parameters(): List<Array<Any>> = screens.keys.flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
}
