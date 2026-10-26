package com.lisdatasolutions.microrag.data.indexing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.domain.indexing.PauseIndexingUseCase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** «Pausar» from the indexing notification (P11). Not exported: only our PendingIntent reaches it. */
class IndexingActionReceiver : BroadcastReceiver(), KoinComponent {

    private val pause: PauseIndexingUseCase by inject()
    private val applicationScope: ApplicationScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAUSE) return
        val pending = goAsync()
        applicationScope.scope.launch {
            try {
                pause()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.lisdatasolutions.microrag.action.PAUSE_INDEXING"
    }
}
