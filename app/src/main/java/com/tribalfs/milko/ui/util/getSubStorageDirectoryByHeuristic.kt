package com.tribalfs.milko.ui.util

import android.R.attr.path
import android.os.Environment
import java.io.File
import java.util.concurrent.ThreadLocalRandom.current

fun getRootDriveOfSelectedPath(selectedPath: String): File {
    if (selectedPath.contains(Environment.getExternalStorageDirectory().path)){
        return Environment.getExternalStorageDirectory()
    }

    if (selectedPath.contains("/storage/emulated/")){
        return File("/storage/emulated/${selectedPath.substringAfter("/storage/emulated/").substringBefore("/")}")
    }

    if (selectedPath.contains("/storage/")){
        return File("/storage/${selectedPath.substringAfter("/storage/").substringBefore("/")}")
    }

    //Fallback
    var current = File(selectedPath)
    while (current.parentFile != null) {
        current = current.parentFile!!
    }
    return current
}