package com.lisdatasolutions.microrag.data.download

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * T044 (plan risk 1): a real user-initiated data transfer job downloads EmbeddingGemma 2 (165 MB)
 * from the pinned URL and the file ends up hash-verified in the models folder.
 */
@LargeTest
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class UidtDownloadSmokeTest : KoinComponent {

    private val controller: ModelDownloadController by inject()
    private val repository: ModelFileRepository by inject()
    private val store: OnboardingStore by inject()
    private val dirs: AppDirs by inject()

    @Test
    fun transferJobDownloadsAndVerifiesTheEmbeddingModel() = runBlocking<Unit> {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val file = ModelCatalog.byId(ModelId.EG2_270M)
        repository.refresh()
        assumeTrue("already installed", repository.states.value[file.id] !is ModelFileState.Verified)
        dirs.partial(file).delete()

        val started = System.nanoTime()
        store.requestDownload(setOf(file.id))
        controller.schedule(file.sizeBytes)
        assertThat(controller.isScheduledOrRunning()).isTrue()

        val state = withTimeout(10 * 60_000L) {
            repository.states.first { it[file.id] is ModelFileState.Verified || it[file.id] is ModelFileState.Mismatch }
        }.getValue(file.id)
        val seconds = (System.nanoTime() - started) / 1e9
        Log.i("MicroRAG", "uidt smoke: ${file.id} ${state::class.simpleName} in %.1f s".format(seconds))

        assertThat(state).isInstanceOf(ModelFileState.Verified::class.java)
        assertThat(dirs.partial(file).exists()).isFalse()
        store.clearRequest(file.id)
    }
}
