package com.lisdatasolutions.microrag.domain.rag

/** What to do with an existing index after reading its `rag_meta`. */
sealed interface CompatibilityDecision {
    data object Compatible : CompatibilityDecision

    /** Older schema with a migration for every version from [from] up to the app's. */
    data class NeedsMigration(val from: Int) : CompatibilityDecision

    data class Incompatible(val field: String, val expected: String, val found: String?) : CompatibilityDecision
}

/**
 * Compares the `rag_meta` of an existing index with the app configuration (F04 research R7).
 * Never searching an index built for another vector space is what constitution III requires.
 */
object IndexCompatibilityChecker {

    /** [migrationsFrom] holds the versions that have a migration to the next one. */
    fun check(config: RagIndexConfig, meta: Map<String, String>, migrationsFrom: Set<Int>): CompatibilityDecision {
        val expected = config.toMeta()
        RagMetaKeys.VALIDATED.firstOrNull { it !in meta }?.let { key ->
            return CompatibilityDecision.Incompatible("missing:$key", expected.getValue(key), null)
        }

        val found = meta.getValue(RagMetaKeys.SCHEMA_VERSION)
        val fileVersion = found.toIntOrNull()
        if (fileVersion == null || fileVersion > config.schemaVersion) {
            return CompatibilityDecision.Incompatible(RagMetaKeys.SCHEMA_VERSION, config.schemaVersion.toString(), found)
        }

        RagMetaKeys.VALIDATED.drop(1).firstOrNull { meta[it] != expected[it] }?.let { key ->
            return CompatibilityDecision.Incompatible(key, expected.getValue(key), meta[key])
        }

        return when {
            fileVersion == config.schemaVersion -> CompatibilityDecision.Compatible
            (fileVersion until config.schemaVersion).all { it in migrationsFrom } ->
                CompatibilityDecision.NeedsMigration(fileVersion)
            else -> CompatibilityDecision.Incompatible(RagMetaKeys.SCHEMA_VERSION, config.schemaVersion.toString(), found)
        }
    }
}
