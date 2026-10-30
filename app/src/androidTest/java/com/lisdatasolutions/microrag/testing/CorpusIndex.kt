package com.lisdatasolutions.microrag.testing

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.ChunkEmbeddingSession
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentImporter
import com.lisdatasolutions.microrag.data.ingest.ImportResult
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.ingest.SafDocumentSource
import com.lisdatasolutions.microrag.data.ingest.SourceInfo
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The public evaluation corpus, from the test APK's assets, imported and indexed by the real runner and
 * engine on [temp], with the manifest titles as names, as the desktop index has them (F10 research R11).
 * Shared by RetrievalParityTest and ChatBenchmarkTest (F11 T061). The user's library is never read; the
 * copied files go to [root], which the caller deletes.
 */
class CorpusIndex(private val temp: TempRagIndex, private val root: File, private val pdf: PlatformPdfOpener) {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val context = instrumentation.targetContext

    /** Imports and indexes every document, on the backend this device picks for indexing (F07); manifest id → document id. */
    suspend fun build(coordinator: EngineCoordinator, factory: EngineFactory, embeddingModel: File): Map<String, Long> {
        val documents = importCorpus()
        val session = ChunkEmbeddingSession(coordinator, factory, { embeddingModel }, InMemoryIngestRunStore(), APP_VERSION, temp.logger)
        val runner = IngestRunner(temp.index, DocumentExtractor(pdf), session, InMemoryIngestRunStore(), FakeThermalStatusSource(), { Long.MAX_VALUE }, Dispatchers.IO, temp.logger)
        runner.process(documents.values.toList()) { }
        documents.values.forEach { assertThat(temp.index.document(it)!!.status).isEqualTo(DocumentStatus.DONE) }
        return documents
    }

    /** Copies `docs/` out of the test assets and imports each file as «manifest title».ext. */
    private suspend fun importCorpus(): Map<String, Long> {
        val manifest = Json.parseToJsonElement(assets.open("manifest.json").bufferedReader().use { it.readText() }).jsonArray
        val source = SafDocumentSource(context.contentResolver)
        val importer = DocumentImporter(source, DocumentFileStore(File(root, "documents")), pdf, temp.index, { Long.MAX_VALUE / 2 }, temp.logger)
        return manifest.associate { element ->
            val entry = element.jsonObject
            val id = entry.getValue("id").jsonPrimitive.content
            val format = entry.getValue("format").jsonPrimitive.content
            val file = File(root, "$id.$format").apply { assets.open("docs/$id.$format").use { input -> outputStream().use(input::copyTo) } }
            val name = "${entry.getValue("title").jsonPrimitive.content}.$format"
            val uri = Uri.fromFile(file).toString()
            id to (importer.import(uri, SourceInfo(name, file.length(), null)) as ImportResult.Imported).documentId
        }
    }

    private companion object {
        const val APP_VERSION = 1L
    }
}
