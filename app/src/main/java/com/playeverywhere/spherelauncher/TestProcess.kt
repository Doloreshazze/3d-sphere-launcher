package com.playeverywhere.spherelauncher

import android.app.ActivityManager
import android.content.Context
import android.util.Log

fun getRunningApps(context: Context) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val processes = am.runningAppProcesses
    processes?.forEach { 
        Log.d("TestProcess", "Process: ${it.processName}, Importance: ${it.importance}")
    }
}
