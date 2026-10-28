package com.lisdatasolutions.microrag.domain.model

/**
 * The model files MicroRAG works with, as published on Hugging Face `litert-community`
 * (verified 2026-10-25, F02 research R2). Changing a revision means updating size and hash too.
 */
object ModelCatalog {

    private const val GEMMA4_E2B_REPO = "litert-community/gemma-4-E2B-it-litert-lm"
    private const val GEMMA4_E2B_REVISION = "1caa527c1e88b55f046fcd8d2b4265606680663d"

    val all: List<ModelFile> = listOf(
        ModelFile(
            id = ModelId.GEMMA4_E2B,
            family = ModelFamily.LLM,
            displayName = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            repository = GEMMA4_E2B_REPO,
            revision = GEMMA4_E2B_REVISION,
        ),
        ModelFile(
            id = ModelId.GEMMA4_E2B_GPU,
            family = ModelFamily.LLM,
            displayName = "Gemma 4 E2B (GPU)",
            fileName = "gemma-4-E2B-it-gpu.litertlm",
            sizeBytes = 2_008_432_640L,
            sha256 = "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5",
            repository = GEMMA4_E2B_REPO,
            revision = GEMMA4_E2B_REVISION,
        ),
        ModelFile(
            id = ModelId.EG2_740M,
            family = ModelFamily.EMBEDDING,
            displayName = "EmbeddingGemma 2 740M",
            fileName = "embeddinggemma-2-740m.litertlm",
            sizeBytes = 484_622_336L,
            sha256 = "e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c",
            repository = "litert-community/embeddinggemma-2-740m-litert-lm",
            revision = "24d962e906c7d332c6428e71c9676855024569e2",
        ),
        ModelFile(
            id = ModelId.EG2_440M,
            family = ModelFamily.EMBEDDING,
            displayName = "EmbeddingGemma 2 440M",
            fileName = "embeddinggemma-2-text-vision-440m.litertlm",
            sizeBytes = 387_710_976L,
            sha256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0",
            repository = "litert-community/embeddinggemma-2-text-vision-440m-litert-lm",
            revision = "e301f74d5551b0c2641bd5cb4652a76239d5c5f8",
        ),
        ModelFile(
            id = ModelId.EG2_270M,
            family = ModelFamily.EMBEDDING,
            displayName = "EmbeddingGemma 2 270M",
            fileName = "embeddinggemma-2-text-270m.litertlm",
            sizeBytes = 164_626_432L,
            sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
            repository = "litert-community/embeddinggemma-2-text-270m-litert-lm",
            revision = "9be6e8b90982095dc05c2bd162e4b954ee4dbac7",
        ),
    )

    /**
     * Files the product sets up during onboarding, in download order (F03 FR-014): the embedding
     * model first, then the LLM. The other entries are only used by the debug diagnostics.
     */
    val required: List<ModelFile> = listOf(ModelId.EG2_270M, ModelId.GEMMA4_E2B).map { id -> all.first { it.id == id } }

    fun byId(id: ModelId): ModelFile = all.first { it.id == id }

    /** Every [required] file verified and prepared on a backend: what completes onboarding and lights the Home chip. */
    fun allReady(states: Map<ModelId, ModelFileState>, prepared: Map<ModelId, ComputeBackend>): Boolean =
        required.all { states[it.id] is ModelFileState.Verified && it.id in prepared }
}
