package com.lisdatasolutions.microrag.data.download

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** contracts/download-api.md: protocol table, redirects, headers and cancellation (T027, T054). */
class ModelDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val content = Random(3).nextBytes(300 * 1024 + 7)
    private val file = ModelFile(
        id = ModelId.EG2_270M,
        family = ModelFamily.EMBEDDING,
        displayName = "test",
        fileName = "test.litertlm",
        sizeBytes = content.size.toLong(),
        sha256 = "0".repeat(64),
        repository = "litert-community/test",
        revision = "0".repeat(40),
    )
    private lateinit var partial: File

    @Before
    fun setUp() {
        server.start()
        partial = File(tmp.root, "downloads/test.litertlm.download")
    }

    @After
    fun tearDown() = server.close()

    private fun downloader(path: String = "/model", requireHttps: Boolean = false, hosts: Set<String> = setOf(server.hostName)) =
        ModelDownloader(
            userAgent = "MicroRAG/test",
            allowedHosts = HostAllowlist(hosts),
            requireHttps = requireHttps,
            ioDispatcher = Dispatchers.IO,
            urlOf = { server.url(path).toString() },
        )

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    private fun full() = MockResponse.Builder().code(200).body(body(content)).build()

    private fun partialResponse(from: Int) = MockResponse.Builder()
        .code(206)
        .addHeader("Content-Range", "bytes $from-${content.size - 1}/${content.size}")
        .body(body(content.copyOfRange(from, content.size)))
        .build()

    private fun run(downloader: ModelDownloader = downloader(), progress: MutableList<Long> = mutableListOf()) =
        runBlocking { downloader.download(file, partial) { progress += it } }

    @Test
    fun fullResponse_writesTheFileAndReportsGrowingProgress() {
        server.enqueue(full())
        val progress = mutableListOf<Long>()

        assertThat(run(progress = progress)).isEqualTo(DownloadResult.Complete)
        assertThat(partial.readBytes()).isEqualTo(content)
        assertThat(progress).isInStrictOrder()
        assertThat(progress.last()).isEqualTo(content.size.toLong())
    }

    @Test
    fun resume_asksOnlyForTheMissingBytes() {
        val half = content.size / 2
        partial.parentFile!!.mkdirs()
        partial.writeBytes(content.copyOfRange(0, half))
        server.enqueue(partialResponse(half))

        assertThat(run()).isEqualTo(DownloadResult.Complete)

        val request = server.takeRequest()
        assertThat(request.headers["Range"]).isEqualTo("bytes=$half-")
        assertThat(partial.readBytes()).isEqualTo(content)
        // SC-002: resuming at 50 % transfers only what was missing.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun resume_whenTheServerIgnoresTheRange_startsOverFromZero() {
        partial.parentFile!!.mkdirs()
        partial.writeBytes(ByteArray(1000) { 9 })
        server.enqueue(full())

        assertThat(run()).isEqualTo(DownloadResult.Complete)
        assertThat(partial.readBytes()).isEqualTo(content)
    }

    @Test
    fun resume_withAnotherContentRange_discardsThePartialAndRetriesOnce() {
        partial.parentFile!!.mkdirs()
        partial.writeBytes(content.copyOfRange(0, 5000))
        server.enqueue(partialResponse(4000))
        server.enqueue(full())

        assertThat(run()).isEqualTo(DownloadResult.Complete)
        assertThat(server.takeRequest().headers["Range"]).isEqualTo("bytes=5000-")
        assertThat(server.takeRequest().headers["Range"]).isNull()
        assertThat(partial.readBytes()).isEqualTo(content)
    }

    @Test
    fun rangeNotSatisfiable_withTheWholeFile_isComplete_otherwiseStartsOver() {
        partial.parentFile!!.mkdirs()
        partial.writeBytes(content)
        server.enqueue(MockResponse.Builder().code(416).build())
        assertThat(run()).isEqualTo(DownloadResult.Complete)

        partial.writeBytes(content.copyOfRange(0, 10))
        server.enqueue(MockResponse.Builder().code(416).build())
        server.enqueue(full())
        assertThat(run()).isEqualTo(DownloadResult.Complete)
        assertThat(partial.readBytes()).isEqualTo(content)
    }

    @Test
    fun redirects_onlyToAllowedHostsAndHttps() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/cdn/model?sig=1").build())
        server.enqueue(full())
        assertThat(run()).isEqualTo(DownloadResult.Complete)
        assertThat(server.takeRequest().target).isEqualTo("/model")
        assertThat(server.takeRequest().target).isEqualTo("/cdn/model?sig=1")

        partial.delete()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://evil.example.com/model").build())
        assertThat(run()).isEqualTo(DownloadResult.Failed(DownloadError.ForbiddenHost("evil.example.com")))

        assertThat(run(downloader(requireHttps = true))).isEqualTo(DownloadResult.Failed(DownloadError.InsecureScheme))
    }

    @Test
    fun tooManyRedirects_fail() {
        repeat(6) { server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/loop").build()) }

        assertThat(run()).isEqualTo(DownloadResult.Failed(DownloadError.TooManyRedirects))
    }

    @Test
    fun requests_carryNoIdentifiers() {
        server.enqueue(full())
        run()

        val headers = server.takeRequest().headers
        assertThat(headers["User-Agent"]).isEqualTo("MicroRAG/test")
        assertThat(headers["Accept-Encoding"]).isEqualTo("identity")
        assertThat(headers.names().map { it.lowercase() }).containsNoneOf("cookie", "authorization", "x-device-id")
    }

    @Test
    fun httpErrorsAndShortBodies_areReported() {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertThat(run()).isEqualTo(DownloadResult.Failed(DownloadError.Http(404)))

        server.enqueue(MockResponse.Builder().code(503).build())
        assertThat(run()).isEqualTo(DownloadResult.Failed(DownloadError.Http(503)))

        // Promises the whole file but sends half: a short read, kept for the next resume.
        server.enqueue(
            MockResponse.Builder().code(206)
                .addHeader("Content-Range", "bytes 0-${content.size - 1}/${content.size}")
                .body(body(content.copyOfRange(0, content.size / 2)))
                .build(),
        )
        assertThat(run()).isEqualTo(DownloadResult.Failed(DownloadError.Network("short read")))
        assertThat(partial.length()).isEqualTo((content.size / 2).toLong())
    }

    @Test
    fun cancellation_stopsPromptlyAndKeepsWhatWasWritten() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(body(content)).throttleBody(16 * 1024, 200, TimeUnit.MILLISECONDS).build())
        var lastProgress = 0L
        val job = async(Dispatchers.Default) { downloader().download(file, partial) { lastProgress = it } }
        withTimeout(5_000) { while (lastProgress < 32 * 1024) kotlinx.coroutines.delay(20) }

        val started = System.nanoTime()
        job.cancelAndJoin()
        val stopMs = (System.nanoTime() - started) / 1_000_000

        assertThat(job.isCancelled).isTrue()
        assertThat(runCatching { job.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(stopMs).isLessThan(2_000)
        assertThat(partial.length()).isAtLeast(32 * 1024L)
        assertThat(partial.length()).isLessThan(content.size.toLong())
    }
}
