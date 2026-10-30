package com.lisdatasolutions.microrag.feature.chat

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBar
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBarItem
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.core.navigation.TopLevelDestination
import com.lisdatasolutions.microrag.core.navigation.shouldShowBottomBar
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * F11 T059 (SC-009, U6): with the real keyboard open, the composer stays above it and the bottom bar of
 * the app shell hides. Only on the reference phone, as the other layout tests: the keyboard and the
 * insets are the device's.
 */
class ChatScreenLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The chat inside the shell's Scaffold, edge to edge with adjustResize, as MainActivity draws it. */
    @OptIn(ExperimentalLayoutApi::class)
    private fun show(state: ChatUiState) {
        assumeTrue("measured on the reference phone (D-022)", Build.MODEL == "A063")
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            @Suppress("DEPRECATION")
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            LisdataTheme {
                Scaffold(
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        if (shouldShowBottomBar(TopLevelDestination.CHAT, imeVisible = WindowInsets.isImeVisible)) {
                            LisdataBottomBar(
                                items = TopLevelDestination.entries.map { LisdataBottomBarItem(it.icon, stringResource(it.label)) },
                                selectedIndex = TopLevelDestination.CHAT.ordinal,
                                onSelect = {},
                            )
                        }
                    },
                ) { padding -> ChatScreen(state, ChatActions(), Modifier.padding(padding).consumeWindowInsets(padding)) }
            }
        }
    }

    private fun imeBottomPx(): Int {
        var bottom = 0
        compose.runOnUiThread {
            bottom = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        return bottom
    }

    @Test
    fun withTheKeyboardOpen_theComposerShowsAboveIt_andTheBottomBarHides() {
        show(ChatPreviewData.png)
        val tab = compose.activity.getString(R.string.nav_chat)
        compose.onNodeWithText(tab).assertExists()

        compose.onNode(hasSetTextAction()).performClick()
        compose.waitUntil(KEYBOARD_TIMEOUT_MS) { imeBottomPx() > 0 }
        compose.waitForIdle()

        compose.onNodeWithText(tab).assertDoesNotExist()
        val window = compose.onRoot().fetchSemanticsNode().size.height
        val send = compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_send)).fetchSemanticsNode().boundsInRoot
        assertWithMessage("bottom of the composer above the keyboard").that(send.bottom).isAtMost(window - imeBottomPx() + 1f)
    }

    private companion object {
        const val KEYBOARD_TIMEOUT_MS = 5_000L
    }
}
