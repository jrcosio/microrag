package com.lisdatasolutions.microrag.data.rag

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import java.io.File
import org.junit.Test

/** F04 data-model §1: the shared schema file and how the app splits it. */
class RagSchemaTest {

    private val sql = File("src/main/assets/schema.sql").readText()

    @Test
    fun split_givesTheElevenStatementsInOrderWithWholeTriggers() {
        val statements = RagSchema.split(sql)

        assertThat(statements.map { it.substringBefore(" IF NOT EXISTS").substringBefore(" =") }).containsExactly(
            "PRAGMA foreign_keys",
            "CREATE TABLE",
            "CREATE TABLE",
            "CREATE INDEX",
            "CREATE INDEX",
            "CREATE TABLE",
            "CREATE INDEX",
            "CREATE VIRTUAL TABLE",
            "CREATE TRIGGER",
            "CREATE TRIGGER",
            "CREATE TRIGGER",
        ).inOrder()
        statements.filter { it.startsWith("CREATE TRIGGER") }.forEach { trigger ->
            assertThat(trigger).contains(" BEGIN")
            assertThat(trigger).endsWith("END")
        }
        statements.forEach { assertThat(it).doesNotContain("--") }
    }

    @Test
    fun documentStatus_matchesTheStatusCheck() {
        val check = Regex("""status\s+TEXT[^,]*CHECK \(status IN \(([^)]*)\)\)""").find(sql)!!.groupValues[1]
        val values = check.split(",").map { it.trim().removeSurrounding("'") }

        assertThat(values).containsExactlyElementsIn(DocumentStatus.entries.map { it.sqlValue })
    }

    @Test
    fun migrations_areOrderedUniqueAndBelowTheCurrentVersion() {
        val from = RagMigrations.all.map { it.from }

        assertThat(from).isInStrictOrder()
        assertThat(from.all { it in 0 until RagIndexConfig.SCHEMA_VERSION }).isTrue()
        // Every released version has its step up to the current one (F06: 1 -> 2, D-038; F08: 2 -> 3, R1).
        assertThat(from).containsExactlyElementsIn(1 until RagIndexConfig.SCHEMA_VERSION).inOrder()
    }

    @Test
    fun migrationFromTwo_addsTheLibraryColumnsAndIndexes() {
        val statements = RagMigrations.all.single { it.from == 2 }.statements

        assertThat(statements).containsExactly(
            "ALTER TABLE documents ADD COLUMN name_norm TEXT",
            "ALTER TABLE documents ADD COLUMN replaces_id INTEGER REFERENCES documents(id) ON DELETE CASCADE",
            "CREATE INDEX IF NOT EXISTS idx_documents_created ON documents(created_at_ms, id)",
            "CREATE INDEX IF NOT EXISTS idx_documents_replaces ON documents(replaces_id)",
        ).inOrder()
    }

    @Test
    fun everyMigration_endsWhereSchemaSqlIs() {
        val schema = RagSchema.split(sql)
        val documents = schema.single { it.startsWith("CREATE TABLE IF NOT EXISTS documents") }.replace(Regex("\\s+"), " ")

        RagMigrations.all.flatMap { it.statements }.forEach { statement ->
            val column = Regex("^ALTER TABLE documents ADD COLUMN (.+)$").find(statement)?.groupValues?.get(1)
            when {
                column != null -> assertThat(documents).contains(column)
                statement.startsWith("CREATE INDEX") -> assertThat(schema).contains(statement)
                else -> error("Unexpected migration statement: $statement")
            }
        }
    }
}
