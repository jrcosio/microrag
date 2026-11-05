package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F18 T009: the device set is used only once every file matches its hash (data-model §2.1). */
class DeviceSetLoaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun put(path: String, bytes: ByteArray): String {
        val file = File(tmp.root, path)
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
        return sha(bytes)
    }

    private fun manifest(vararg entries: String, parityPx: Int = 1024) {
        File(tmp.root, "manifest.json").writeText("""{"version": 1, "parity_px": $parityPx, "files": [${entries.joinToString()}]}""")
    }

    private fun entry(path: String, sha: String, kind: String, extra: String = "") =
        """{"path": "$path", "sha256": "$sha", "kind": "$kind"$extra}"""

    @Test
    fun aValidSetIsLoadedWithItsPartsInOrder() {
        val image = put("images/a.png", byteArrayOf(1, 2, 3))
        val pdf = put("pdfs/t.pdf", byteArrayOf(4))
        val reading = put("reading.json", """[{"id": "r01", "parts": [{"text": "[1] Doc, página 2"}, {"image": "images/a.png"}, {"text": "¿Pregunta?"}]}]""".toByteArray())
        val contexts = put("contexts.json", """[{"id": "c01", "layouts": {"texto": [{"text": "solo texto"}]}}]""".toByteArray())
        manifest(
            entry("images/a.png", image, "image"),
            entry("pdfs/t.pdf", pdf, "pdf", """, "role": "text", "pages": [1, 3]"""),
            entry("reading.json", reading, "reading"),
            entry("contexts.json", contexts, "contexts"),
        )

        val set = DeviceSetLoader(tmp.root).load()

        assertThat(set.present).isTrue()
        assertThat(set.rejected).isEmpty()
        assertThat(set.parityPx).isEqualTo(1024)
        assertThat(set.images.map { it.first }).containsExactly("images/a.png")
        assertThat(set.pdfs.single().pages).containsExactly(1, 3).inOrder()
        assertThat(set.pdfs.single().role).isEqualTo("text")
        val parts = set.parts(set.reading.single().parts)!!
        assertThat(parts.map { it::class.simpleName }).containsExactly("Text", "Image", "Text").inOrder()
        assertThat((parts[1] as VisionPart.Image).bytes.toList()).containsExactly(1.toByte(), 2.toByte(), 3.toByte()).inOrder()
        assertThat(set.contexts.single().layouts.keys).containsExactly("texto")
    }

    @Test
    fun aFileWithAnotherHashIsRejectedAndNeverUsed() {
        put("images/a.png", byteArrayOf(1, 2, 3))
        val reading = put("reading.json", """[{"id": "r01", "parts": [{"image": "images/a.png"}]}]""".toByteArray())
        manifest(entry("images/a.png", "0".repeat(64), "image"), entry("reading.json", reading, "reading"))

        val set = DeviceSetLoader(tmp.root).load()

        assertThat(set.rejected).containsExactly("images/a.png")
        assertThat(set.images).isEmpty()
        assertThat(set.parts(set.reading.single().parts)).isNull()
    }

    @Test
    fun aMissingFileIsRejected() {
        manifest(entry("images/missing.png", "0".repeat(64), "image"))

        assertThat(DeviceSetLoader(tmp.root).load().rejected).containsExactly("images/missing.png")
    }

    @Test
    fun withoutAManifestOrAFolderTheSetIsAbsent() {
        assertThat(DeviceSetLoader(tmp.root).load().present).isFalse()
        assertThat(DeviceSetLoader(File(tmp.root, "nowhere")).load()).isEqualTo(DeviceSet.ABSENT)
    }
}
