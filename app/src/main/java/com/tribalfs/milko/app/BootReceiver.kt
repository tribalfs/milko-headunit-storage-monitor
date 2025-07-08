package com.tribalfs.milko.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, MilkoService::class.java)
            serviceIntent.putExtra(MilkoService.Companion.KEY_COMMAND, MilkoService.Companion.COMMAND_START)
            context.startService(serviceIntent)
        }
    }
}