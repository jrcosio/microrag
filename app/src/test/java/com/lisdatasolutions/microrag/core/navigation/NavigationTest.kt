package com.lisdatasolutions.microrag.core.navigation

import android.app.Application
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.onboarding.FakeOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** Navigation contract N1–N8 (contracts/navigation.md, FR-016…FR-022, SC-003, SC-007). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var nav: TestNavHostController

    private fun launchShell() {
        compose.setContent {
            nav = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            LisdataTheme { MicroRagAppShell(navController = nav) }
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
        val secondary = listOf(Ingest, Document(documentId = 1), Index, Languages)
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

    @Test
    fun n6_bottomBarHiddenWhileKeyboardIsVisible() {
        TopLevelDestination.entries.forEach { destination ->
            assertThat(shouldShowBottomBar(destination, imeVisible = false)).isTrue()
            assertThat(shouldShowBottomBar(destination, imeVisible = true)).isFalse()
        }
        assertThat(shouldShowBottomBar(null, imeVisible = false)).isFalse()
        assertThat(shouldShowBottomBar(null, imeVisible = true)).isFalse()
    }

    @Test
    fun n8_invalidArgumentShowsPlaceholderWithoutCrashing() {
        launchShell()
        compose.runOnUiThread { nav.navigate(Document(documentId = -1)) }
        compose.waitForIdle()

        compose.onNodeWithText("documentId=-1", substring = true).assertIsDisplayed()
    }
}

/** N7: destination and tab survive recreation by rotation and by light/dark theme change. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NavigationRestorationTest {

    /** Since F03 the activity asks Koin where to start: Home, with the onboarding completed. */
    @get:Rule(order = 0)
    val completedOnboarding = object : ExternalResource() {
        override fun before() {
            startKoin {
                modules(module { factory { ResolveStartDestinationUseCase(FakeOnboardingStore(OnboardingStage.COMPLETED)) } })
            }
        }

        override fun after() = stopKoin()
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun assertOnLibrary() {
        compose.onNodeWithText(context.getString(R.string.pending_destination_title, "F08")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(TopLevelDestination.LIBRARY.label)).assertIsDisplayed()
    }

    @Test
    fun n7_survivesRotationRecreation() {
        compose.onNodeWithText(context.getString(TopLevelDestination.LIBRARY.label)).performClick()
        RuntimeEnvironment.setQualifiers("+land")
        compose.activityRule.scenario.recreate()
        assertOnLibrary()
    }

    @Test
    fun n7_survivesThemeChangeRecreation() {
        compose.onNodeWithText(context.getString(TopLevelDestination.LIBRARY.label)).performClick()
        RuntimeEnvironment.setQualifiers("+night")
        compose.activityRule.scenario.recreate()
        assertOnLibrary()
    }
}
