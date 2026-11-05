package com.lisdatasolutions.microrag.core.navigation

import android.app.Application
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ClearChatHistoryUseCase
import com.lisdatasolutions.microrag.domain.chat.CountConversationsUseCase
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.IsAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationsUseCase
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.PauseIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.ReconcileIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.ResumeIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.RetryIndexingUseCase
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.RemoveQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.RetryQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
import com.lisdatasolutions.microrag.domain.library.CreateFolderUseCase
import com.lisdatasolutions.microrag.domain.library.DeleteDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.DeleteFolderUseCase
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DiscardReindexUseCase
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.FolderDocumentRefsUseCase
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LoadPreviewUseCase
import com.lisdatasolutions.microrag.domain.library.MoveDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveLibrarySummaryUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveLibraryUseCase
import com.lisdatasolutions.microrag.domain.library.PageImageRepository
import com.lisdatasolutions.microrag.domain.library.ReindexDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.RemoveFromIndexUseCase
import com.lisdatasolutions.microrag.domain.library.RenameFolderUseCase
import com.lisdatasolutions.microrag.domain.library.SetIncludedUseCase
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.model.StartDestination
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.ObserveImportModeUseCase
import com.lisdatasolutions.microrag.domain.multimodal.SetDefaultImportModeUseCase
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.onboarding.BuildDownloadConfirmationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.CompleteOnboardingUseCase
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.ImportModelFileUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ModelFileSource
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareModelsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareStorageUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ReconcileDownloadsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RetryModelUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.settings.ObserveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.ObserveModelOverviewUseCase
import com.lisdatasolutions.microrag.domain.settings.ObserveThemeSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveThemeSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SettingsNotices
import com.lisdatasolutions.microrag.feature.chat.chatViewModel
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSelectorViewModel
import com.lisdatasolutions.microrag.feature.document.DocumentViewModel
import com.lisdatasolutions.microrag.feature.document.PageImageLoader
import com.lisdatasolutions.microrag.feature.folders.FoldersViewModel
import com.lisdatasolutions.microrag.feature.history.HistoryViewModel
import com.lisdatasolutions.microrag.feature.home.HomeViewModel
import com.lisdatasolutions.microrag.feature.indexing.IndexViewModel
import com.lisdatasolutions.microrag.feature.ingest.IngestViewModel
import com.lisdatasolutions.microrag.feature.library.LibraryViewModel
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupViewModel
import com.lisdatasolutions.microrag.feature.settings.ImportModeViewModel
import com.lisdatasolutions.microrag.feature.settings.LanguagesViewModel
import com.lisdatasolutions.microrag.feature.settings.SettingsViewModel
import com.lisdatasolutions.microrag.feature.settings.ThemeViewModel
import com.lisdatasolutions.microrag.rag.runtime.CompatibilityResult
import com.lisdatasolutions.microrag.testing.ChatTestGraph
import com.lisdatasolutions.microrag.testing.FakeAppLanguageController
import com.lisdatasolutions.microrag.testing.FakeAppThemeController
import com.lisdatasolutions.microrag.testing.FakeAppThemeStore
import com.lisdatasolutions.microrag.testing.FakeChatSession
import com.lisdatasolutions.microrag.testing.FakeImportQueueRepository
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.testing.FakeModelSetup
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import com.lisdatasolutions.microrag.testing.InMemoryChatRepository
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import com.lisdatasolutions.microrag.testing.fakeMultimodalSettingsViewModel
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Library and the detail (F08) take their ViewModels from Koin; a fake repository with documents 1 and 7 is enough to navigate. */
internal fun libraryViewModels(library: FakeLibraryRepository = navigationLibrary()) = module {
    val indexing = object : IndexingRepository {
        override val state = MutableStateFlow<IndexingState>(IndexingState.Idle(emptyList()))
        override suspend fun start() = 0
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun retry(documentId: Long) = Unit
        override suspend fun reconcile() = Unit
        override suspend fun add(documentId: Long) = Unit
    }
    viewModel { params -> LibraryViewModel(get(), ObserveLibraryUseCase(library), SetIncludedUseCase(library), folder = params.getOrNull()) }
    viewModel {
        FoldersViewModel(
            get(),
            ObserveFoldersUseCase(library),
            CreateFolderUseCase(library),
            RenameFolderUseCase(library),
            DeleteFolderUseCase(library, ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)), RecordingLogger()),
        )
    }
    viewModel {
        DocumentViewModel(
            get(), ObserveDocumentUseCase(library), LoadPreviewUseCase(library), SetIncludedUseCase(library),
            DeleteDocumentUseCase(library), RemoveFromIndexUseCase(library), ReindexDocumentUseCase(library),
            DiscardReindexUseCase(library), RetryIndexingUseCase(indexing), ObserveFoldersUseCase(library), MoveDocumentUseCase(library),
        )
    }
}

/** Home (F09) takes its ViewModel from Koin too; the models are ready. */
internal fun homeViewModels() = module {
    viewModel {
        val models = InMemoryModelFileRepository(verified = ModelCatalog.required.map { it.id }.toSet()).apply {
            preparedBackends.value = ModelCatalog.required.associate { it.id to ComputeBackend.GPU }
        }
        val indexing = object : IndexingRepository {
            override val state = MutableStateFlow<IndexingState>(IndexingState.Idle(emptyList()))
            override suspend fun start() = 0
            override suspend fun pause() = Unit
            override suspend fun resume() = Unit
            override suspend fun retry(documentId: Long) = Unit
            override suspend fun reconcile() = Unit
            override suspend fun add(documentId: Long) = Unit
        }
        HomeViewModel(
            ObserveLocalAiUseCase(models), RefreshLocalAiUseCase(models), ObserveLibrarySummaryUseCase(FakeLibraryRepository()),
            ObserveIndexingUseCase(indexing), RecordingLogger(),
        )
    }
}

/** Ajustes (F12) takes its ViewModel from Koin too, over the Preparation's fakes with both models ready. */
internal fun settingsViewModels(
    models: FakeModelSetup = FakeModelSetup().apply {
        ready(ModelId.GEMMA4_E2B, ComputeBackend.GPU)
        ready(ModelId.EG2_270M, ComputeBackend.CPU)
    },
    language: FakeAppLanguageController = FakeAppLanguageController(),
    themeStore: FakeAppThemeStore = FakeAppThemeStore(),
    themeController: FakeAppThemeController = FakeAppThemeController(),
    /** F19: the multimodal mode's copy, shared by Ajustes and «Modo al importar PDF». */
    multimodal: FakeMultimodalStore = FakeMultimodalStore(),
) = module {
    single { SettingsNotices() }
    viewModel {
        val chats = InMemoryChatRepository()
        val session = FakeChatSession(chats)
        SettingsViewModel(
            ObserveModelOverviewUseCase(models.useCase, RefreshLocalAiUseCase(models.repository)),
            RefreshLocalAiUseCase(models.repository),
            ObserveLanguageSettingsUseCase(language),
            ObserveThemeSettingsUseCase(themeStore),
            CountConversationsUseCase(chats),
            IsAnswerInProgressUseCase(session),
            ClearChatHistoryUseCase(session),
            get(),
            Dispatchers.Unconfined,
        )
    }
    viewModel { fakeMultimodalSettingsViewModel(savedState = get(), store = multimodal) }
    viewModel { ImportModeViewModel(ObserveImportModeUseCase(multimodal), SetDefaultImportModeUseCase(multimodal), get()) }
    viewModel { LanguagesViewModel(ObserveLanguageSettingsUseCase(language), SaveLanguageSettingsUseCase(language, get()), get()) }
    viewModel { ThemeViewModel(ObserveThemeSettingsUseCase(themeStore), SaveThemeSettingsUseCase(themeStore, themeController, get()), get()) }
    // «Completar configuración» in a model sheet opens the Preparation (F12 S2), over the same fakes.
    viewModel {
        val scope = ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val prepare = PrepareModelsUseCase(
            models.repository, { _, _ -> CompatibilityResult.Compatible(ComputeBackend.CPU) }, models.work, scope,
        )
        val source = object : ModelFileSource {
            override fun sizeOf(uri: String) = 0L
            override fun open(uri: String) = ByteArrayInputStream(ByteArray(0))
        }
        SetupViewModel(
            observeSetup = models.useCase,
            buildConfirmation = BuildDownloadConfirmationUseCase(models.repository, models.controller, models.resources),
            startDownload = StartModelDownloadUseCase(models.repository, models.controller, models.resources, models.store, models.work),
            pauseDownload = PauseModelDownloadUseCase(models.store, models.controller),
            resumeDownload = ResumeModelDownloadUseCase(models.repository, models.store, models.controller),
            retryModel = RetryModelUseCase(models.repository, models.controller, models.store, models.work),
            reconcile = ReconcileDownloadsUseCase(models.repository, models.controller, models.store, models.resources, RecordingLogger()),
            prepareModels = prepare,
            importModel = ImportModelFileUseCase(models.repository, source, models.work, prepare, scope),
            prepareStorage = PrepareStorageUseCase(models.repository, models.controller, models.resources),
            completeOnboarding = CompleteOnboardingUseCase(models.repository, models.store),
            backgroundDispatcher = Dispatchers.Unconfined,
        )
    }
}

/** The chat (F11) and Historial (F13) take their ViewModels from Koin too; [graph] holds their fakes. */
internal fun chatViewModels(graph: ChatTestGraph = ChatTestGraph()) = module {
    viewModel { graph.chatViewModel(get()) }
    // F20: the chat's page thumbnails; no page has an image here.
    single { PageImageLoader(object : PageImageRepository { override suspend fun image(documentId: Long, page: Int): ByteArray? = null }) }
    // F17 K8: the «Buscar en» sheet of the chat.
    viewModel {
        ScopeSelectorViewModel(
            get(), ObserveFoldersUseCase(graph.library), ObserveLibraryUseCase(graph.library), FolderDocumentRefsUseCase(graph.library),
            Dispatchers.Unconfined,
        )
    }
    viewModel {
        HistoryViewModel(
            savedState = get(),
            observeConversations = ObserveConversationsUseCase(graph.chats),
            deleteConversation = DeleteConversationUseCase(graph.session),
            observeAnswerInProgress = ObserveAnswerInProgressUseCase(graph.session),
            backgroundDispatcher = Dispatchers.Unconfined,
        )
    }
}

internal fun navigationLibrary() = FakeLibraryRepository().apply {
    documents.value = listOf(LibraryDocument(7, "Manual.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(3, null), true))
    // F17: an empty folder, so its screen opens (a folder that does not exist goes back by itself).
    folderRows.value = listOf(Folder(1, "Legal", createdAtMs = 10, documentCount = 0))
    listOf(1L, 7L).forEach { id ->
        details[id] = MutableStateFlow(
            DetailResult.Loaded(
                DocumentDetail(
                    id = id, displayName = "Manual.pdf", format = DocumentFormat.PDF, state = DocumentDetailState.Indexed,
                    included = true, pages = 3, chunks = 3, sizeBytes = 1_000, importedAtMs = 0, canReindex = true,
                    copyMissing = false, reindex = null,
                ),
            ),
        )
    }
}

/** Navigation contract N1–N8 (contracts/navigation.md, FR-016…FR-022, SC-003, SC-007). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NavigationTest {

    /** The import (F06), indexing (F07) and library (F08) screens take their ViewModels from Koin; fakes are enough here. */
    private val library = navigationLibrary()
    private val models = FakeModelSetup().apply {
        ready(ModelId.GEMMA4_E2B, ComputeBackend.GPU)
        ready(ModelId.EG2_270M, ComputeBackend.CPU)
    }
    private val chat = ChatTestGraph()
    private val multimodal = FakeMultimodalStore()

    @get:Rule(order = 0)
    val ingestGraph = object : ExternalResource() {
        override fun before() {
            val repository = FakeImportQueueRepository()
            startKoin {
                modules(
                    libraryViewModels(library),
                    homeViewModels(),
                    settingsViewModels(models, multimodal = multimodal),
                    chatViewModels(chat),
                    module {
                        viewModel {
                            IngestViewModel(
                                ObserveImportQueueUseCase(repository),
                                ImportDocumentsUseCase(repository),
                                StartIndexingUseCase(repository),
                                RetryQueueItemUseCase(repository),
                                RemoveQueueItemUseCase(repository),
                                ObserveFoldersUseCase(library),
                                get(),
                            )
                        }
                        viewModel {
                            val indexing = object : IndexingRepository {
                                override val state = MutableStateFlow<IndexingState>(IndexingState.Idle(emptyList()))
                                override suspend fun start() = 0
                                override suspend fun pause() = Unit
                                override suspend fun resume() = Unit
                                override suspend fun retry(documentId: Long) = Unit
                                override suspend fun reconcile() = Unit
                                override suspend fun add(documentId: Long) = Unit
                            }
                            IndexViewModel(
                                ObserveIndexingUseCase(indexing),
                                PauseIndexingUseCase(indexing),
                                ResumeIndexingUseCase(indexing),
                                RetryIndexingUseCase(indexing),
                            )
                        }
                    },
                )
            }
        }

        override fun after() = stopKoin()
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var nav: TestNavHostController

    private fun launchShell(openIndexing: Int = 0) {
        compose.setContent {
            nav = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            LisdataTheme { MicroRagAppShell(navController = nav, openIndexing = openIndexing) }
        }
    }

    private fun tab(destination: TopLevelDestination) = compose.onNodeWithText(context.getString(destination.label))

    private fun routesInBackStack() = nav.currentBackStack.value
        .map { it.destination }
        .filter { it.route != null && it.parent != null }

    @Test
    fun startsAtHome_withBottomBar() {
        launchShell()
        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
        tab(TopLevelDestination.HOME).assertIsDisplayed()
    }

    @Test
    fun n1_eachTabAppearsOnceInTheBackStack() {
        launchShell()
        listOf(
            TopLevelDestination.CHAT, TopLevelDestination.LIBRARY, TopLevelDestination.SETTINGS,
            TopLevelDestination.CHAT, TopLevelDestination.LIBRARY,
        ).forEach { tab(it).performClick() }

        val stack = routesInBackStack()
        assertThat(stack).hasSize(2)
        assertThat(stack.first().hasRoute(Home::class)).isTrue()
        assertThat(stack.last().hasRoute(Library::class)).isTrue()
    }

    /**
     * N2/N3 at stack level: Home always sits below a tab root, so popping (what the system Back
     * does through NavHost) lands on Home, and Home has nothing below it. The real Back key is
     * covered by SystemBackTest (androidTest): Robolectric does not dispatch NavigationEvent back.
     */
    @Test
    fun n2_backFromTabReturnsHome_n3_homeHasNothingToPop() {
        launchShell()
        tab(TopLevelDestination.SETTINGS).performClick()
        assertThat(nav.previousBackStackEntry?.destination?.hasRoute(Home::class)).isTrue()

        compose.runOnUiThread { nav.popBackStack() }
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
        assertThat(nav.previousBackStackEntry).isNull()
    }

    @Test
    fun n4_reselectingTabReturnsToItsRoot() {
        launchShell()
        tab(TopLevelDestination.LIBRARY).performClick()
        compose.runOnUiThread { nav.navigate(Document(documentId = 5)) }
        compose.waitForIdle()

        compose.runOnUiThread { nav.navigateToTopLevel(TopLevelDestination.LIBRARY) }
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Library::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(2)
    }

    @Test
    fun n5_topBarBackArrowOnTabReturnsHome() {
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()

        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
    }

    @Test
    fun n6_bottomBarOnlyOnTabRoots() {
        launchShell()
        // Welcome and Setup need the Koin graph since F03: OnboardingNavigationTest (androidTest) covers them.
        val secondary = listOf(Ingest(), Document(documentId = 1), Index, Languages, Theme, LibraryFolder(), LibraryFolder(folderId = 1))
        secondary.forEach { route ->
            compose.runOnUiThread { nav.navigate(route) }
            compose.waitForIdle()
            tab(TopLevelDestination.HOME).assertDoesNotExist()
            compose.runOnUiThread { nav.popBackStack() }
            compose.waitForIdle()
        }
        TopLevelDestination.entries.forEach { destination ->
            tab(destination).performClick()
            tab(TopLevelDestination.HOME).assertIsDisplayed()
        }
    }

    // F09 T010 (research R4): Home's own exits, which replace the provisional entries of F06 and F07.

    private fun homeControl(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun backToASingleHome() {
        compose.runOnUiThread { nav.popBackStack() }
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(1)
    }

    @Test
    fun homeQuickActions_openTheirScreens_andBackReturnsHome() {
        launchShell()

        homeControl(context.getString(R.string.home_import))
        assertThat(nav.currentDestination?.hasRoute(Ingest::class)).isTrue()
        compose.onNodeWithText(context.getString(R.string.ingest_headline)).assertIsDisplayed()
        backToASingleHome()

        homeControl(context.getString(R.string.home_index))
        assertThat(nav.currentDestination?.hasRoute(Index::class)).isTrue()
        compose.onNodeWithText(context.getString(R.string.index_idle_title)).assertIsDisplayed()
        backToASingleHome()

        homeControl(context.getString(R.string.home_languages))
        assertThat(nav.currentDestination?.hasRoute(Languages::class)).isTrue()
        backToASingleHome()
    }

    @Test
    fun documentsAndTheSettingsIcon_openTheirTabs() {
        launchShell()

        homeControl(context.getString(R.string.home_documents))
        assertThat(nav.currentTopLevel()).isEqualTo(TopLevelDestination.LIBRARY)
        assertThat(routesInBackStack()).hasSize(2)
        backToASingleHome()

        // Home kept its scroll: the header is above the fold again.
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(context.getString(R.string.home_settings)))
        compose.onNodeWithContentDescription(context.getString(R.string.home_settings)).performClick()
        compose.waitForIdle()
        assertThat(nav.currentTopLevel()).isEqualTo(TopLevelDestination.SETTINGS)
        assertThat(routesInBackStack()).hasSize(2)
    }

    @Test
    fun newChat_opensTheChatTabOnANewConversation_notTheLastOne() {
        launchShell()
        // A conversation left open in the Chat tab, which the tab would bring back.
        compose.runOnUiThread {
            nav.navigate(Chat(conversationId = 7)) {
                popUpTo<Home> { saveState = true }
                launchSingleTop = true
            }
        }
        compose.waitForIdle()
        tab(TopLevelDestination.HOME).performClick()
        compose.waitForIdle()

        homeControl(context.getString(R.string.home_new_chat))

        val entry = nav.currentBackStackEntry!!
        assertThat(entry.destination.hasRoute(Chat::class)).isTrue()
        assertThat(entry.toRoute<Chat>()).isEqualTo(Chat(newConversation = true))
        assertThat(nav.currentTopLevel()).isEqualTo(TopLevelDestination.CHAT)
        backToASingleHome()
    }

    @Test
    fun aSource_opensTheCitedFragment_andBackReturnsToTheChat() {
        // Document 1 is in the Library's fake too, so the detail opens; the chat cites its chunk 3.
        runBlocking {
            val documentId = chat.addDocument("Manual.pdf")
            val id = chat.chats.createConversation(0)
            // Asked after the document was imported (F13 Q15).
            val turn = chat.chats.insertTurn(id, "¿Qué plazo hay?", partialCoverage = false, now = 5_000)
            chat.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Son 30 días [1].", promptBlocks = 1))
            chat.chats.replaceSources(turn.id, listOf(AnswerSource(1, 1, documentId, 3, 2, 2, "Manual.pdf", 2)))
        }
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()

        // The row reads as one sentence for TalkBack: «Fuente 1: Manual.pdf, …».
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Manual.pdf", substring = true))
        compose.onNodeWithContentDescription("Manual.pdf", substring = true).performClick()
        compose.waitForIdle()

        assertThat(nav.currentBackStackEntry!!.toRoute<Document>()).isEqualTo(Document(documentId = 1, chunkId = 3))
        assertThat(library.previewRequests).contains(1L to 3L)
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Chat::class)).isTrue()
        // SC-006: the list is where it was left, on the source.
        compose.onNodeWithContentDescription("Manual.pdf", substring = true).assertIsDisplayed()
    }

    @Test
    fun theChatTab_opensTheLastConversation() {
        runBlocking {
            chat.addDocument("Manual.pdf")
            listOf(0L to "¿La primera?", 10L to "¿La última?").forEach { (at, question) ->
                val id = chat.chats.createConversation(at)
                val turn = chat.chats.insertTurn(id, question, partialCoverage = false, now = at)
                chat.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Sí."))
            }
        }
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()

        // The conversation opens at its end: its question is up the list.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("¿La última?", substring = true))
        compose.onNodeWithContentDescription("¿La primera?", substring = true).assertDoesNotExist()
    }

    /** FR-011: the answer lives in the session, so leaving the tab and coming back finds it still going. */
    @Test
    fun anAnswerInProgress_goesOn_acrossTabs() {
        runBlocking {
            chat.addDocument("Manual.pdf")
            val id = chat.chats.createConversation(0)
            val turn = chat.chats.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 0)
            chat.session.active.value = ActiveAnswer(id, turn.id, "¿Plazo?", TurnState.GENERATING, text = "El plazo es")
        }
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.onNodeWithText(context.getString(R.string.chat_generating)).assertExists()

        tab(TopLevelDestination.LIBRARY).performClick()
        compose.runOnUiThread { chat.session.active.value = chat.session.active.value!!.copy(text = "El plazo es de 30 días") }
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.chat_generating)).assertExists()
        compose.onNodeWithText("El plazo es de 30 días", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun aDoubleTapOnHome_opensASingleScreen_FR006() {
        launchShell()
        val languages = context.getString(R.string.home_languages)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(languages))

        compose.onNodeWithText(languages).performTouchInput {
            click()
            advanceEventTime(100)
            click()
        }
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Languages::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(2)
    }

    @Test
    fun finishingSetupOpenedFromHome_leavesASingleHome() {
        launchShell()
        // Setup needs its Koin graph (OnboardingNavigationTest covers it); what matters is the stack below.
        compose.runOnUiThread { nav.navigate(Index) }
        compose.waitForIdle()

        compose.runOnUiThread { nav.leaveSetup() }
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(1)
    }

    // F07 T040 (research R3): the indexing notification opens Index over Home, only after onboarding.

    @Test
    fun theIndexingNotificationOpensIndexOverHome() {
        launchShell(openIndexing = 1)
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Index::class)).isTrue()

        compose.runOnUiThread { nav.popBackStack() }
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
    }

    @Test
    fun theIndexingNotificationIsIgnoredBeforeOnboardingEnds() {
        assertThat(MainActivity.indexingRequestsFor(StartDestination.HOME, 2)).isEqualTo(2)
        assertThat(MainActivity.indexingRequestsFor(StartDestination.WELCOME, 2)).isEqualTo(0)
        assertThat(MainActivity.indexingRequestsFor(StartDestination.SETUP_OVER_WELCOME, 2)).isEqualTo(0)
    }

    // F08 T021: the Library's own exits.

    @Test
    fun libraryPlusOpensTheImportScreen_andARowTheDocument() {
        launchShell()
        tab(TopLevelDestination.LIBRARY).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.library_headline)).assertIsDisplayed()

        compose.onNodeWithContentDescription(context.getString(R.string.library_import)).performClick()
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Ingest::class)).isTrue()

        compose.runOnUiThread { nav.popBackStack() }
        compose.waitForIdle()
        // F17 K1: the root lists the folders; Manual.pdf is in «General».
        openGeneral()
        compose.waitForIdle()
        compose.onNodeWithText("Manual.pdf", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertThat(nav.currentBackStackEntry?.let { it.destination.hasRoute(Document::class) }).isTrue()
        assertThat(nav.previousBackStackEntry?.destination?.hasRoute(LibraryFolder::class)).isTrue()
    }

    /** F17 K1: «General» is under the folders, below the fold of a small screen. */
    private fun openGeneral() {
        val general = context.getString(R.string.library_folder_general)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(general))
        compose.onNodeWithText(general, useUnmergedTree = true).performClick()
    }

    /** F17 T017: Biblioteca → carpeta → detalle → Atrás → carpeta → Atrás → raíz, with no bottom bar inside a folder. */
    @Test
    fun theDetail_opensFromAFolderRow_andBackReturnsThroughTheFolder() {
        launchShell()
        tab(TopLevelDestination.LIBRARY).performClick()
        openGeneral()
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(LibraryFolder::class)).isTrue()
        assertThat(nav.currentBackStackEntry?.toRoute<LibraryFolder>()?.folderId).isNull()
        tab(TopLevelDestination.HOME).assertDoesNotExist()

        compose.onNodeWithText("Manual.pdf", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.document_title)).assertIsDisplayed()

        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(LibraryFolder::class)).isTrue()

        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Library::class)).isTrue()
        // The root keeps its scroll: still on «General».
        compose.onNodeWithText(context.getString(R.string.library_folder_general), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aDocumentThatNoLongerExists_returnsToTheLibraryWithANotice_B8() {
        launchShell()
        tab(TopLevelDestination.LIBRARY).performClick()
        // The snackbar lasts 4 s of frame time: the clock moves by hand so it is seen before it goes.
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav.navigate(Document(documentId = 99)) }
        val notice = context.getString(R.string.library_document_gone)

        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(100)
            compose.onAllNodesWithText(notice).fetchSemanticsNodes().isNotEmpty()
        }
        // Consuming the message must not cancel the snackbar that shows it (seen on the phone).
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText(notice).assertIsDisplayed()
        assertThat(nav.currentDestination?.hasRoute(Library::class)).isTrue()
    }

    @Test
    fun aCitationRoute_asksForTheCitedChunk() {
        launchShell()
        compose.runOnUiThread { nav.navigate(Document(documentId = 7, chunkId = 3)) }
        compose.waitForIdle()

        assertThat(library.previewRequests).contains(7L to 3L)
    }

    @Test
    fun theIndexingLink_opensTheIndexScreen() {
        library.details[7]!!.value = DetailResult.Loaded(
            (library.details[7]!!.value as DetailResult.Loaded).detail.copy(state = DocumentDetailState.Indexing(1, 3), chunks = null),
        )
        launchShell()
        compose.runOnUiThread { nav.navigate(Document(documentId = 7)) }
        compose.waitForIdle()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.document_view_indexing)))
        compose.onNodeWithText(context.getString(R.string.document_view_indexing)).performClick()
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Index::class)).isTrue()
    }

    @Test
    fun libraryBackArrowReturnsHome() {
        launchShell()
        tab(TopLevelDestination.LIBRARY).performClick()

        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
    }

    @Test
    fun n6_bottomBarHiddenWhileKeyboardIsVisible() {
        TopLevelDestination.entries.forEach { destination ->
            assertThat(shouldShowBottomBar(destination, imeVisible = false)).isTrue()
            assertThat(shouldShowBottomBar(destination, imeVisible = true)).isFalse()
        }
        assertThat(shouldShowBottomBar(null, imeVisible = false)).isFalse()
        assertThat(shouldShowBottomBar(null, imeVisible = true)).isFalse()
    }

    /** N8, since F08: an id that does not exist goes back where it came from, without crashing (research R8). */
    @Test
    fun n8_invalidArgumentGoesBackWithoutCrashing() {
        launchShell()
        compose.runOnUiThread { nav.navigate(Document(documentId = -1)) }
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
    }

    // --- F12 T057: moving from Ajustes (contracts/settings-ui.md §5) ---------------------------------

    private fun openSettings() {
        tab(TopLevelDestination.SETTINGS).performClick()
        compose.waitForIdle()
        assertThat(nav.currentTopLevel()).isEqualTo(TopLevelDestination.SETTINGS)
    }

    private fun settingsControl(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun backArrow() {
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()
    }

    private fun entriesOf(route: kotlin.reflect.KClass<*>) = nav.currentBackStack.value.count { it.destination.hasRoute(route) }

    @Test
    fun languagesFromSettings_backOrSave_returnToSettings() {
        launchShell()
        openSettings()

        settingsControl(context.getString(R.string.settings_languages))
        assertThat(nav.currentDestination?.hasRoute(Languages::class)).isTrue()
        backArrow()
        assertThat(nav.currentDestination?.hasRoute(Settings::class)).isTrue()

        settingsControl(context.getString(R.string.settings_languages))
        settingsControl(context.getString(R.string.languages_save))
        assertThat(nav.currentDestination?.hasRoute(Settings::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(2)
    }

    /** F16 T019 (U3, U4): Tema sits over Ajustes; Back and a save both return there. */
    @Test
    fun themeFromSettings_backOrSave_returnToSettings() {
        launchShell()
        openSettings()

        settingsControl(context.getString(R.string.settings_theme))
        assertThat(nav.currentDestination?.hasRoute(Theme::class)).isTrue()
        backArrow()
        assertThat(nav.currentDestination?.hasRoute(Settings::class)).isTrue()

        settingsControl(context.getString(R.string.settings_theme))
        settingsControl(context.getString(R.string.languages_save))
        assertThat(nav.currentDestination?.hasRoute(Settings::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(2)
    }

    /** F19 T047 (contract U5–U6): with the mode on, «Modo al importar PDF» opens, and Back or a save return to Ajustes. */
    @Test
    fun importModeFromSettings_backOrSave_returnToSettings() {
        multimodal.state.value = StoredMultimodal(MultimodalStage.ON)
        launchShell()
        openSettings()

        settingsControl(context.getString(R.string.import_mode_title))
        assertThat(nav.currentDestination?.hasRoute(ImportMode::class)).isTrue()
        backArrow()
        assertThat(nav.currentDestination?.hasRoute(Settings::class)).isTrue()

        settingsControl(context.getString(R.string.import_mode_title))
        settingsControl(context.getString(R.string.import_mode_save))
        assertThat(nav.currentDestination?.hasRoute(Settings::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(2)
    }

    @Test
    fun indexFromSettings_backReturnsToSettings() {
        launchShell()
        openSettings()

        settingsControl(context.getString(R.string.settings_index))
        assertThat(nav.currentDestination?.hasRoute(Index::class)).isTrue()
        compose.runOnUiThread { nav.popBackStack() }
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Settings::class)).isTrue()
    }

    @Test
    fun backFromSettings_returnsToASingleHome() {
        launchShell()
        openSettings()

        backArrow()

        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
        assertThat(routesInBackStack()).hasSize(1)
    }

    @Test
    fun aDoubleTapOnLanguages_opensASingleScreen() {
        launchShell()
        openSettings()
        val row = context.getString(R.string.settings_languages)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(row))

        // Both taps land before the screen changes.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(row).performClick()
        compose.onNodeWithText(row).performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Languages::class)).isTrue()
        assertThat(entriesOf(Languages::class)).isEqualTo(1)
    }

    @Test
    fun aModelSheet_opensThePreparation() {
        models.missing(ModelId.GEMMA4_E2B)
        launchShell()
        openSettings()

        settingsControl(context.getString(R.string.settings_model_answers))
        compose.onNode(hasText(context.getString(R.string.chat_models_action)) and hasClickAction()).performClick()
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Setup::class)).isTrue()
    }
    // --- F13 (research R4; contracts/history-ui.md §5, U8) ------------------------------------------

    private suspend fun savedConversation(question: String, at: Long): Long {
        val id = chat.chats.createConversation(at)
        val turn = chat.chats.insertTurn(id, question, partialCoverage = false, now = at)
        chat.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Sí."))
        return id
    }

    private fun openHistory() {
        compose.onNodeWithContentDescription(context.getString(R.string.action_more_options)).performClick()
        compose.onNodeWithText(context.getString(R.string.history_title)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun theChatMenu_opensHistory_withoutTheBottomBar_andBackReturnsToTheSameChat() {
        val id = runBlocking {
            chat.addDocument("Manual.pdf")
            savedConversation("¿La primera?", at = 5_000)
        }
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()

        openHistory()

        assertThat(nav.currentBackStackEntry!!.toRoute<History>()).isEqualTo(History(openConversationId = id))
        tab(TopLevelDestination.CHAT).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.history_delete_label, "¿La primera?")).assertIsDisplayed()

        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Chat::class)).isTrue()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("¿La primera?", substring = true))
    }

    /**
     * The system Back key from Historial. Here and not in SystemBackTest, which runs on the reference phone
     * against the real conversations: Historial would list the user's own (research R12).
     */
    @Test
    fun systemBack_fromHistory_returnsToTheChat() {
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()
        openHistory()

        Espresso.pressBack()
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Chat::class)).isTrue()
        assertThat(routesInBackStack().none { it.hasRoute(History::class) }).isTrue()
    }

    /** Research R4: the row asked for comes back to the chat, which shows it (Q11). */
    private fun openFromHistory(question: String) {
        openHistory()
        compose.onNodeWithContentDescription(question, substring = true).performClick()
        compose.waitForIdle()
    }

    private fun twoConversations(): Pair<Long, Long> = runBlocking {
        chat.addDocument("Manual.pdf")
        savedConversation("¿La antigua?", at = 5_000) to savedConversation("¿La reciente?", at = 6_000)
    }

    @Test
    fun aRowOfHistory_opensThatConversationInTheChat() {
        val (older, _) = twoConversations()
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()

        openFromHistory("¿La antigua? ")

        assertThat(nav.currentDestination?.hasRoute(Chat::class)).isTrue()
        assertThat(chat.session.calls).contains("open:$older")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("¿La antigua?", substring = true))
        compose.onNodeWithContentDescription("¿La reciente?", substring = true).assertDoesNotExist()
    }

    /** U8: once Historial is leaving, a second tap does nothing, so the chat is not popped too. */
    @Test
    fun aDoubleTapOnARow_opensItOnce() {
        twoConversations()
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()
        openHistory()

        compose.onNodeWithContentDescription("¿La antigua? ", substring = true).performTouchInput {
            click()
            advanceEventTime(100)
            click()
        }
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Chat::class)).isTrue()
        assertThat(routesInBackStack().count { it.hasRoute(Chat::class) }).isEqualTo(1)
    }

    /** FR-014 and FR-003: the conversation stays across tabs, and Back from the chat still goes Home. */
    @Test
    fun theConversationOpenedFromHistory_staysAcrossTabs_andBackGoesHome() {
        twoConversations()
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()
        openFromHistory("¿La antigua? ")

        tab(TopLevelDestination.LIBRARY).performClick()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("¿La antigua?", substring = true))
        compose.onNodeWithContentDescription("¿La reciente?", substring = true).assertDoesNotExist()

        Espresso.pressBack()
        compose.waitForIdle()
        assertThat(nav.currentDestination?.hasRoute(Home::class)).isTrue()
    }

    /** FR-021: «＋» of Historial leaves an empty chat, stopping an answer as «Nuevo chat» does (D-054). */
    @Test
    fun thePlusOfHistory_leavesAnEmptyChat() {
        twoConversations()
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()
        openHistory()

        compose.onNodeWithContentDescription(context.getString(R.string.chat_new)).performClick()
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Chat::class)).isTrue()
        assertThat(chat.session.calls).contains("new")
        compose.onNodeWithContentDescription("¿La reciente?", substring = true).assertDoesNotExist()
    }

    @Test
    fun theNewChatOfAnEmptyHistory_leavesAnEmptyChat() {
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()
        openHistory()

        compose.onNodeWithText(context.getString(R.string.chat_new)).performClick()
        compose.waitForIdle()

        assertThat(nav.currentDestination?.hasRoute(Chat::class)).isTrue()
        assertThat(chat.session.calls).contains("new")
    }

    @Test
    fun anEmptyChat_alsoOffersHistory() {
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()

        openHistory()

        assertThat(nav.currentBackStackEntry!!.toRoute<History>()).isEqualTo(History(openConversationId = null))
        compose.onNodeWithText(context.getString(R.string.history_empty_title)).assertIsDisplayed()
    }

    @Test
    fun aDoubleTapOnHistory_opensASingleHistory() {
        launchShell()
        tab(TopLevelDestination.CHAT).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.action_more_options)).performClick()

        compose.onNodeWithText(context.getString(R.string.history_title)).performTouchInput {
            click()
            advanceEventTime(100)
            click()
        }
        compose.waitForIdle()

        assertThat(routesInBackStack().count { it.hasRoute(History::class) }).isEqualTo(1)
    }

}

/** N7: destination and tab survive recreation by rotation and by light/dark theme change. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NavigationRestorationTest {

    private val chat = ChatTestGraph()

    /** Since F03 the activity asks Koin where to start: Home, with the onboarding completed; since F07 it reconciles the run. */
    @get:Rule(order = 0)
    val completedOnboarding = object : ExternalResource() {
        override fun before() {
            val indexing = object : IndexingRepository {
                override val state = MutableStateFlow<IndexingState>(IndexingState.Idle(emptyList()))
                override suspend fun start() = 0
                override suspend fun pause() = Unit
                override suspend fun resume() = Unit
                override suspend fun retry(documentId: Long) = Unit
                override suspend fun reconcile() = Unit
                override suspend fun add(documentId: Long) = Unit
            }
            startKoin {
                modules(
                    libraryViewModels(),
                    homeViewModels(),
                    settingsViewModels(),
                    chatViewModels(chat),
                    module {
                        factory { ResolveStartDestinationUseCase(FakeOnboardingStore(OnboardingStage.COMPLETED)) }
                        factory { ReconcileIndexingUseCase(indexing) }
                        single { ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) }
                    },
                )
            }
        }

        override fun after() = stopKoin()
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // The activity shows the app language (Spanish by default, D-029), not the test's default locale.
    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun assertOnLibrary() {
        compose.onNodeWithText(text(R.string.library_headline)).assertIsDisplayed()
        // The top bar title and the selected tab.
        compose.onAllNodesWithText(text(TopLevelDestination.LIBRARY.label)).assertCountEquals(2)
    }

    @Test
    fun n7_survivesRotationRecreation() {
        compose.onNodeWithText(text(TopLevelDestination.LIBRARY.label)).performClick()
        RuntimeEnvironment.setQualifiers("+land")
        compose.activityRule.scenario.recreate()
        assertOnLibrary()
    }

    @Test
    fun n7_survivesThemeChangeRecreation() {
        compose.onNodeWithText(text(TopLevelDestination.LIBRARY.label)).performClick()
        RuntimeEnvironment.setQualifiers("+night")
        compose.activityRule.scenario.recreate()
        assertOnLibrary()
    }


    /** F13 research R4, U8: Historial's request survives a recreation and is done once. */
    @Test
    fun aHistoryRequest_survivesARecreation_andIsDoneOnce() {
        runBlocking {
            chat.addDocument("Manual.pdf")
            listOf(5_000L to "¿La antigua?", 6_000L to "¿La reciente?").forEach { (at, question) ->
                val id = chat.chats.createConversation(at)
                val turn = chat.chats.insertTurn(id, question, partialCoverage = false, now = at)
                chat.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Sí."))
            }
        }
        compose.onNodeWithText(text(TopLevelDestination.CHAT.label)).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.history_title)).performClick()
        compose.waitForIdle()

        RuntimeEnvironment.setQualifiers("+land")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("¿La antigua? ", substring = true).performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("¿La antigua?", substring = true))

        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.chat_new)).performClick()
        compose.waitForIdle()
        RuntimeEnvironment.setQualifiers("+port")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("¿La antigua?", substring = true).assertDoesNotExist()
    }

}
