package com.lisdatasolutions.microrag.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** Light, dark and 200 % font scale previews required by FR-010. */
@Preview(name = "Light", showBackground = true, widthDp = 390)
@Preview(name = "Dark", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Font 200%", showBackground = true, widthDp = 390, fontScale = 2f)
annotation class LisdataPreviews

/** Wraps preview content in the LISDATA theme and background. */
@Composable
fun LisdataPreviewSurface(content: @Composable () -> Unit) {
    LisdataTheme {
        Box(
            modifier = Modifier
                .background(LisdataTheme.colors.background)
                .padding(16.dp),
        ) {
            content()
        }
    }
}
