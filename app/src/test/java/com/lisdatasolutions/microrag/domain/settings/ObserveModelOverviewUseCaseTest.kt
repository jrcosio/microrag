package com.lisdatasolutions.microrag.domain.settings

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.testing.FakeModelSetup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F12 T024: the model rows of Ajustes, from the catalog and the Preparation's own state (P13). */
class ObserveModelOverviewUseCaseTest {
    private val setup = FakeModelSetup()
    private val useCase = ObserveModelOverviewUseCase(setup.useCase, RefreshLocalAiUseCase(setup.repository))

    @Test
    fun nothingIsEmittedBeforeTheFilesAreRescanned() = runTest {
        useCase().first()

        assertThat(setup.repository.refreshes).hasSize(1)
    }

    @Test
    fun answersFirst_thenEmbeddings_withTheCatalogData() = runTest {
        val models = useCase().first()

        assertThat(models.map { it.id }).containsExactly(ModelId.GEMMA4_E2B, ModelId.EG2_270M).inOrder()
        val gemma = ModelCatalog.byId(ModelId.GEMMA4_E2B)
        with(models[0]) {
            assertThat(displayName).isEqualTo(gemma.displayName)
            assertThat(fileName).isEqualTo(gemma.fileName)
            assertThat(sizeBytes).isEqualTo(gemma.sizeBytes)
            assertThat(sha256).isEqualTo(gemma.sha256)
        }
    }

    @Test
    fun onlyTheEmbeddingModelHasDimensions() = runTest {
        val models = useCase().first().associateBy { it.id }

        assertThat(models.getValue(ModelId.EG2_270M).dimensions).isEqualTo(RagParameters.DIMS)
        assertThat(models.getValue(ModelId.GEMMA4_E2B).dimensions).isNull()
    }

    @Test
    fun theStatusIsTheOneOfThePreparation() = runTest {
        setup.ready(ModelId.GEMMA4_E2B, ComputeBackend.GPU)
        setup.preparing(ModelId.EG2_270M)

        val models = useCase().first().associateBy { it.id }
        val preparation = setup.useCase().first()

        assertThat(models.getValue(ModelId.GEMMA4_E2B).status).isEqualTo(preparation.getValue(ModelId.GEMMA4_E2B))
        assertThat(models.getValue(ModelId.GEMMA4_E2B).status).isInstanceOf(ModelSetupStatus.Ready::class.java)
        assertThat(models.getValue(ModelId.EG2_270M).status).isEqualTo(ModelSetupStatus.Preparing)
    }

    @Test
    fun aMissingFileIsNeverReady() = runTest {
        setup.missing(ModelId.GEMMA4_E2B)
        setup.missing(ModelId.EG2_270M)

        assertThat(useCase().first().map { it.status }).containsNoneOf(
            ModelSetupStatus.Ready(ComputeBackend.GPU, ModelCatalog.byId(ModelId.GEMMA4_E2B).sizeBytes),
            ModelSetupStatus.Ready(ComputeBackend.CPU, ModelCatalog.byId(ModelId.EG2_270M).sizeBytes),
        )
        assertThat(useCase().first().none { it.status is ModelSetupStatus.Ready }).isTrue()
    }
}
