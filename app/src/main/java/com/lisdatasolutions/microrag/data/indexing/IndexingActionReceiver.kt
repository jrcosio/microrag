package com.lisdatasolutions.microrag.data.indexing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.domain.indexing.PauseIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.ReconcileIndexingUseCase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * «Pausar» from the indexing notification (P11) and the wake-up of [IndexingAlarm]. Not exported: only
 * our PendingIntents reach it.
 */
class IndexingActionReceiver : BroadcastReceiver(), KoinComponent {

    private val pause: PauseIndexingUseCase by inject()
    private val reconcile: ReconcileIndexingUseCase by inject()
    private val applicationScope: ApplicationScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action: suspend () -> Unit = when (intent.action) {
            ACTION_PAUSE -> { { pause() } }
            ACTION_WAKE -> { { reconcile() } }
            else -> return
        }
        val pending = goAsync()
        applicationScope.scope.launch {
            try {
                action()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.lisdatasolutions.microrag.action.PAUSE_INDEXING"
        const val ACTION_WAKE = "com.lisdatasolutions.microrag.action.WAKE_INDEXING"
    }
}
