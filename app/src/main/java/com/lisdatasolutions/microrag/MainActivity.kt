package com.lisdatasolutions.microrag

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.locale.AppLocale
import com.lisdatasolutions.microrag.core.navigation.MicroRagAppShell
import com.lisdatasolutions.microrag.debug.DebugRouting
import com.lisdatasolutions.microrag.domain.indexing.ReconcileIndexingUseCase
import com.lisdatasolutions.microrag.domain.model.StartDestination
import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val resolveStartDestination: ResolveStartDestinationUseCase by inject()
    private val reconcileIndexing: ReconcileIndexingUseCase by inject()
    private val applicationScope: ApplicationScope by inject()
    private var startDestination by mutableStateOf<StartDestination?>(null)

    /** Taps on the indexing notification since this Activity was created (research R3). */
    private var indexingRequests by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug tooling may request a start route; on recreation the restored back stack wins.
        val initialRoute = if (savedInstanceState == null) DebugRouting.initialRoute(intent) else null
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_INDEXING) indexingRequests++
        keepSplashUntilStartDestinationIsKnown()
        lifecycleScope.launch { startDestination = resolveStartDestination() }
        setContent {
            LisdataTheme {
                startDestination?.let {
                    MicroRagAppShell(
                        startDestination = it,
                        initialRoute = initialRoute,
                        openIndexing = indexingRequestsFor(it, indexingRequests),
                    )
                }
            }
        }
    }

    /** singleTop: a tap on the notification with the app open arrives here instead of a new Activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_INDEXING) indexingRequests++
    }

    /** With the app visible: a run left without work is enqueued again (F07 research R12, rule N14). */
    override fun onStart() {
        super.onStart()
        applicationScope.scope.launch { reconcileIndexing() }
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

    companion object {
        /** Internal and explicit: the indexing notification's tap, not a deep link (research R3). */
        const val ACTION_OPEN_INDEXING = "com.lisdatasolutions.microrag.action.OPEN_INDEXING"

        /** Only once onboarding is done: the exported Activity must not let anyone skip it. */
        fun indexingRequestsFor(destination: StartDestination, requests: Int): Int =
            if (destination == StartDestination.HOME) requests else 0
    }
}
