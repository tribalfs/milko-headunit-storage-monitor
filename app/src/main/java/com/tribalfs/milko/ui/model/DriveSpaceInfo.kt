package com.tribalfs.milko.ui.model

import android.annotation.SuppressLint

@SuppressLint("DefaultLocale")
data class DriveSpaceInfo(
    val path: String,
    val totalSpace: Long,    // Bytes
    val freeSpace: Long,     // Bytes
    val usedSpace: Long,     // Bytes
    val usedPercentage: Float // 0.0 to 100.0
) {
    override fun toString(): String {
        return "DriveSpaceInfo(path='$path', \n" +
                "  totalSpace=${formatBytes(totalSpace)}, \n" +
                "  freeSpace=${formatBytes(freeSpace)}, \n" +
                "  usedSpace=${formatBytes(usedSpace)}, \n" +
                "  usedPercentage=${String.format("%.2f", usedPercentage)}%)"
    }

    companion object{
        fun formatBytes(bytes: Long): String {
            if (bytes < 0) return "N/A"
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.2f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }
}