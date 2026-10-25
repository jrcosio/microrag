package com.lisdatasolutions.microrag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.core.navigation.MicroRagAppShell
import com.lisdatasolutions.microrag.debug.DebugRouting

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug tooling may request a start route; on recreation the restored back stack wins.
        val initialRoute = if (savedInstanceState == null) DebugRouting.initialRoute(intent) else null
        setContent {
            LisdataTheme {
                MicroRagAppShell(initialRoute = initialRoute)
            }
        }
    }
}
