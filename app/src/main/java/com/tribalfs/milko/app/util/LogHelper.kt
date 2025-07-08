package com.tribalfs.milko.app.util

import com.tribalfs.milko.BuildConfig
import timber.log.Timber

object LogHelper {
    fun setupTimber() {
        if (BuildConfig.DEBUG) {
            Timber.Forest.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "Milko_${element.fileName}:${element.lineNumber})#${element.methodName}"
                }
            })
        }
    }

    inline fun debugLog(message: String) {
        if (BuildConfig.DEBUG) { Timber.Forest.d(message) }
    }

}