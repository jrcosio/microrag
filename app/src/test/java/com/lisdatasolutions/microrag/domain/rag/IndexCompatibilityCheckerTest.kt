package com.lisdatasolutions.microrag.domain.rag

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.CompatibilityDecision.Compatible
import com.lisdatasolutions.microrag.domain.rag.CompatibilityDecision.Incompatible
import com.lisdatasolutions.microrag.domain.rag.CompatibilityDecision.NeedsMigration
import org.junit.Test

/** F04 research R7: decision taken when an existing index is opened. */
class IndexCompatibilityCheckerTest {

    private val config = RagIndexConfig.current().copy(schemaVersion = 3)
    private val meta = config.toMeta()

    private fun check(meta: Map<String, String>, migrationsFrom: Set<Int> = emptySet()) =
        IndexCompatibilityChecker.check(config, meta, migrationsFrom)

    @Test
    fun sameValues_areCompatible() {
        assertThat(check(meta)).isEqualTo(Compatible)
    }

    @Test
    fun differentVectorSpace_isIncompatibleWithTheField() {
        val cases = mapOf(
            RagMetaKeys.EMBEDDING_MODEL_SHA256 to "0".repeat(64),
            RagMetaKeys.EMBEDDING_DIMS to "768",
            RagMetaKeys.EMBEDDING_DOC_PREFIX to "task: search result | text:",
            RagMetaKeys.EMBEDDING_QUERY_PREFIX to "query: ",
            RagMetaKeys.NORMALIZER_VERSION to "2",
        )
        cases.forEach { (key, found) ->
            assertThat(check(meta + (key to found))).isEqualTo(Incompatible(key, meta.getValue(key), found))
        }
    }

    @Test
    fun missingKey_isIncompatible() {
        RagMetaKeys.VALIDATED.forEach { key ->
            assertThat(check(meta - key)).isEqualTo(Incompatible("missing:$key", meta.getValue(key), null))
        }
    }

    @Test
    fun unreadableOrNewerSchema_isIncompatible() {
        assertThat(check(meta + (RagMetaKeys.SCHEMA_VERSION to "x")))
            .isEqualTo(Incompatible(RagMetaKeys.SCHEMA_VERSION, "3", "x"))
        assertThat(check(meta + (RagMetaKeys.SCHEMA_VERSION to "4")))
            .isEqualTo(Incompatible(RagMetaKeys.SCHEMA_VERSION, "3", "4"))
    }

    @Test
    fun olderSchema_needsMigrationOnlyWithTheWholeChain() {
        val old = meta + (RagMetaKeys.SCHEMA_VERSION to "1")

        assertThat(check(old, migrationsFrom = setOf(1, 2))).isEqualTo(NeedsMigration(from = 1))
        assertThat(check(old, migrationsFrom = setOf(2))).isEqualTo(Incompatible(RagMetaKeys.SCHEMA_VERSION, "3", "1"))
        assertThat(check(old)).isEqualTo(Incompatible(RagMetaKeys.SCHEMA_VERSION, "3", "1"))
    }

    @Test
    fun vectorSpaceMismatch_winsOverMigration() {
        val old = meta + (RagMetaKeys.SCHEMA_VERSION to "1") + (RagMetaKeys.EMBEDDING_DIMS to "768")

        assertThat(check(old, migrationsFrom = setOf(1, 2)))
            .isEqualTo(Incompatible(RagMetaKeys.EMBEDDING_DIMS, "256", "768"))
    }

    @Test
    fun quantDirty_isNotValidated() {
        assertThat(check(meta + (RagMetaKeys.QUANT_DIRTY to "0"))).isEqualTo(Compatible)
        assertThat(check(meta - RagMetaKeys.QUANT_DIRTY)).isEqualTo(Compatible)
    }
}
