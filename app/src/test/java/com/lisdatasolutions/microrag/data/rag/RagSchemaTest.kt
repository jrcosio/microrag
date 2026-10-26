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
    fun split_givesTheNineStatementsInOrderWithWholeTriggers() {
        val statements = RagSchema.split(sql)

        assertThat(statements.map { it.substringBefore(" IF NOT EXISTS").substringBefore(" =") }).containsExactly(
            "PRAGMA foreign_keys",
            "CREATE TABLE",
            "CREATE TABLE",
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
        assertThat(RagMigrations.all).isEmpty()
    }
}
