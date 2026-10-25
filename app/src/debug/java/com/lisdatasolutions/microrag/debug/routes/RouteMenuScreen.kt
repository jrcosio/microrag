package com.lisdatasolutions.microrag.debug.routes

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.debug.DebugRouting

private class RouteSample(val label: String, val route: String, val id: Long? = null)

private val routeSamples = listOf(
    RouteSample("welcome", "welcome"),
    RouteSample("setup", "setup"),
    RouteSample("home", "home"),
    RouteSample("chat (nueva)", "chat"),
    RouteSample("chat/7", "chat", 7),
    RouteSample("ingest", "ingest"),
    RouteSample("library", "library"),
    RouteSample("document/42", "document", 42),
    RouteSample("document/-1 (id inválido, N8)", "document", -1),
    RouteSample("index", "index"),
    RouteSample("settings", "settings"),
    RouteSample("languages", "languages"),
)

/** Debug-only menu that opens every destination with test arguments (FR-024). */
@Composable
fun RouteMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LisdataTheme.colors.background),
    ) {
        LisdataTopBar(title = "Menú de rutas", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(LisdataTheme.spacing.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2),
        ) {
            items(routeSamples) { sample ->
                LisdataOutlinedButton(
                    text = sample.label,
                    onClick = { context.openRoute(sample) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun Context.openRoute(sample: RouteSample) {
    val intent = Intent(this, MainActivity::class.java)
        .putExtra(DebugRouting.EXTRA_ROUTE, sample.route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    sample.id?.let { intent.putExtra(DebugRouting.EXTRA_ID, it) }
    startActivity(intent)
}
