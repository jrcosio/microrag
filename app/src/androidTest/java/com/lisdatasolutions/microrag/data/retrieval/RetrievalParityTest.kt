package com.lisdatasolutions.microrag.data.retrieval

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
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
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PyText
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.InstalledEmbeddingModel
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F10 T042 (research R11, FR-027, SC-005): the public evaluation corpus, from the test APK's assets, is
 * indexed by the real runner and engine on a temporary index, with the manifest titles as names, as the
 * desktop index has them. The 50 questions of the parity sample are then retrieved, and Recall@bloques
 * must stay within 5 points of the desktop's on the same questions. The user's library is never read.
 * Logged with `retrieval-parity`: numbers and question ids only.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class RetrievalParityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "parity-${UUID.randomUUID()}").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val pdf = PlatformPdfOpener()
    private val models = InstalledEmbeddingModel(context)
    private val questions = RetrievalGoldenFiles.parity(assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() })

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    @Test
    fun recallAtBlocksMatchesTheDesktop() = runBlocking<Unit> {
        assumeTrue("the embedding model is not installed", models.isInstalled)
        assumeTrue(pdf.isAvailable)
        val documents = importCorpus()

        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            // The backend this device picks for indexing, as the app does (GPU check of F07).
            val session = ChunkEmbeddingSession(coordinator, factory, { models.file }, InMemoryIngestRunStore(), APP_VERSION, temp.logger)
            val runner = IngestRunner(temp.index, DocumentExtractor(pdf), session, InMemoryIngestRunStore(), FakeThermalStatusSource(), { Long.MAX_VALUE }, Dispatchers.IO, temp.logger)
            runner.process(documents.values.toList()) { }
            documents.values.forEach { assertThat(temp.index.document(it)!!.status).isEqualTo(DocumentStatus.DONE) }

            val retriever = HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models), temp.logger)
            val phone = mutableMapOf<String, Int>()
            val notExtracted = mutableListOf<String>()
            for (question in questions) {
                val documentId = documents.getValue(question.doc)
                val relevant = relevantOrdinals(question.evidence, temp.index.storedChunkContents(documentId))
                if (relevant.isEmpty()) {
                    notExtracted += question.id
                    continue
                }
                val outcome = retriever.retrieve(RetrievalRequest(question.question, question.previous)).outcome
                val blocks = (outcome as? RetrievalOutcome.Blocks)?.blocks.orEmpty()
                phone[question.id] = if (blocks.any { b -> b.documentId == documentId && relevant.any { it in b.firstOrdinal..b.lastOrdinal } }) 1 else 0
            }

            val compared = questions.filter { it.id in phone }
            val phoneRecall = compared.sumOf { phone.getValue(it.id) }.toDouble() / compared.size
            val desktopRecall = compared.sumOf { it.desktopHit }.toDouble() / compared.size
            val changed = compared.filter { phone.getValue(it.id) != it.desktopHit }.joinToString(",") { "${it.id}:${it.desktopHit}→${phone.getValue(it.id)}" }
            Log.i(
                TAG,
                "retrieval-parity model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} questions=${questions.size} " +
                    "not_extracted=${notExtracted.size} phone=%.3f desktop=%.3f diff=%+.3f changed=%s not_extracted_ids=%s".format(
                        Locale.ROOT, phoneRecall, desktopRecall, phoneRecall - desktopRecall, changed, notExtracted.joinToString(","),
                    ),
            )

            // SC-008: no question, nor its previous one, ever reaches the log.
            val texts = questions.flatMap { listOfNotNull(it.question, it.previous) }
            temp.logger.lines.toList().forEach { line -> texts.forEach { assertWithMessage("log line").that(line).doesNotContain(it) } }
            assertWithMessage("|phone − desktop| (SC-005)").that(abs(phoneRecall - desktopRecall)).isAtMost(MAX_DIFFERENCE)
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    /** Copies `docs/` out of the test assets and imports each file as «manifest title».ext; manifest id → document id. */
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
        const val TAG = "MicroRAG"
        const val APP_VERSION = 1L
        const val MAX_DIFFERENCE = 0.05

        /** `SENTENCE` of offline/eval/testset.py, on text whose spaces [normWs] already joined. */
        val SENTENCE = Regex("(?<=[.!?;:]) +")

        /** `norm_ws`: `" ".join(text.split())`, with Python's whitespace. */
        fun normWs(text: String): String {
            val words = mutableListOf<String>()
            val word = StringBuilder()
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (PyText.isSpace(codePoint)) {
                    if (word.isNotEmpty()) words += word.toString().also { word.clear() }
                } else {
                    word.appendCodePoint(codePoint)
                }
                i += Character.charCount(codePoint)
            }
            if (word.isNotEmpty()) words += word.toString()
            return words.joinToString(" ")
        }

        /** `derive_relevant`: chunks holding the evidence; if none holds it whole, those holding its longest sentence. */
        fun relevantOrdinals(evidence: String, chunks: Map<Int, String>): List<Int> {
            val target = normWs(evidence)
            val normalized = chunks.mapValues { normWs(it.value) }
            val hits = normalized.filterValues { target in it }.keys.toList()
            if (hits.isNotEmpty()) return hits
            val longest = SENTENCE.split(target).maxBy { it.codePointCount(0, it.length) }
            return normalized.filterValues { longest in it }.keys.toList()
        }
    }
}
