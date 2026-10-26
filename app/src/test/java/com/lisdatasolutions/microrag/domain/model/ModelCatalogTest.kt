package com.lisdatasolutions.microrag.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelCatalogTest {

    private val all = ModelCatalog.all

    @Test
    fun catalog_listsTheFivePinnedFiles() {
        assertThat(all).hasSize(5)
        assertThat(all.count { it.family == ModelFamily.LLM }).isEqualTo(2)
        assertThat(all.count { it.family == ModelFamily.EMBEDDING }).isEqualTo(3)
        assertThat(all.map { it.id }).containsExactlyElementsIn(ModelId.entries)
    }

    @Test
    fun catalog_idsAndFileNamesAreUnique() {
        assertThat(all.map { it.id }.toSet()).hasSize(all.size)
        assertThat(all.map { it.fileName }.toSet()).hasSize(all.size)
    }

    @Test
    fun catalog_hashesAndRevisionsAreWellFormed() {
        all.forEach { file ->
            assertThat(file.sha256).matches("[0-9a-f]{64}")
            assertThat(file.revision).matches("[0-9a-f]{40}")
            assertThat(file.sizeBytes).isGreaterThan(0L)
            assertThat(file.repository).startsWith("litert-community/")
        }
    }

    @Test
    fun catalog_matchesResearchR2() {
        val expected = mapOf(
            ModelId.GEMMA4_E2B to Triple(
                "gemma-4-E2B-it.litertlm",
                2_588_147_712L,
                "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            ),
            ModelId.GEMMA4_E2B_GPU to Triple(
                "gemma-4-E2B-it-gpu.litertlm",
                2_008_432_640L,
                "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5",
            ),
            ModelId.EG2_740M to Triple(
                "embeddinggemma-2-740m.litertlm",
                484_622_336L,
                "e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c",
            ),
            ModelId.EG2_440M to Triple(
                "embeddinggemma-2-text-vision-440m.litertlm",
                387_710_976L,
                "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0",
            ),
            ModelId.EG2_270M to Triple(
                "embeddinggemma-2-text-270m.litertlm",
                164_626_432L,
                "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
            ),
        )
        expected.forEach { (id, values) ->
            val file = ModelCatalog.byId(id)
            assertThat(Triple(file.fileName, file.sizeBytes, file.sha256)).isEqualTo(values)
        }
        assertThat(all.sumOf { it.sizeBytes }).isEqualTo(5_633_540_096L)
    }

    @Test
    fun required_isTheEmbeddingModelThenTheLlm() {
        // F03 FR-014, D-023 and D-025: EmbeddingGemma 2 270M first, then the generic Gemma 4 E2B file.
        assertThat(ModelCatalog.required.map { it.id }).containsExactly(ModelId.EG2_270M, ModelId.GEMMA4_E2B).inOrder()
        assertThat(ModelCatalog.required.sumOf { it.sizeBytes }).isEqualTo(2_752_774_144L)
    }

    @Test
    fun downloadUrl_isPinnedToTheRevision() {
        val file = ModelCatalog.byId(ModelId.EG2_270M)
        assertThat(file.downloadUrl).isEqualTo(
            "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/" +
                "9be6e8b90982095dc05c2bd162e4b954ee4dbac7/embeddinggemma-2-text-270m.litertlm",
        )
        all.forEach { assertThat(it.downloadUrl).startsWith("https://huggingface.co/${it.repository}/resolve/${it.revision}/") }
    }
}
