package com.lisdatasolutions.microrag.feature.home

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.toSize
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBar
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBarItem
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.core.navigation.TopLevelDestination
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * F09 T027 (D-046): with a run active, its card and the first row of quick actions show without
 * scrolling on the reference phone, above the bottom bar of the app shell. Only there, as
 * IndexScreenLayoutTest: Robolectric's text metrics and other screens differ.
 */
class HomeScreenLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Home inside the shell's Scaffold, with its bottom bar and insets (MicroRagAppShell). */
    private fun show(state: HomeUiState) {
        assumeTrue("measured on the reference phone (D-022)", Build.MODEL == "A063")
        compose.setContent {
            LisdataTheme {
                Scaffold(
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        LisdataBottomBar(
                            items = TopLevelDestination.entries.map { LisdataBottomBarItem(it.icon, stringResource(it.label)) },
                            selectedIndex = 0,
                            onSelect = {},
                        )
                    },
                ) { padding -> HomeScreen(state, HomeActions(), Modifier.padding(padding).consumeWindowInsets(padding)) }
            }
        }
    }

    private fun viewportBottom() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNodes().single().boundsInRoot.bottom

    /** Unclipped, unlike boundsInRoot: a text below the fold keeps its real place. */
    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        .let { Rect(it.positionInRoot, it.size.toSize()) }

    @Test
    fun withARunActive_itsCardAndTheFirstQuickActionsShow() {
        show(HomePreviewData.indexing)

        val bottom = viewportBottom()
        val activity = compose.activity
        assertWithMessage("bottom of the run's figures").that(bounds(activity.getString(R.string.index_notification_chunks, "320", "860")).bottom).isAtMost(bottom)
        assertWithMessage("bottom of «${activity.getString(R.string.home_import_subtitle)}»")
            .that(bounds(activity.getString(R.string.home_import_subtitle)).bottom).isAtMost(bottom)
        assertWithMessage("bottom of «${activity.getString(R.string.home_documents_subtitle)}»")
            .that(bounds(activity.getString(R.string.home_documents_subtitle)).bottom).isAtMost(bottom)
    }
}
