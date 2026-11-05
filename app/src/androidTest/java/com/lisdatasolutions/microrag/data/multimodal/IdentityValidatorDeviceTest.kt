package com.lisdatasolutions.microrag.data.multimodal

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.InstalledModels
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F19 T059 (research R3, analyze M8): the identity check with the real 270M and 440M on this device, before the
 * mode is turned on. It needs both models installed (dev models on the emulator); on the phone it runs before the
 * switch deletes the 270M.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class IdentityValidatorDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theTwoTextEmbedders_giveThisDeviceTheSameVectors() = runBlocking<Unit> {
        val models = InstalledModels(context)
        assumeTrue("The 270M and the 440M are not both installed", models.isReady(ModelId.EG2_270M) && models.isReady(ModelId.EG2_440M))
        val dispatchers = AppDispatchers.create()
        val logger = CapturingLogger()
        val coordinator = EngineCoordinator(logger)
        try {
            val validator = IdentityValidator(
                coordinator = coordinator,
                factory = LiteRtEngineFactory(context, dispatchers),
                models = models,
                texts = { IdentityValidator.load(context.assets) },
                logger = logger,
            )

            val started = System.nanoTime()
            val first = validator.check()
            val seconds = (System.nanoTime() - started) / 1e9
            Log.i(TAG, "identity ${first.texts} texts min cosine ${"%.6f".format(first.minCosine)} in ${"%.1f".format(seconds)} s")

            assertThat(first.texts).isEqualTo(20)
            assertThat(first.minCosine).isAtLeast(IdentityValidator.MIN_COSINE)
            assertThat(first.passed).isTrue()
            assertThat(coordinator.loaded.value).isEmpty()

            // A stop in VALIDATING runs it again at start: the result does not change.
            val again = validator.check()
            assertThat(again.passed).isTrue()
            assertThat(again.minCosine).isWithin(1e-6).of(first.minCosine)
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    private companion object {
        const val TAG = "MicroRAG-F19"
    }
}
