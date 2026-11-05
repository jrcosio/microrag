package com.lisdatasolutions.microrag.data.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.rag.RagSchema
import java.io.File
import org.junit.Test

/** F17 T005 (research R7): `chat_schema.sql` and the steps from v1; F20 T005 (research R9): v3 and its page sources. */
class ChatSchemaTest {

    private val schema = RagSchema.split(File("src/main/assets/chat_schema.sql").readText())

    @Test
    fun theSchemaHasTheScopeTablesAfterTheF11Ones() {
        assertThat(schema.map { it.substringAfter("EXISTS ").substringBefore(" ") }).containsExactly(
            "chat_meta", "conversations", "conversations_updated", "turns", "sources", "conversation_scope", "last_scope",
        ).inOrder()
        assertThat(ChatSchema.VERSION).isEqualTo(3)
    }

    @Test
    fun migrationFromTwo_addsTheSourceKindAndThePageTokens() {
        assertThat(ChatMigrations.all.single { it.from == 2 }.statements).containsExactly(
            "ALTER TABLE sources ADD COLUMN kind TEXT NOT NULL DEFAULT 'chunk' CHECK (kind IN ('chunk','page','image'))",
            "ALTER TABLE turns ADD COLUMN page_tokens INTEGER",
        ).inOrder()
    }

    /** As RagSchemaTest: an added column is the very column of its CREATE TABLE, and a new table is the whole statement. */
    @Test
    fun everyMigration_endsWhereChatSchemaSqlIs() {
        assertThat(ChatMigrations.all.map { it.from }).isEqualTo((1 until ChatSchema.VERSION).toList())
        ChatMigrations.all.flatMap { it.statements }.forEach { statement ->
            val added = Regex("^ALTER TABLE (\\w+) ADD COLUMN (.+)$").find(statement)?.groupValues
            when {
                added != null -> {
                    val table = schema.single { it.startsWith("CREATE TABLE IF NOT EXISTS ${added[1]} ") }.replace(Regex("\\s+"), " ")
                    assertThat(table).contains(added[2])
                }
                statement.startsWith("CREATE TABLE IF NOT EXISTS ") -> assertThat(schema).contains(statement)
                else -> error("Unexpected migration statement: $statement")
            }
        }
    }
}
