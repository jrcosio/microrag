package com.lisdatasolutions.microrag.testing.bench

import android.content.Context
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.security.MessageDigest

/**
 * F14 (research R2 and R11, contract B1 and B4, D-090): the debug folders the phone phase fills with `adb push`.
 * `dev-bench` holds the banks; `dev-models` holds the 270M and Gemma 4 E4B, which the benches verify by SHA-256
 * and load from there, never installed as app models nor added to [ModelCatalog] (E4B would become downloadable).
 */
object DevFiles {

    /** A model the benches load from `dev-models`; [label] names it in the reports. */
    data class DevModel(val label: String, val fileName: String, val sha256: String)

    val EG2_270M = ModelCatalog.byId(ModelId.EG2_270M).let { DevModel("EG2_270M", it.fileName, it.sha256) }

    /** The desktop's verified copy (offline/eval/models.py, D-035); not in the app's catalog. */
    val GEMMA4_E4B = DevModel(
        label = "GEMMA4_E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    )

    fun benchDir(context: Context): File = checkNotNull(context.getExternalFilesDir(BENCH)) { "no external files dir" }

    fun modelsDir(context: Context): File = checkNotNull(context.getExternalFilesDir(MODELS)) { "no external files dir" }

    /** The model's file in `dev-models` when its SHA-256 matches; null when missing or different. */
    fun verified(context: Context, model: DevModel): File? =
        File(modelsDir(context), model.fileName).takeIf { it.isFile && sha256(it) == model.sha256 }

    fun sha256(file: File): String {
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

    const val BENCH = "dev-bench"
    const val MODELS = "dev-models"
    private const val BUFFER = 1 shl 20
}
