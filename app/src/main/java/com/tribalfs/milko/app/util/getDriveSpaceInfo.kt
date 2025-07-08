package com.tribalfs.milko.app.util

import com.tribalfs.milko.ui.model.DriveSpaceInfo
import com.tribalfs.milko.app.util.LogHelper.debugLog
import java.io.File

fun getDriveSpaceInfo(drivePath: String): DriveSpaceInfo? {
    // Attempt 1: Standard API
    var spaceInfo = getDriveSpaceInfoStdApi(drivePath)
    if (spaceInfo != null && spaceInfo.totalSpace > 0) { // Check if standard API returned valid data
        debugLog("Got space info via standard API for $drivePath")
        return spaceInfo
    }

    debugLog("Standard API failed for $drivePath or returned invalid data. Attempting with root.")

    // Attempt 2: Fallback to Root if standard failed or returned invalid data
    if (isDeviceRootedAndAppHasPermission()) { // Implement this check
        spaceInfo = RootHelper.getDriveSpaceInfoAsRoot(drivePath)
        if (spaceInfo != null) {
            debugLog("Got space info via root for $drivePath")
        } else {
            debugLog("Failed to get space info via root for $drivePath.")
        }
    } else {
        debugLog("Root not available or permission not granted. Cannot use root fallback for $drivePath.")
    }

    return spaceInfo
}

private fun getDriveSpaceInfoStdApi(drivePath: String): DriveSpaceInfo? {
    try {
        val file = File(drivePath)
        if (!file.exists() || !file.isDirectory) {
            // Path doesn't exist or is not a directory
            return null
        }

        val totalSpace = file.totalSpace
        val freeSpace = file.freeSpace

        if (totalSpace <= 0) { // totalSpace can be 0 if path is invalid or not accessible
            return DriveSpaceInfo(drivePath, -1, -1, -1, 0.0f)
        }

        val usedSpace = totalSpace - freeSpace
        val usedPercentage = (usedSpace / totalSpace.toFloat()) * 100.0f

        return DriveSpaceInfo(
            path = drivePath,
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            usedSpace = usedSpace,
            usedPercentage = usedPercentage
        )
    } catch (e: Exception) {
        debugLog("Error getting drive space for $drivePath: $e")
        return null
    }
}

private fun isDeviceRootedAndAppHasPermission(): Boolean {
    val check = RootHelper.executeAsRoot("echo hello")
    return (check != null && check.exitCode == 0).also { debugLog( "isDeviceRootedAndAppHasPermission $it") }
}

