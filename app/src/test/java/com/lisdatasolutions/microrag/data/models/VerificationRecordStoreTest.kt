package com.lisdatasolutions.microrag.data.models

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerificationRecordStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val entry = VerificationEntry(
        fileName = "a.litertlm",
        sha256 = "0".repeat(64),
        sizeBytes = 10,
        lastModified = 1234,
        verifiedAt = "2026-10-26T10:00:00Z",
        preparedBackend = "GPU",
        preparedAt = "2026-10-26T10:01:00Z",
    )

    @Test
    fun writeThenRead_roundTripsAndLeavesNoTemporaryFile() {
        val file = File(tmp.root, "models/verification.json")
        val store = VerificationRecordStore(file)

        store.write(listOf(entry, entry.copy(fileName = "b.litertlm", preparedBackend = null, preparedAt = null)))

        assertThat(store.read()).containsExactly(
            "a.litertlm", entry,
            "b.litertlm", entry.copy(fileName = "b.litertlm", preparedBackend = null, preparedAt = null),
        )
        assertThat(file.parentFile!!.list()!!.toList()).containsExactly("verification.json")
    }

    @Test
    fun missingOrCorruptRecord_isEmpty() {
        val file = File(tmp.root, "verification.json")
        assertThat(VerificationRecordStore(file).read()).isEmpty()

        file.writeText("{ not json")
        assertThat(VerificationRecordStore(file).read()).isEmpty()

        file.writeText("""{"version":1,"entries":[{"fileName":"x"}]}""")
        assertThat(VerificationRecordStore(file).read()).isEmpty()
    }

    @Test
    fun unknownFields_areIgnored() {
        val file = File(tmp.root, "verification.json")
        file.writeText(
            """{"version":2,"future":true,"entries":[{"fileName":"a.litertlm","sha256":"${"0".repeat(64)}","sizeBytes":10,
               "lastModified":1234,"verifiedAt":"t","extra":1}]}""",
        )

        assertThat(VerificationRecordStore(file).read().getValue("a.litertlm").sizeBytes).isEqualTo(10)
    }
}
