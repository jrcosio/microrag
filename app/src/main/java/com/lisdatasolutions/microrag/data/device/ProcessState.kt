package com.lisdatasolutions.microrag.data.device

import android.app.ActivityManager

/** Whether Android keeps this process in the foreground: visible, or running a foreground service. */
fun interface ProcessState {
    fun isForeground(): Boolean
}

/**
 * From the process importance, which only a foreground service that really started raises (F07
 * research R1). WorkManager's setForeground can return while its service failed to reach the foreground.
 */
class ActivityManagerProcessState : ProcessState {
    override fun isForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
    }
}
