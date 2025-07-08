package com.tribalfs.milko.app.util

import com.tribalfs.milko.ui.model.DriveSpaceInfo
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object RootHelper {

    data class CommandResult(
        val exitCode: Int,
        val output: List<String>,
        val error: List<String>
    )

    fun executeAsRoot(vararg commands: String): CommandResult? {
        var process: Process? = null
        var os: DataOutputStream? = null
        val outputLines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()
        var exitCode = -1

        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)

            for (command in commands) {
                LogHelper.debugLog("Executing: $command")
                os.writeBytes("$command\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (outputReader.readLine().also { line = it } != null) {
                outputLines.add(line!!)
            }

            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                errorLines.add(line!!)
            }

            exitCode = process.waitFor()
            LogHelper.debugLog("Root command exit code: $exitCode")
            if (errorLines.isNotEmpty()) {
                LogHelper.debugLog("Root command errors: ${errorLines.joinToString("\n")}")
            }

        } catch (e: Exception) {
            LogHelper.debugLog("Error executing root command: $e")
            errorLines.add("Exception: ${e.message}")
            return null // Indicate failure to even start the process
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (e: Exception) {
                LogHelper.debugLog("Error closing root process streams: $e")
            }
        }
        return CommandResult(exitCode, outputLines, errorLines)
    }

    /**
     * Lists directory contents using root.
     * Returns a list of item names. Appends "/" to directory names.
     */
    fun listDirectoryAsRoot(path: String): List<File> {
        // -1: one entry per line
        // -A: do not list implied . and ..
        // -p: append / indicator to directories
        // Ensure path is quoted to handle spaces
        val command = "ls -1Ap \"$path\""
        val result = executeAsRoot(command)
        return if (result != null && result.exitCode == 0) {
            result.output.map { File(it) }
        } else {
            LogHelper.debugLog("Failed to list directory as root: $path. Errors: ${result?.error?.joinToString()}")
            emptyList()
        }
    }

    /**
     * Checks if a path is a readable directory using root.
     * This is a basic check. 'test -r' checks readability, 'test -d' checks if directory.
     */
    fun isReadableDirectoryAsRoot(path: String): Boolean {
        // test -d "$path" && test -r "$path" && test -x "$path"
        // For a directory, readable and executable (for listing) are important
        val command = "if [ -d \"$path\" ] && [ -r \"$path\" ] && [ -x \"$path\" ]; then echo \"true\"; else echo \"false\"; fi"
        val result = executeAsRoot(command)
        return if (result != null && result.exitCode == 0 && result.output.isNotEmpty()) {
            result.output.firstOrNull()?.trim() == "true"
        } else {
            LogHelper.debugLog("Failed to check directory status as root: $path. Errors: ${result?.error?.joinToString()}")
            false
        }
    }

    /**
     * Checks if a path is a readable file using root.
     */
    fun isReadableFileAsRoot(path: String): Boolean {
        val command = "if [ -f \"$path\" ] && [ -r \"$path\" ]; then echo \"true\"; else echo \"false\"; fi"
        val result = executeAsRoot(command)
        return if (result != null && result.exitCode == 0 && result.output.isNotEmpty()) {
            result.output.firstOrNull()?.trim() == "true"
        } else {
            LogHelper.debugLog("Failed to check file status as root: $path. Errors: ${result?.error?.joinToString()}")
            false
        }
    }

    /**
     * Gets disk space information for a given path using the 'df' command as root.
     * The path should ideally be a mount point or a directory within a mount.
     * Returns null if the command fails or output cannot be parsed.
     *
     * Note: `df` output can vary slightly between Android versions/OEMs.
     * This parser assumes a common POSIX-like output format.
     * Example `df` output line for a path:
     * Filesystem     1K-blocks      Used Available Use% Mounted on
     * /dev/block/dm-10 121866748  91007876  30858872  75% /data
     * Or for a specific path:
     * Filesystem     1K-blocks    Used Available Use% File
     * /dev/fuse        61045756 1281048  59764708   3% /storage/emulated/0
     */
    fun getDriveSpaceInfoAsRoot(targetPath: String): DriveSpaceInfo? {
        // df options:
        // -k: sizes in 1K blocks (POSIX standard, more consistent than -h)
        // -P: Use POSIX output format (more stable for parsing)
        // The command will be: df -kP "path"
        val command = "df -kP \"$targetPath\""
        val result = executeAsRoot(command)

        if (result == null || result.exitCode != 0 || result.output.size < 2) {
            LogHelper.debugLog("Failed to execute 'df' as root for $targetPath or insufficient output. Errors: ${result?.error?.joinToString()}")
            return null
        }

        // Output typically looks like:
        // Filesystem     1K-blocks      Used Available Use% Mounted on (or File)
        // /dev/fuse        xxxxxxx   xxxxxxx   xxxxxxx  xx% /path/given
        // We are interested in the second line (index 1)
        val dataLine = result.output[1]
        val parts = dataLine.trim().split(Regex("\\s+")) // Split by one or more whitespace

        // Expected order (can vary slightly, this is common for POSIX df -P):
        // 0: Filesystem (e.g., /dev/block/sda1)
        // 1: 1K-blocks (Total)
        // 2: Used
        // 3: Available
        // 4: Use% (e.g., 75%)
        // 5: Mounted on (or File, if path was given directly)

        if (parts.size < 5) { // Need at least up to Use%
            LogHelper.debugLog("Could not parse 'df' output for $targetPath. Line: '$dataLine', Parts: $parts")
            return null
        }

        try {
            val totalBlocks1K = parts[1].toLong()
            val usedBlocks1K = parts[2].toLong()
            val availableBlocks1K = parts[3].toLong()

            val totalSpaceBytes = totalBlocks1K * 1024
            val usedSpaceBytes = usedBlocks1K * 1024
            val freeSpaceBytes = availableBlocks1K * 1024 // df's "Available" is usually free to non-root

            val calculatedUsedPercentage = if (totalSpaceBytes > 0) (usedSpaceBytes.toFloat() / totalSpaceBytes) * 100.0f else 0.0f

            return DriveSpaceInfo(
                path = targetPath, // or parts[5] if you prefer the "Mounted on" path
                totalSpace = totalSpaceBytes,
                freeSpace = freeSpaceBytes,
                usedSpace = usedSpaceBytes,
                usedPercentage = calculatedUsedPercentage
            )
        } catch (e: NumberFormatException) {
            LogHelper.debugLog("Error parsing numbers from 'df' output for $targetPath: $dataLine: $e")
            return null
        }
    }

    /**
     * Deletes a file using root ('rm' command).
     *
     * @param filePath The absolute path to the file to delete.
     * @param force Whether to use the '-f' (force) option with 'rm'.
     *              Using force can suppress some errors and attempt deletion even if write-protected,
     *              but use with extreme caution.
     * @return True if 'rm' command exit code was 0 (success), false otherwise.
     */
    fun deleteFileAsRoot(filePath: String, force: Boolean = false): Boolean {
        // Construct the 'rm' command
        // Ensure filePath is quoted to handle spaces or special characters
        val command = if (force) {
            "rm -f \"$filePath\""
        } else {
            "rm \"$filePath\""
        }

        LogHelper.debugLog("Attempting to delete file as root: $command")
        val result = executeAsRoot(command)

        if (result == null) {
            LogHelper.debugLog("Failed to execute root command for deleting file: $filePath (executeAsRoot returned null)")
            return false
        }

        if (result.exitCode == 0) {
            LogHelper.debugLog("File successfully deleted as root: $filePath")
            // You might want to also check if the file truly no longer exists here
            // using `!isPathExistsAsRoot(filePath)` if you have such a function.
            return true
        } else {
            LogHelper.debugLog("Failed to delete file as root: $filePath. Exit code: ${result.exitCode}")
            if (result.error.isNotEmpty()) {
                LogHelper.debugLog("Deletion errors: ${result.error.joinToString("\n")}")
            }
            if (result.output.isNotEmpty()) { // rm might output something on failure too
                LogHelper.debugLog("Deletion output: ${result.output.joinToString("\n")}")
            }
            return false
        }
    }

    /**
     * Deletes a directory recursively using root ('rm -r' command).
     * USE WITH EXTREME CAUTION! This will delete the directory and all its contents.
     *
     * @param directoryPath The absolute path to the directory to delete.
     * @param force Whether to use the '-f' (force) option with 'rm'.
     *              This makes it even more dangerous as it will suppress most errors.
     * @return True if 'rm -r' command exit code was 0, false otherwise.
     */
    fun deleteDirectoryRecursivelyAsRoot(directoryPath: String, force: Boolean = false): Boolean {
        // Ensure it's not a trivial path like "/" or "/system" unless absolutely intended
        if (directoryPath.isBlank() || directoryPath == "/" || directoryPath.startsWith("/system") || directoryPath.startsWith("/efs")) {
            // Add more critical paths if needed
            LogHelper.debugLog("Safety check: Refusing to recursively delete critical path as root: $directoryPath")
            return false
        }

        val command = if (force) {
            "rm -rf \"$directoryPath\"" // r for recursive, f for force
        } else {
            "rm -r \"$directoryPath\""
        }

        LogHelper.debugLog("Attempting to recursively delete directory as root: $command")
        val result = executeAsRoot(command)

        if (result == null) {
            LogHelper.debugLog("Failed to execute root command for deleting directory: $directoryPath (executeAsRoot returned null)")
            return false
        }

        if (result.exitCode == 0) {
            LogHelper.debugLog("Directory successfully deleted recursively as root: $directoryPath")
            return true
        } else {
            LogHelper.debugLog("Failed to recursively delete directory as root: $directoryPath. Exit code: ${result.exitCode}")
            if (result.error.isNotEmpty()) {
                LogHelper.debugLog("Recursive deletion errors: ${result.error.joinToString("\n")}")
            }
            if (result.output.isNotEmpty()) {
                LogHelper.debugLog("Recursive deletion output: ${result.output.joinToString("\n")}")
            }
            return false
        }
    }

    // Optional: Helper to check if a path exists using root (useful for verification)
    fun isPathExistsAsRoot(path: String): Boolean {
        // "test -e" checks if path exists (file or directory)
        val command = "if [ -e \"$path\" ]; then echo \"true\"; else echo \"false\"; fi"
        val result = executeAsRoot(command)
        return if (result != null && result.exitCode == 0 && result.output.isNotEmpty()) {
            result.output.firstOrNull()?.trim() == "true"
        } else {
            // If the command fails, conservatively assume it doesn't exist or isn't accessible
            false
        }
    }
}