package com.lisdatasolutions.microrag.feature.settings

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.ModelOverview
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F12 T026 and T033: Ajustes as in PNG 09, with the chips and sheet of S1–S2 and the deletion of S6 (settings-ui.md §1). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-w390dp-h844dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object {
        const val LOCAL_ROW = "Procesamiento 100 % local: activo. Sin enviar documentos a la nube."
    }

    private val calls = mutableListOf<String>()

    private val actions = SettingsActions(
        onBack = { calls += "back" },
        onOpenModel = { calls += "open:$it" },
        onCompleteSetup = { calls += "setup" },
        onLanguages = { calls += "languages" },
        onTheme = { calls += "theme" },
        onIndex = { calls += "index" },
        onClearHistory = { calls += "clear" },
        onConfirmClear = { calls += "confirm" },
        onDismissClear = { calls += "dismiss" },
        onMessageShown = { calls += "shown" },
    )

    private fun overview(id: ModelId, status: ModelSetupStatus): ModelOverview = ModelCatalog.byId(id).let {
        ModelOverview(id, it.displayName, it.fileName, it.sizeBytes, it.sha256, if (id == ModelId.EG2_270M) RagParameters.DIMS else null, status)
    }

    private val gemmaGpu = overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Ready(ComputeBackend.GPU, ModelCatalog.byId(ModelId.GEMMA4_E2B).sizeBytes))
    private val embeddingCpu = overview(ModelId.EG2_270M, ModelSetupStatus.Ready(ComputeBackend.CPU, ModelCatalog.byId(ModelId.EG2_270M).sizeBytes))

    private val multimodalActions = MultimodalActions(
        onToggle = { calls += "toggle:$it" },
        onConfirmEnable = { calls += "enable" },
        onConfirmDisable = { calls += "disable" },
        onDismissDialog = { calls += "dismissDialog" },
        onAction = { calls += "action:$it" },
        onNoticeShown = { calls += "noticeShown" },
    )

    private fun show(state: SettingsUiState, multimodal: MultimodalUiState = MultimodalUiState()) {
        compose.setContent {
            LisdataTheme { SettingsScreen(state = state, actions = actions, multimodal = multimodal, multimodalActions = multimodalActions) }
        }
    }

    private fun showSheet(model: ModelOverview) {
        compose.setContent { LisdataTheme { ModelSheetContent(model = model, onCompleteSetup = { calls += "setup" }) } }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    /** F19 T021 (contract U1): the 440M's row replaces the 270M's once the mode switched to it. */
    @Test
    fun the440MRow_replacesThe270M() {
        val embedding440 = overview(ModelId.EG2_440M, ModelSetupStatus.Ready(ComputeBackend.CPU, ModelCatalog.byId(ModelId.EG2_440M).sizeBytes))
        show(SettingsUiState(models = listOf(gemmaGpu, embedding440)), MultimodalUiState(stage = MultimodalStage.ON))

        compose.onNodeWithText("Modelo de embeddings", useUnmergedTree = true).performClick()

        assertThat(calls).containsExactly("open:EG2_440M")
    }

    /** F19 T021 (wireframe §1, contract U2): off, the switch under the models with its subtitle. */
    @Test
    fun off_theSwitchAsksToTurnItOn() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)))
        scrollTo("Modo multimodal")

        compose.onNode(isToggleable()).assertIsOff().assertIsEnabled()
        compose.onNodeWithText("Lee también páginas e imágenes. Respuestas más lentas.", useUnmergedTree = true).assertExists()
        compose.onNode(isToggleable()).performClick()

        assertThat(calls).containsExactly("toggle:true")
    }

    /** F19 T021 (wireframe §2): the stage is the switch's subtitle, with the real bar and its buttons. */
    @Test
    fun downloading_showsThePercentAndItsButtons_andLocksTheSwitch() {
        show(
            SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)),
            MultimodalUiState(stage = MultimodalStage.DOWNLOADING, percent = 45),
        )
        scrollTo("Cancelar")

        compose.onNode(isToggleable()).assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Descargando el modelo · 45 %", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Pausar").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertThat(calls).containsExactly("action:PAUSE", "action:CANCEL").inOrder()
    }

    @Test
    fun aFailedCheck_saysWhy_withAceptar() {
        show(
            SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)),
            MultimodalUiState(stage = MultimodalStage.OFF, error = MultimodalError.IDENTITY_MISMATCH),
        )
        scrollTo("Aceptar")

        compose.onNodeWithText("Este modelo no es compatible con tu teléfono", useUnmergedTree = true).assertExists()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("Aceptar").performClick()

        assertThat(calls).containsExactly("action:OK")
    }

    /** F19 T021 (contract U3): not destructive, with the four points and the 440M's size. */
    @Test
    fun theEnableDialog_listsWhatChanges() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)), MultimodalUiState(dialog = MultimodalDialog.ENABLE))

        compose.onNodeWithText("¿Activar el modo multimodal?").assertExists()
        for (text in listOf("• Descarga un modelo de", "• Cada página tarda unos 3 s", "• Las respuestas que miran páginas", "• Tus documentos de texto")) {
            compose.onNodeWithText(text, substring = true).assertExists()
        }
        compose.onNodeWithText("Activar").performClick()

        assertThat(calls).containsExactly("enable")
    }

    /** F19 T021 (contract U4): pages and models stay. */
    @Test
    fun theDisableDialog_saysEverythingStays() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)), MultimodalUiState(stage = MultimodalStage.ON, dialog = MultimodalDialog.DISABLE))

        compose.onNodeWithText("¿Desactivar el modo multimodal?").assertExists()
        compose.onNodeWithText("se conservan", substring = true).assertExists()
        compose.onNodeWithText("Desactivar").performClick()

        assertThat(calls).containsExactly("disable")
    }

    /** F19 T021 (contract U7): once, then consumed. */
    @Test
    fun reachingOn_isAnnounced() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)), MultimodalUiState(stage = MultimodalStage.ON, enabledNotice = true))

        compose.onNodeWithText("Modo multimodal activado").assertExists()
        assertThat(calls).containsExactly("noticeShown")
    }

    @Test
    fun structureOfPng09() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)))

        compose.onNodeWithText("Configuración").assertExists()
        for (text in listOf(
            "Ajustes", "Personaliza tu experiencia local.", "INTELIGENCIA ARTIFICIAL",
            // The model names keep their spaces unbreakable (T065).
            "Modelo de respuestas", "Gemma\u00A04", "Modelo de embeddings", "EmbeddingGemma\u00A02", "PREFERENCIAS",
            // F16 A1: «Tema» with the saved theme, between «Idiomas» and «Índice y rendimiento».
            "Idiomas", "Español", "Tema", "Sistema", "Índice y rendimiento", "Almacenamiento y procesamiento", "PRIVACIDAD Y DATOS",
            "Borrar historial de chat", "Eliminar conversaciones locales",
        )) {
            scrollTo(text)
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
        // S5: read as one sentence, so its texts are only in the description.
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(LOCAL_ROW))
        compose.onNode(hasContentDescription(LOCAL_ROW)).assertExists()
    }

    @Test
    fun theOtherRowsAskForTheirDestination() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)))

        for (text in listOf("Idiomas", "Tema", "Índice y rendimiento", "Borrar historial de chat")) {
            scrollTo(text)
            compose.onNodeWithText(text, useUnmergedTree = true).performClick()
        }
        compose.onNode(hasContentDescription("Volver")).performClick()

        assertThat(calls).containsExactly("languages", "theme", "index", "clear", "back").inOrder()
    }

    @Test
    fun theLocalProcessingRowIsInformationOnly() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(LOCAL_ROW))

        val row = compose.onNode(hasContentDescription(LOCAL_ROW))
        row.assertExists()
        assertThat(row.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)).isNull()
    }

    @Test
    fun chipsOfS1() {
        val gemmaCpu = overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Ready(ComputeBackend.CPU, ModelCatalog.byId(ModelId.GEMMA4_E2B).sizeBytes))
        show(SettingsUiState(models = listOf(gemmaCpu, overview(ModelId.EG2_270M, ModelSetupStatus.Preparing))))

        compose.onNodeWithText("Modo CPU, más lento", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Preparando", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Local", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun noChipBeforeTheFirstState() {
        show(SettingsUiState(models = emptyList()))

        compose.onNodeWithText("Modelo de respuestas", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Local", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun eachModelRowIsOneNode_withTheShowDetailsAction() {
        show(SettingsUiState(models = listOf(gemmaGpu, embeddingCpu)))

        val withDetails = SemanticsMatcher("onClickLabel = Ver detalles") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Ver detalles"
        }
        compose.onAllNodes(withDetails).assertCountEquals(2)
        compose.onAllNodes(withDetails and hasText("Modelo de respuestas", substring = true) and hasText("Local", substring = true))
            .assertCountEquals(1)
        compose.onNodeWithText("Modelo de embeddings").performClick()

        assertThat(calls).containsExactly("open:EG2_270M")
    }

    @Test
    fun sheetOfS2_withTheShortHashAndItsDescription() {
        showSheet(embeddingCpu)

        for (text in listOf("EmbeddingGemma 2 270M", "Modelo de embeddings", "Fichero", "embeddinggemma-2-text-270m.litertlm",
            "Tamaño", "SHA-256", "Dimensiones", "256", "Procesador", "CPU", "Estado", "Local")) {
            compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
        val sha = ModelCatalog.byId(ModelId.EG2_270M).sha256
        compose.onNodeWithText(sha.take(12) + "…", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("SHA-256, empieza por ${sha.take(12)}")).assertExists()
        compose.onAllNodesWithText("Completar configuración").assertCountEquals(0)
    }

    @Test
    fun gemmaSheetHasNoDimensions() {
        showSheet(gemmaGpu)

        compose.onNodeWithText("Dimensiones", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("GPU", useUnmergedTree = true).assertExists()
    }

    @Test
    fun anUnverifiedModel_showsItsRealState_andCompletesSetup() {
        val failed = overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Unconfigured())
        showSheet(failed)

        compose.onNodeWithText("Por configurar", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Local", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("—", useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Completar configuración") and hasClickAction()).performClick()

        assertThat(calls).containsExactly("setup")
    }

    @Test
    fun aRowNeverSaysLocal_whileTheModelIsBeingVerified() {
        val bytes = ModelCatalog.byId(ModelId.EG2_270M).sizeBytes
        show(SettingsUiState(models = listOf(gemmaGpu, overview(ModelId.EG2_270M, ModelSetupStatus.Verifying(bytes / 2, bytes)))))

        compose.onAllNodesWithText("Local", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Verificando", useUnmergedTree = true).assertExists()
    }

    // --- US4: «Borrar historial de chat» (S6) -------------------------------------------------------

    private val png = SettingsUiState(models = listOf(gemmaGpu, embeddingCpu))

    @Test
    fun theDialog_withoutAnAnswerInProgress() {
        show(png.copy(confirmClear = ConfirmClear(stopsAnswer = false)))

        compose.onNodeWithText("¿Borrar el historial de chat?").assertExists()
        compose.onNodeWithText(
            "Se eliminarán todas tus conversaciones de este teléfono. Tus documentos y el índice no cambian.", substring = true,
        ).assertExists()
        compose.onNodeWithText("La respuesta en curso se detendrá.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cancelar").performClick()

        assertThat(calls).containsExactly("dismiss")
    }

    @Test
    fun theDialog_warnsThatTheAnswerWillStop_andConfirms() {
        show(png.copy(confirmClear = ConfirmClear(stopsAnswer = true)))

        compose.onNodeWithText("La respuesta en curso se detendrá.", substring = true).assertExists()
        compose.onNodeWithText("Borrar").performClick()

        assertThat(calls).containsExactly("confirm")
    }

    @Test
    fun theNotice_isShownOnce_andAnnounced() {
        show(png.copy(message = SettingsMessage.HISTORY_CLEARED))
        compose.waitForIdle()

        val notice = compose.onNodeWithText("Historial borrado")
        notice.assertExists()
        assertThat(calls).contains("shown")
        // The snackbar host reads its message politely (TalkBack, S6).
        val announced = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNodes()
        assertThat(announced).isNotEmpty()
    }

    @Test
    fun otherNotices() {
        show(png.copy(message = SettingsMessage.NO_CONVERSATIONS))
        compose.onNodeWithText("No hay conversaciones guardadas.").assertExists()
    }

    @Test
    fun aFailureNotice() {
        show(png.copy(message = SettingsMessage.CLEAR_FAILED))
        compose.onNodeWithText("No se pudo borrar el historial.").assertExists()
    }

    /** F16 T018 (A1): the row's subtitle is the saved theme, and the row sits between its neighbours. */
    @Test
    fun a1_theThemeRow_saysTheSavedTheme_belowLanguages() {
        show(png.copy(theme = ThemePreference.DARK))
        scrollTo("Oscuro")
        compose.onNodeWithText("Oscuro", useUnmergedTree = true).assertExists()

        val top = { text: String -> compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top }
        scrollTo("Índice y rendimiento")
        assertThat(top("Idiomas")).isLessThan(top("Tema"))
        assertThat(top("Tema")).isLessThan(top("Índice y rendimiento"))
    }

    @Test
    fun s3_theLanguagesRow_saysTheAppLanguage_inItsOwnName() {
        show(png.copy(appLanguage = AppLanguage.ENGLISH))
        scrollTo("English")

        compose.onNodeWithText("English", useUnmergedTree = true).assertExists()
    }
}
