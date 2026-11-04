package com.lisdatasolutions.microrag.data.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.rag.RagSchema
import java.io.File
import org.junit.Test

/** F17 T005 (research R7): `chat_schema.sql` v2 and the step from v1. */
class ChatSchemaTest {

    private val schema = RagSchema.split(File("src/main/assets/chat_schema.sql").readText())

    @Test
    fun theSchemaHasTheScopeTablesAfterTheF11Ones() {
        assertThat(schema.map { it.substringAfter("EXISTS ").substringBefore(" ") }).containsExactly(
            "chat_meta", "conversations", "conversations_updated", "turns", "sources", "conversation_scope", "last_scope",
        ).inOrder()
        assertThat(ChatSchema.VERSION).isEqualTo(2)
    }

    @Test
    fun everyMigration_endsWhereChatSchemaSqlIs() {
        assertThat(ChatMigrations.all.map { it.from }).isEqualTo((1 until ChatSchema.VERSION).toList())
        ChatMigrations.all.flatMap { it.statements }.forEach { assertThat(schema).contains(it) }
    }
}
