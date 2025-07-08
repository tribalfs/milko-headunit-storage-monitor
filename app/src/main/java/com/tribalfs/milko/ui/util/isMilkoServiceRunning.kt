package com.tribalfs.milko.ui.util

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import com.tribalfs.milko.app.MilkoService

fun Context.isMilkoServiceRunning(): Boolean {
    val manager = applicationContext.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE).any {
        it.service.className == MilkoService::class.java.name
    }
}