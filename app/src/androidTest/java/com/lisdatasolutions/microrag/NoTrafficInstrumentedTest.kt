package com.lisdatasolutions.microrag

import android.net.TrafficStats
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.navigation.TopLevelDestination
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.testing.OnboardingStageRule
import com.lisdatasolutions.microrag.testing.ReadyModelsRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * T067 (SC-008, constitution I): with the models ready and nothing to download, walking welcome,
 * setup and the tabs sends and receives no bytes from the app's UID.
 */
class NoTrafficInstrumentedTest {

    private val stageRule = OnboardingStageRule(OnboardingStage.NOT_STARTED)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(stageRule).around(ReadyModelsRule()).around(compose)

    private fun string(id: Int) = compose.activity.getString(id)

    @Test
    fun walkingTheAppOpensNoConnection() {
        val uid = Process.myUid()
        val txBefore = TrafficStats.getUidTxBytes(uid)
        val rxBefore = TrafficStats.getUidRxBytes(uid)

        compose.onNodeWithText(string(R.string.welcome_cta)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.setup_continue)).performClick()
        compose.waitUntil(5_000) { stageRule.store.stage.value == OnboardingStage.COMPLETED }
        TopLevelDestination.entries.forEach { tab ->
            compose.onNodeWithText(string(tab.label)).performClick()
            compose.onNodeWithText(string(tab.label)).assertIsDisplayed()
        }
        compose.waitForIdle()
        Thread.sleep(2_000)

        assertWithMessage("bytes sent").that(TrafficStats.getUidTxBytes(uid) - txBefore).isEqualTo(0L)
        assertWithMessage("bytes received").that(TrafficStats.getUidRxBytes(uid) - rxBefore).isEqualTo(0L)
    }
}
