package com.lisdatasolutions.microrag.data.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Install and verification on real device storage, with a 1 MB stand-in for a model file. */
@RunWith(AndroidJUnit4::class)
class ModelInstallInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // A separate folder, so the real models under no_backup/models are never touched.
    private val modelsDir = File(context.noBackupFilesDir, "models-instrumented-test")
    private val bytes = Random(1).nextBytes(1024 * 1024)
    private val file = ModelCatalog.byId(ModelId.EG2_270M).copy(
        sizeBytes = bytes.size.toLong(),
        sha256 = Sha256.of(ByteArrayInputStream(bytes)),
    )
    private val repository = DefaultModelFileRepository(
        modelsDir = modelsDir,
        catalog = listOf(file),
        ioDispatcher = Dispatchers.IO,
        logger = DefaultAppLogger(isEnabled = false),
    )

    @After
    fun tearDown() {
        modelsDir.deleteRecursively()
        File(context.cacheDir, SOURCE).delete()
    }

    @Test
    fun install_verifiedFileLandsInNoBackupStorage() = runBlocking<Unit> {
        val source = File(context.cacheDir, SOURCE).apply { writeBytes(bytes) }

        val state = repository.installVerified(file.id, source, deleteSourceOnSuccess = true)

        assertThat(state).isInstanceOf(ModelFileState.Verified::class.java)
        val installed = (state as ModelFileState.Verified).path
        assertThat(installed.canonicalPath).startsWith(context.noBackupFilesDir.canonicalPath + File.separator)
        assertThat(installed.readBytes()).isEqualTo(bytes)
        assertThat(source.exists()).isFalse()
    }

    @Test
    fun install_withWrongHash_keepsNothing() = runBlocking<Unit> {
        val source = File(context.cacheDir, SOURCE).apply { writeBytes(bytes.copyOf(bytes.size - 1)) }

        val state = repository.installVerified(file.id, source, deleteSourceOnSuccess = true)

        assertThat(state).isInstanceOf(ModelFileState.Mismatch::class.java)
        assertThat(modelsDir.listFiles().orEmpty()).isEmpty()
        assertThat(source.exists()).isTrue()
        assertThat(repository.pathOf(file.id)).isNull()
    }

    private companion object {
        const val SOURCE = "install-source.bin"
    }
}
