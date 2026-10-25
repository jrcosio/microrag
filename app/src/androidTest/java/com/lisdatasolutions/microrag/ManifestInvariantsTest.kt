package com.lisdatasolutions.microrag

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/** Platform invariants P2, P4, P5 and P10 on the installed package (contracts/platform-invariants.md). */
@RunWith(AndroidJUnit4::class)
class ManifestInvariantsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager
    private val packageInfo = packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
    )

    @Test
    fun p4_requestsNoNetworkNormalOrDangerousPermissions() {
        val allowed = setOf("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertWithMessage("Unexpected permissions: ${requested - allowed}").that(requested - allowed).isEmpty()
    }

    @Test
    fun p2_backupIsDisabled() {
        val flags = packageInfo.applicationInfo!!.flags
        assertThat(flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }

    @Test
    fun p5_noActivityHandlesExternalLinks() {
        val uris = listOf("https://example.com/document/1", "http://example.com", "microrag://document/1", "content://x/y", "file:///sdcard/x")
        uris.forEach { uri ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(context.packageName)
            val handlers = packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            assertWithMessage("Activities handling $uri").that(handlers).isEmpty()
        }
    }

    @Test
    fun p10_appLabelIsMicroRag() {
        val label = packageManager.getApplicationLabel(packageInfo.applicationInfo!!).toString()
        assertThat(label).isEqualTo("MicroRAG")
    }
}
