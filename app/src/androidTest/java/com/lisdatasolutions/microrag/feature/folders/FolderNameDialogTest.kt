package com.lisdatasolutions.microrag.feature.folders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F17 T016 (wireframe §3, K3) on a device: the name dialog, whose text field inside an AlertDialog never
 * settles under Robolectric. The focus starts in the field, and «Crear» waits for a valid name.
 */
@RunWith(AndroidJUnit4::class)
class FolderNameDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theFocusStartsInTheField_andCreateWaitsForAValidName() {
        val calls = mutableListOf<String>()
        compose.setContent {
            LisdataTheme {
                var name by remember { mutableStateOf("") }
                val dialog = FolderDialog.Name(
                    renaming = null,
                    name = name,
                    error = if (name == "General") FolderFieldError.RESERVED else null,
                    canSave = name.isNotBlank() && name != "General",
                )
                FolderDialogs(dialog, busy = false, FolderDialogActions(onNameChange = { name = it }, onSaveName = { calls += "save $name" }))
            }
        }

        compose.onNodeWithText(context.getString(R.string.library_folder_new)).assertExists()
        compose.onNode(isFocused()).assertExists()
        compose.onNodeWithText(context.getString(R.string.folder_dialog_create)).assertIsNotEnabled()

        compose.onNode(isFocused()).performTextReplacement("General")
        compose.onNodeWithText(context.getString(R.string.folder_error_reserved)).assertExists()
        compose.onNodeWithText(context.getString(R.string.folder_dialog_create)).assertIsNotEnabled()

        compose.onNode(isFocused()).performTextReplacement("Legal")
        compose.onNodeWithText(context.getString(R.string.folder_dialog_create)).assertIsEnabled().performClick()
        assertThat(calls).containsExactly("save Legal")
    }
}
