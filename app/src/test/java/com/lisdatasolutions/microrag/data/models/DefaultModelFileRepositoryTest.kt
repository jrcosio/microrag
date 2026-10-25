package com.lisdatasolutions.microrag.data.models

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultModelFileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val content = Random(7).nextBytes(3 * 1024 * 1024 + 5)
    private val otherContent = Random(8).nextBytes(1024)
    private val file = testFile(ModelId.EG2_270M, "small.litertlm", content)
    private val second = testFile(ModelId.EG2_440M, "second.litertlm", otherContent)

    private var free = Long.MAX_VALUE

    private fun TestScope.repository(): Pair<DefaultModelFileRepository, File> {
        val modelsDir = File(tmp.root, "no_backup/models")
        val repo = DefaultModelFileRepository(
            modelsDir = modelsDir,
            catalog = listOf(file, second),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            logger = DefaultAppLogger(isEnabled = false),
            freeBytes = { free },
        )
        return repo to modelsDir
    }

    private fun source(bytes: ByteArray, name: String = "source.bin") =
        File(tmp.root, "dev-models/$name").apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    @Test
    fun installVerified_movesTheFileIntoPlaceAndDeletesTheSource() = runTest {
        val (repo, modelsDir) = repository()
        val src = source(content)

        val state = repo.installVerified(file.id, src, deleteSourceOnSuccess = true)

        val target = File(modelsDir, file.fileName)
        assertThat(state).isEqualTo(ModelFileState.Verified(target))
        assertThat(target.readBytes()).isEqualTo(content)
        assertThat(modelsDir.listFiles()!!.map { it.name }).containsExactly(file.fileName)
        assertThat(src.exists()).isFalse()
        assertThat(repo.pathOf(file.id)).isEqualTo(target)
    }

    @Test
    fun installVerified_withWrongHash_keepsNothingAndKeepsTheSource() = runTest {
        val (repo, modelsDir) = repository()
        val src = source(content.copyOf(content.size - 1))

        val state = repo.installVerified(file.id, src, deleteSourceOnSuccess = true)

        assertThat(state).isInstanceOf(ModelFileState.Mismatch::class.java)
        assertThat((state as ModelFileState.Mismatch).expectedSha256).isEqualTo(file.sha256)
        assertThat(modelsDir.listFiles().orEmpty()).isEmpty()
        assertThat(src.exists()).isTrue()
        assertThat(repo.pathOf(file.id)).isNull()
    }

    @Test
    fun installVerified_withoutSpace_failsWithoutCopying() = runTest {
        val (repo, modelsDir) = repository()
        free = 1024
        val src = source(content)

        val state = repo.installVerified(file.id, src, deleteSourceOnSuccess = true)

        assertThat(state).isInstanceOf(ModelFileState.Failed::class.java)
        assertThat(modelsDir.listFiles().orEmpty()).isEmpty()
        assertThat(src.exists()).isTrue()
    }

    @Test
    fun refresh_reportsPresentFilesAsUnverifiedAndOthersAsMissing() = runTest {
        val (repo, modelsDir) = repository()
        modelsDir.mkdirs()
        File(modelsDir, file.fileName).writeBytes(content)
        File(modelsDir, "${second.fileName}.part").writeBytes(otherContent)

        repo.refresh()

        assertThat(repo.states.value[file.id]).isEqualTo(ModelFileState.Unverified(File(modelsDir, file.fileName)))
        assertThat(repo.states.value[second.id]).isEqualTo(ModelFileState.Missing)
        // Leftover partial copies from an interrupted import are removed.
        assertThat(File(modelsDir, "${second.fileName}.part").exists()).isFalse()
        assertThat(repo.pathOf(file.id)).isNull()
    }

    @Test
    fun verify_intactFileBecomesVerifiedAndCorruptFileIsDeleted() = runTest {
        val (repo, modelsDir) = repository()
        modelsDir.mkdirs()
        File(modelsDir, file.fileName).writeBytes(content)
        val corrupt = File(modelsDir, second.fileName).apply { writeBytes(otherContent.reversedArray()) }
        repo.refresh()

        assertThat(repo.verify(file.id)).isEqualTo(ModelFileState.Verified(File(modelsDir, file.fileName)))
        assertThat(repo.verify(second.id)).isInstanceOf(ModelFileState.Mismatch::class.java)
        assertThat(corrupt.exists()).isFalse()
        assertThat(repo.pathOf(second.id)).isNull()

        repo.refresh()
        assertThat(repo.states.value[file.id]).isInstanceOf(ModelFileState.Verified::class.java)
        assertThat(repo.states.value[second.id]).isEqualTo(ModelFileState.Missing)
    }

    @Test
    fun verifyingProgress_isMonotonicAndReachesTheTotal() = runTest {
        val (repo, _) = repository()
        val progress = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.states.collect { states ->
                (states[file.id] as? ModelFileState.Verifying)?.let { progress += it.bytesDone }
            }
        }

        repo.installVerified(file.id, source(content), deleteSourceOnSuccess = false)

        assertThat(progress).isNotEmpty()
        assertThat(progress).isInOrder()
        assertThat(progress.last()).isEqualTo(content.size.toLong())
    }

    private fun testFile(id: ModelId, name: String, bytes: ByteArray) = ModelFile(
        id = id,
        family = ModelFamily.EMBEDDING,
        displayName = name,
        fileName = name,
        sizeBytes = bytes.size.toLong(),
        sha256 = Sha256.of(ByteArrayInputStream(bytes)),
        repository = "litert-community/test",
        revision = "0".repeat(40),
    )
}
