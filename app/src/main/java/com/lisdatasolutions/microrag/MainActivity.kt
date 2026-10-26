package com.lisdatasolutions.microrag

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.core.navigation.MicroRagAppShell
import com.lisdatasolutions.microrag.debug.DebugRouting
import com.lisdatasolutions.microrag.domain.model.StartDestination
import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val resolveStartDestination: ResolveStartDestinationUseCase by inject()
    private var startDestination by mutableStateOf<StartDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug tooling may request a start route; on recreation the restored back stack wins.
        val initialRoute = if (savedInstanceState == null) DebugRouting.initialRoute(intent) else null
        keepSplashUntilStartDestinationIsKnown()
        lifecycleScope.launch { startDestination = resolveStartDestination() }
        setContent {
            LisdataTheme {
                startDestination?.let { MicroRagAppShell(startDestination = it, initialRoute = initialRoute) }
            }
        }
    }

    /** The platform splash (API 31+) stays until the onboarding stage is read (F03 research R10). */
    private fun keepSplashUntilStartDestinationIsKnown() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (startDestination == null) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }
}
