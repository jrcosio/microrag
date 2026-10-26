package com.lisdatasolutions.microrag

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/** Platform invariants P2, P4', P5 and P10 on the installed package (F01 and F03 contracts/platform-invariants.md). */
@RunWith(AndroidJUnit4::class)
class ManifestInvariantsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager
    private val packageInfo = packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
    )

    /** P4' (F03 contracts/platform-invariants.md): exactly the closed list, nothing more and nothing less. */
    @Test
    fun p4_requestsExactlyTheClosedPermissionList() {
        val expected = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RUN_USER_INITIATED_JOBS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.WAKE_LOCK",
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertWithMessage("Requested permissions").that(requested).containsExactlyElementsIn(expected)
        assertThat(requested.filter { it.contains("STORAGE") || it.contains("READ_MEDIA") }).isEmpty()
    }

    @Test
    fun p4_downloadComponentsAreNotExportedAndTyped() {
        val flags = PackageManager.GET_SERVICES.toLong() or PackageManager.GET_RECEIVERS.toLong()
        val info = packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags))
        val job = info.services!!.single { it.name.endsWith(".data.download.ModelDownloadJobService") }
        assertThat(job.exported).isFalse()
        assertThat(job.permission).isEqualTo("android.permission.BIND_JOB_SERVICE")
        val foreground = info.services!!.single { it.name == "androidx.work.impl.foreground.SystemForegroundService" }
        assertThat(foreground.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC).isNotEqualTo(0)
        val receiver = info.receivers!!.single { it.name.endsWith(".data.download.DownloadActionReceiver") }
        assertThat(receiver.exported).isFalse()
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
