package com.tribalfs.milko.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.tribalfs.milko.app.util.LogHelper.debugLog
import com.tribalfs.milko.app.util.RootHelper.isReadableDirectoryAsRoot
import com.tribalfs.milko.app.util.RootHelper.listDirectoryAsRoot
import java.io.File

/**
 * Shows a dialog to select a storage volume (primary, USB drives, etc.)
 */
fun showStorageVolumePicker(
    context: Context,
    onVolumeSelected: (File) -> Unit, // Callback with the root of the selected volume
    onPermissionDenied: () -> Unit = {}
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED
    ) {
        onPermissionDenied() // You should request permission here
        debugLog("READ_EXTERNAL_STORAGE permission not granted.")
        context.semToast("Storage permission is required.", Toast.LENGTH_SHORT)
        return
    }

    val storageRoots = getAvailableStorageRoots(context)

    if (storageRoots.isEmpty()) {
        debugLog("No storage volumes found.")
        context.semToast("No storage volumes accessible.", Toast.LENGTH_SHORT)
        return
    }

    val displayNames = storageRoots.map { file ->
        when {
            file.absolutePath.equals(Environment.getExternalStorageDirectory().absolutePath, ignoreCase = true) -> "Internal Shared Storage"
            isUsbStorage(file, context) -> "USB Drive: ${file.name}"
            else -> "External Storage: ${file.name}" // For SD cards etc.
        }
    }.toTypedArray()

    AlertDialog.Builder(context)
        .setTitle("Select Storage")
        .setItems(displayNames) { _, which ->
            onVolumeSelected(storageRoots[which])
        }
        .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}


/**
 * Retrieves a list of File objects representing the roots of available storage volumes.
 */
fun getAvailableStorageRoots(context: Context): List<File> {
    val roots = mutableSetOf<File>()

    // 1. Primary shared storage
    roots.add(Environment.getExternalStorageDirectory())

    // 2. Other storage volumes (including USB, SD cards)
    val externalFilesDirs =  context.getExternalFilesDirs(null)
    debugLog("externalFilesDirs: ${externalFilesDirs.size}")

    for (appSpecificDir in externalFilesDirs) {
        if (appSpecificDir != null) {
            getSubStorageDirectoryByHeuristic(appSpecificDir.path).let { roots.add(it) }
        }
    }

    // 3. Manually add potential known USB paths
   for (i in listOf<Any>("otg", 0, 1, 2, 3, 4, 5)) {
        val usbFile = File("/storage/usb$i")
        if ((usbFile.exists() && usbFile.isDirectory) || isReadableDirectoryAsRoot(usbFile.path)) {
            debugLog("Manually trying to add potential USB path: ${usbFile.path}, CanRead: ${usbFile.canRead()}")
            roots.add(usbFile)
        } else {
            debugLog("Manually potential USB path ${usbFile.path} does not exist or is not a directory.")
        }
    }
    return roots.toList()
}

private fun getSubStorageDirectoryByHeuristic(path: String): File {
    var current = File(path)
    while (current.parentFile != null) {
        if (current.parentFile?.name == "data" && current.parentFile?.parentFile?.name == "Android") {
            current = current.parentFile!!.parentFile!!.parentFile!!
            break
        } else {
            current = current.parentFile!!
        }
    }
    return current
}
/**
 * A more specific (but still heuristic) check if a File path might be a USB drive.
 * This is highly dependent on how the OEM mounts USB drives.
 */
private fun isUsbStorage(file: File, context: Context): Boolean {
    // This is a simplified check. On newer Android versions (API 26+),
    // StorageManager.getStorageVolumes() provides more reliable info,
    // but we're targeting API 25 and using java.io.File.

    val path = file.absolutePath.lowercase()
    if (path.contains("usb") || path.contains("udisk")) return true

    // Check against paths from ContextCompat.getExternalFilesDirs
    // If a path from getExternalFilesDirs is not the primary external storage
    // and is removable, it's likely an SD card or USB.
    val primaryExternalStorageAppPath = context.getExternalFilesDir(null)?.absolutePath
    val allAppSpecificDirs = context.getExternalFilesDirs(null)

    for (appSpecificDir in allAppSpecificDirs) {
        if (appSpecificDir != null && appSpecificDir.absolutePath.startsWith(file.absolutePath)) {
            // If our `file` is an ancestor of one of the app-specific dirs
            if (primaryExternalStorageAppPath != null && appSpecificDir.absolutePath.equals(primaryExternalStorageAppPath, ignoreCase = true)) {
                // This is the primary storage
                continue
            }
            // If it's not primary and it's an app-specific dir on some volume,
            // it's a candidate for removable storage (SD or USB).
            // Differentiating between SD and USB with only java.io.File is hard.
            // We are relying on path naming conventions like "usb" or "udisk".
            // For API 25, true determination is tricky without platform specific knowledge.
            return true // It's a secondary storage, could be USB.
        }
    }
    return false
}


/**
 * Shows a directory picker starting from a given root path.
 * This is the recursive part of your picker.
 */
fun showDirectoryPickerFromRoot(
    context: Context,
    currentFile: File,
    onDirectorySelected: (File) -> Unit,
    onPermissionDenied: () -> Unit // Keep this if you re-check permissions deeper
) {
    // Permission check should ideally be done before even calling this,
    // but can be re-verified if necessary.
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED
    ) {
        onPermissionDenied()
        return
    }

    var directoryContents = currentFile.listFiles { file -> file.isDirectory && file.canRead() }
        ?.sortedBy { it.name.lowercase() } // Sort for better UX
        ?: emptyList()

    if (directoryContents.isEmpty()){
        directoryContents = listDirectoryAsRoot(currentFile.path)
    }

    val displayNames = directoryContents.map { it.name }.toTypedArray()

    val builder = AlertDialog.Builder(context)
        .setTitle("Select: ${currentFile.name}")
        .setItems(displayNames) { _, which ->
            showDirectoryPickerFromRoot(context, directoryContents[which], onDirectorySelected, onPermissionDenied)
        }
        .setPositiveButton("Select This Directory") { _, _ ->
            onDirectorySelected(currentFile)
        }

    // Allow navigating "Up" unless we are at the very root of this storage volume
    // (detected by checking against the initially passed currentPath if it was a root,
    // or by checking if parentFile is null or doesn't exist/isn't readable)
    if (currentFile.parentFile != null && currentFile.parentFile!!.canRead() &&
        // Add a check to prevent going "above" the initially selected storage root if needed
        // For example, if you passed `/storage/usb0` as initial `currentPath`,
        // `currentPath.parentFile` would be `/storage`, you might want to stop there.
        // This depends on how `getAvailableStorageRoots` determines the "root".
        isPathWithinKnownRoots(context, currentFile.parentFile!!)) {

        builder.setNeutralButton("Up") { _, _ ->
            currentFile.parentFile?.let { parent ->
                showDirectoryPickerFromRoot(context, parent, onDirectorySelected, onPermissionDenied)
            }
        }
    }
    builder.setNegativeButton("Cancel") { dialog, _ ->
        dialog.dismiss()
    }
    builder.show()
}

/**
 * Helper to check if a path is likely within one of the discovered storage roots.
 * Prevents navigating "up" beyond the root of a selected volume.
 */
private fun isPathWithinKnownRoots(context: Context, path: File): Boolean {
    val knownRoots = getAvailableStorageRoots(context)
    val pathAbs = path.absolutePath
    // Check if the given path's parent is one of the roots,
    // or if the path itself is a root (meaning we can't go further up within that volume context)
    return knownRoots.any { root -> pathAbs.startsWith(root.absolutePath) }
}

