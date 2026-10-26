package com.lisdatasolutions.microrag.data.rag

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.Percentiles
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-008 (research R9): p50/p95 of exact vector search and BM25 search over 10,000 synthetic chunks.
 * A reference for F10 and F14 (INT8 only if the vector p95 exceeds 20 ms), not a threshold of F04.
 */
@RunWith(AndroidJUnit4::class)
class RagIndexBenchmarkTest {

    @Test
    fun searchLatencyWith10kChunks() = runBlocking<Unit> {
        TempRagIndex().use { temp ->
            val index = temp.index
            val random = Random(SEED)

            val insertStarted = System.nanoTime()
            repeat(DOCUMENTS) { d ->
                val doc = index.addDocument(NewDocument(displayName = "Documento $d", contentHash = "bench-$d"))
                (0 until CHUNKS_PER_DOCUMENT).chunked(BATCH).forEach { ordinals ->
                    index.insertChunks(
                        doc,
                        ordinals.map { o ->
                            chunk(o, content = sentence(random, WORDS_PER_CHUNK), headingPath = sentence(random, 3), vector = unitVector(d * 1_000 + o))
                        },
                    )
                }
                // Searches only see published documents (F07 rule N7).
                index.updateStatus(doc, DocumentStatus.DONE, chunkCount = CHUNKS_PER_DOCUMENT)
            }
            val insertSeconds = (System.nanoTime() - insertStarted) / 1e9
            assertThat(index.counts().chunks).isEqualTo(DOCUMENTS * CHUNKS_PER_DOCUMENT)

            val vectorMs = measure { index.vectorSearch(unitVector(-1 - it), k = 50) }
            val textMs = measure { index.textSearch(sentence(random, 4), k = 50) }

            val dbMb = temp.dbFile.length() / 1e6
            Log.i(TAG, "benchmark device=${Build.MODEL} api=${Build.VERSION.SDK_INT} chunks=${DOCUMENTS * CHUNKS_PER_DOCUMENT} insert=%.1f s db=%.1f MB".format(Locale.ROOT, insertSeconds, dbMb))
            Log.i(TAG, "benchmark vector k=50 ${summary(vectorMs)}")
            Log.i(TAG, "benchmark text k=50 ${summary(textMs)}")
        }
    }

    /** [WARMUP] discarded runs, then [RUNS] timed ones, in milliseconds. */
    private suspend fun measure(query: suspend (Int) -> Unit): List<Double> {
        repeat(WARMUP) { query(it) }
        return (0 until RUNS).map { run ->
            val started = System.nanoTime()
            query(WARMUP + run)
            (System.nanoTime() - started) / 1e6
        }
    }

    private fun summary(ms: List<Double>) = "p50=%.2f ms p95=%.2f ms mean=%.2f ms n=%d".format(
        Locale.ROOT, Percentiles.of(ms, 50), Percentiles.of(ms, 95), ms.average(), ms.size,
    )

    private fun sentence(random: Random, words: Int) = List(words) { WORDS[random.nextInt(WORDS.size)] }.joinToString(" ")

    private companion object {
        const val TAG = "MicroRAG"
        const val SEED = 2026
        const val DOCUMENTS = 100
        const val CHUNKS_PER_DOCUMENT = 100
        const val BATCH = 64
        const val WORDS_PER_CHUNK = 120
        const val WARMUP = 5
        const val RUNS = 100

        val WORDS = """
            motor bomba válvula filtro presión caudal temperatura sensor tubería junta aceite revisión
            mantenimiento avería técnico manual instrucción seguridad operario guantes gafas protección
            cliente contrato factura pago plazo reembolso garantía devolución pedido envío proveedor
            compra presupuesto informe auditoría expediente documento firma fecha normativa archivo
            empleado vacaciones turno jornada descanso nómina convenio contrato horario permiso
            paciente medicación dosis tratamiento síntoma consulta médico enfermera prospecto
            servidor red firmware versión actualización configuración usuario contraseña cuenta acceso
            biblioteca préstamo libro sala lectura horario socio carné catálogo colección
            índice búsqueda fragmento consulta respuesta modelo vector texto cita fuente página
            sección capítulo anexo tabla figura nota resumen conclusión objetivo alcance riesgo
            energía eléctrica ventilación refrigeración instalación equipo planta línea producción
            calidad control inspección prueba ensayo resultado medida error tolerancia calibración
        """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
}
