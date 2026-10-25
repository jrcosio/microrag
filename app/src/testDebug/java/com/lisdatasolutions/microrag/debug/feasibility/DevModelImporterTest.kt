package com.lisdatasolutions.microrag.debug.feasibility

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DevModelImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val repository = FakeModelFileRepository(corrupt = setOf(ModelId.EG2_440M))

    @Test
    fun ensureDir_createsTheFolderFromTheApp() = runTest {
        val devDir = File(tmp.root, "files/dev-models")

        DevModelImporter(devDir, repository, StandardTestDispatcher(testScheduler)).ensureDir()

        assertThat(devDir.isDirectory).isTrue()
    }

    @Test
    fun pendingCopies_onlyListsCatalogFiles() = runTest {
        val devDir = tmp.newFolder("dev-models")
        File(devDir, ModelCatalog.byId(ModelId.EG2_270M).fileName).writeBytes(ByteArray(10))
        File(devDir, "notes.txt").writeText("x")
        File(devDir, "probe.litertlm").writeBytes(ByteArray(1))

        val importer = DevModelImporter(devDir, repository, StandardTestDispatcher(testScheduler))
        assertThat(importer.pendingCopies()).containsExactly(ModelId.EG2_270M, 10L)
    }

    @Test
    fun importAll_installsEveryPendingCopyAndDeletesItOnSuccess() = runTest {
        val devDir = tmp.newFolder("dev-models")
        val good = File(devDir, ModelCatalog.byId(ModelId.EG2_270M).fileName).apply { writeBytes(ByteArray(10)) }
        val bad = File(devDir, ModelCatalog.byId(ModelId.EG2_440M).fileName).apply { writeBytes(ByteArray(10)) }

        val result = DevModelImporter(devDir, repository, StandardTestDispatcher(testScheduler)).importAll()

        assertThat(repository.installs.map { it.first }).containsExactly(ModelId.EG2_270M, ModelId.EG2_440M)
        assertThat(result[ModelId.EG2_270M]).isInstanceOf(ModelFileState.Verified::class.java)
        assertThat(result[ModelId.EG2_440M]).isInstanceOf(ModelFileState.Mismatch::class.java)
        assertThat(good.exists()).isFalse()
        assertThat(bad.exists()).isTrue()
    }
}
