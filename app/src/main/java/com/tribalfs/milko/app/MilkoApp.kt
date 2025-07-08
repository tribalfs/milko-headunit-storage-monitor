package com.tribalfs.milko.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.tribalfs.milko.app.util.LogHelper.setupTimber


@HiltAndroidApp
class MilkoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupTimber()
    }

}




