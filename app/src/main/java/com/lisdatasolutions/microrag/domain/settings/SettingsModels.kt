package com.lisdatasolutions.microrag.domain.settings

import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus

/**
 * Language of the app's texts (D-029): Spanish unless the user picks English. The answers are always
 * in Spanish (D-061), so this is the only language preference.
 */
enum class AppLanguage(val tag: String) {
    SPANISH("es"),
    ENGLISH("en"),
    ;

    companion object {
        /** The language of a stored or system tag; Spanish for anything else (F12 P1). */
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag?.trim()?.lowercase() } ?: SPANISH
    }
}

/** What Idiomas shows; [readFailed] when the stored app language could not be read and Spanish is used (S8). */
data class LanguageSettings(
    val app: AppLanguage,
    val readFailed: Boolean = false,
)

/** Outcome of «Guardar preferencias» (F12 P6). */
sealed interface SaveResult {
    /** [appLanguageChanged]: the screen must be recreated (Android 12) or will be (Android 13+). */
    data class Saved(val appLanguageChanged: Boolean) : SaveResult

    data object Unchanged : SaveResult

    data object Failed : SaveResult
}

/**
 * A model row of Ajustes and its sheet (data-model §2): catalog data plus the Preparation's real state,
 * so the row never says «Local» before the model is verified and prepared (constitution V).
 */
data class ModelOverview(
    val id: ModelId,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    /** The published hash the file was verified with (D-011). */
    val sha256: String,
    /** Embedding dimensions for EG2_270M (RagParameters.DIMS); null for Gemma. */
    val dimensions: Int?,
    val status: ModelSetupStatus,
)
