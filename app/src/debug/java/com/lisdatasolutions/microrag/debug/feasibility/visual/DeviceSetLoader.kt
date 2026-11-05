package com.lisdatasolutions.microrag.debug.feasibility.visual

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One file of `manifest.json` (data-model §1.6). */
@Serializable
data class DeviceSetFile(
    val path: String,
    val sha256: String,
    /** image, pdf, decode, reading or contexts. */
    val kind: String,
    /** pdf only: text, scanned, landscape or restricted. */
    val role: String? = null,
    /** pdf only: the pages rasterized for PARITY (1-based). */
    val pages: List<Int> = emptyList(),
)

@Serializable
data class DeviceSetManifest(
    val version: Int,
    /** Resolution chosen by the desktop's retrieval stage, for PARITY and PROJECTION (R12, R13). */
    @SerialName("parity_px") val parityPx: Int? = null,
    /** Tokens per image chosen by the same stage, for PROJECTION. */
    @SerialName("tokens") val chosenTokens: Int? = null,
    val files: List<DeviceSetFile>,
)

/** A part as exported by the desktop: exactly one of [text] and [image] (a device-set path). */
@Serializable
data class RawPart(val text: String? = null, val image: String? = null)

@Serializable
data class ReadingItem(val id: String, val parts: List<RawPart>)

@Serializable
data class ContextItem(val id: String, val layouts: Map<String, List<RawPart>>)

data class DeviceSetPdf(val path: String, val file: File, val role: String?, val pages: List<Int>)

/** The verified device set; files whose hash does not match are in [rejected] and never used (constitution V). */
data class DeviceSet(
    val present: Boolean,
    val verified: Map<String, File>,
    val shaOf: Map<String, String>,
    val rejected: List<String>,
    val parityPx: Int?,
    val chosenTokens: Int?,
    val images: List<Pair<String, File>>,
    val pdfs: List<DeviceSetPdf>,
    val decode: List<Pair<String, File>>,
    val reading: List<ReadingItem>,
    val contexts: List<ContextItem>,
) {
    /** The parts of a message, or null when one of its images is missing or rejected. */
    fun parts(raw: List<RawPart>): List<VisionPart>? = raw.map { part ->
        when {
            part.text != null -> VisionPart.Text(part.text)
            part.image != null -> VisionPart.Image(verified[part.image]?.readBytes() ?: return null)
            else -> return null
        }
    }

    companion object {
        val ABSENT = DeviceSet(
            present = false, verified = emptyMap(), shaOf = emptyMap(), rejected = emptyList(), parityPx = null, chosenTokens = null,
            images = emptyList(), pdfs = emptyList(), decode = emptyList(), reading = emptyList(), contexts = emptyList(),
        )
    }
}

/** Reads `dev-visual/manifest.json`, copied with adb from the desktop's `build/visual/device-set` (R13). */
class DeviceSetLoader(private val dir: File) {

    fun ensureDir() {
        dir.mkdirs()
    }

    fun load(): DeviceSet {
        val manifestFile = File(dir, MANIFEST)
        if (!manifestFile.isFile) return DeviceSet.ABSENT
        val manifest = json.decodeFromString(DeviceSetManifest.serializer(), manifestFile.readText())
        val verified = mutableMapOf<String, File>()
        val rejected = mutableListOf<String>()
        manifest.files.forEach { entry ->
            val file = File(dir, entry.path)
            if (file.isFile && sha256(file) == entry.sha256) verified[entry.path] = file else rejected += entry.path
        }
        fun ok(kind: String) = manifest.files.filter { it.kind == kind && it.path in verified }
        val reading = ok("reading").firstOrNull()?.let {
            json.decodeFromString<List<ReadingItem>>(verified.getValue(it.path).readText())
        }.orEmpty()
        val contexts = ok("contexts").firstOrNull()?.let {
            json.decodeFromString<List<ContextItem>>(verified.getValue(it.path).readText())
        }.orEmpty()
        return DeviceSet(
            present = true,
            verified = verified,
            shaOf = manifest.files.filter { it.path in verified }.associate { it.path to it.sha256 },
            rejected = rejected,
            parityPx = manifest.parityPx,
            chosenTokens = manifest.chosenTokens,
            images = ok("image").map { it.path to verified.getValue(it.path) },
            pdfs = ok("pdf").map { DeviceSetPdf(it.path, verified.getValue(it.path), it.role, it.pages) },
            decode = ok("decode").map { it.path to verified.getValue(it.path) },
            reading = reading,
            contexts = contexts,
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val BUFFER = 1 shl 16
        val json = Json { ignoreUnknownKeys = true }
    }
}
