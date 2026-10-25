package com.lisdatasolutions.microrag.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** Second launcher entry ("MicroRAG Debug") hosting the debug-only tools (FR-011, FR-024). */
class DebugLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LisdataTheme { DebugHome() } }
    }
}

/** A debug tool reachable from the debug launcher. */
class DebugEntry(
    val title: String,
    val content: @Composable (onBack: () -> Unit) -> Unit,
)

@Composable
private fun DebugHome() {
    var openedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val opened = debugEntries.firstOrNull { it.title == openedTitle }
    if (opened != null) {
        BackHandler { openedTitle = null }
        opened.content { openedTitle = null }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding(),
    ) {
        LisdataTopBar(title = "MicroRAG Debug")
        Column(
            modifier = Modifier.padding(LisdataTheme.spacing.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.listGap),
        ) {
            debugEntries.forEach { entry ->
                LisdataGradientButton(
                    text = entry.title,
                    onClick = { openedTitle = entry.title },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
