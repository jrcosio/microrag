package com.lisdatasolutions.microrag.testing.bench

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T009 (contract B4, lesson of F18): the phone phase runs this before `adb push`, so `dev-bench` and
 * `dev-models` exist without opening the app (its screens may show the user's documents).
 */
@RunWith(AndroidJUnit4::class)
class BenchDirsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theDebugFolders_exist() {
        assertThat(DevFiles.benchDir(context).isDirectory).isTrue()
        assertThat(DevFiles.modelsDir(context).isDirectory).isTrue()
    }
}
