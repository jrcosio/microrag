package com.lisdatasolutions.microrag.data.device

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.StatFs
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import com.lisdatasolutions.microrag.domain.onboarding.DeviceResources
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Reads memory, free space and connectivity. Only observes the network; it never opens connections. */
class AndroidDeviceResources(
    context: Context,
    private val dirs: AppDirs,
    private val storageRoot: File,
) : DeviceResources {

    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun totalRamBytes(): Long = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.totalMem

    override fun freeBytes(): Long = StatFs(storageRoot.path).availableBytes

    override fun prepareStorage(): Boolean = dirs.ensureAll()

    override fun currentNetwork(): NetworkKind = kindOf(connectivity.getNetworkCapabilities(connectivity.activeNetwork))

    override val hasInternet: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(kindOf(connectivity.getNetworkCapabilities(connectivity.activeNetwork)) != NetworkKind.NONE)
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun kindOf(capabilities: NetworkCapabilities?): NetworkKind = when {
        capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.NONE
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.MOBILE
        else -> NetworkKind.WIFI
    }
}
