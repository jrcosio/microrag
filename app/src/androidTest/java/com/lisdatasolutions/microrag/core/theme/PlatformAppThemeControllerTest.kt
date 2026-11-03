package com.lisdatasolutions.microrag.core.theme

import android.app.Application
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F16 T009, the gate of research R1 and R3: the system's per-app night mode over the app's own context,
 * the one Koin injects (M1). The device's mode is set with `cmd uimode` and never read back from
 * `Resources.getSystem()`, which already carries the per-app mode (R12). Everything is restored after.
 */
@RunWith(AndroidJUnit4::class)
class PlatformAppThemeControllerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val controller = PlatformAppThemeController(app, DefaultAppLogger(isEnabled = false))
    private var deviceMode = "no"

    @Before
    fun rememberTheDeviceMode() {
        // «Night mode: yes», «no», «auto» or «custom»; anything else is restored as «no».
        deviceMode = shell("cmd uimode night").substringAfter(":").trim().takeIf { it in DEVICE_MODES } ?: "no"
    }

    @After
    fun restore() {
        controller.apply(ThemePreference.SYSTEM)
        shell("cmd uimode night $deviceMode")
    }

    @Test
    fun lightAndDark_overrideTheDeviceMode() {
        shell("cmd uimode night no")
        assertThat(controller.apply(ThemePreference.DARK)).isTrue()
        awaitNight(expected = true)

        shell("cmd uimode night yes")
        assertThat(controller.apply(ThemePreference.LIGHT)).isTrue()
        awaitNight(expected = false)
    }

    @Test
    fun system_followsTheDeviceBothWays() {
        shell("cmd uimode night no")
        controller.apply(ThemePreference.DARK)
        awaitNight(expected = true)

        assertThat(controller.apply(ThemePreference.SYSTEM)).isTrue()
        awaitNight(expected = false)

        shell("cmd uimode night yes")
        awaitNight(expected = true)
    }

    /** R3, variant A: applying the value already in force must not recreate an open activity. */
    @Test
    fun reapplyingTheSameValue_doesNotRecreateTheActivity() {
        shell("cmd uimode night no")
        controller.apply(ThemePreference.DARK)
        awaitNight(expected = true)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var first: ComponentActivity? = null
            scenario.onActivity { first = it }

            controller.apply(ThemePreference.DARK)
            SystemClock.sleep(SETTLE_MS)

            var now: ComponentActivity? = null
            scenario.onActivity { now = it }
            assertWithMessage("the same activity instance after re-applying DARK").that(now).isSameInstanceAs(first)
        }
    }

    /** Waits until a newly created activity shows [expected] as its night mode, within [TIMEOUT_MS]. */
    private fun awaitNight(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var last: Boolean? = null
        while (SystemClock.uptimeMillis() < deadline) {
            last = activityNight()
            if (last == expected) return
            SystemClock.sleep(POLL_MS)
        }
        assertWithMessage("night mode of a new activity").that(last).isEqualTo(expected)
    }

    private fun activityNight(): Boolean {
        var night = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity {
                night = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
        }
        return night
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private companion object {
        val DEVICE_MODES = setOf("yes", "no", "auto", "custom")
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 250L
        const val SETTLE_MS = 2_000L
    }
}
