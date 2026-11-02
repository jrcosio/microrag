package com.lisdatasolutions.microrag.core.designsystem.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F12 T065: a row's status chip goes beside its texts, or under them when a word would not fit whole beside
 * it. Native graphics, so the texts are measured with real fonts.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowChipTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(width: Dp, chip: String) = compose.setContent {
        LisdataTheme {
            Box(Modifier.width(width)) {
                LisdataSettingsRow(
                    icon = LisdataIcons.Layers,
                    title = "Modelo de embeddings",
                    subtitle = "EmbeddingGemma 2",
                    trailing = SettingsTrailing.ChipAndChevron(chip, StatusKind.Warning),
                    onClick = {},
                )
            }
        }
    }

    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun aShortStatusGoesBesideTheTexts() {
        show(390.dp, "Local")

        assertThat(bounds("Local").left).isGreaterThan(bounds("EmbeddingGemma 2").right)
    }

    @Test
    fun aStatusThatWouldBreakAWordGoesUnderTheTexts() {
        show(300.dp, "Por configurar")

        assertThat(bounds("Por configurar").top).isAtLeast(bounds("EmbeddingGemma 2").bottom)
    }
}
