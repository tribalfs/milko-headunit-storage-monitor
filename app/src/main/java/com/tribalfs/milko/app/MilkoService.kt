package com.tribalfs.milko.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tribalfs.milko.R
import com.tribalfs.milko.data.Preference
import com.tribalfs.milko.ui.MainActivity
import com.tribalfs.milko.app.util.LogHelper.debugLog
import com.tribalfs.milko.app.util.RootHelper.deleteDirectoryRecursivelyAsRoot
import com.tribalfs.milko.app.util.getDriveSpaceInfo
import com.tribalfs.milko.ui.util.getRootDriveOfSelectedPath
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MilkoService: Service(), CoroutineScope by MainScope()  {

    companion object{
        private const val CHANNEL_ID = "MilkoService"
        private const val NOTIFICATION_ID = 1
        const val KEY_COMMAND = "command"
        const val COMMAND_START = "start"
        const val COMMAND_STOP = "stop"
    }

    @Inject
    lateinit var preference: Preference
    private var isServiceRunning = false
    private var selectedPathUri: Uri?= null
    private val selectedPath get() = selectedPathUri?.path!!
    private var interval: Long = 3 * 60 * 1000L
    private var threshold: Int = 90
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.getStringExtra(KEY_COMMAND)) {
                COMMAND_START -> {
                    launch {
                        val milkoSettings = preference.milkoSettingsFlow.first()
                        selectedPathUri = milkoSettings.docUri
                        threshold = milkoSettings.thresholdPercent
                        interval = milkoSettings.interval * 60 * 1000L
                        startService()
                    }
                }
                COMMAND_STOP -> stopService()
            }
        }

        return START_STICKY
    }

    private fun startService() {
        if (!isServiceRunning) {
            isServiceRunning = true
            startForeground(NOTIFICATION_ID, createNotification())
            monitorDirectory()
        }
    }

    private fun stopService() {
        if (isServiceRunning) {
            isServiceRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?) = null

    private var notificationBuilderInstance: NotificationCompat.Builder? = null

    private fun createNotification(): Notification {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        if (notificationBuilderInstance == null) {
            notificationBuilderInstance = NotificationCompat.Builder(this, CHANNEL_ID).apply {
                setContentTitle("Milko Service")
                setContentText("Monitoring directory on USB drive")
                setSmallIcon(R.mipmap.ic_launcher)
                setContentIntent(pendingIntent)
                setOnlyAlertOnce(true)
            }
        }
        return notificationBuilderInstance!!.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "File Monitor Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    private var isDeleteInProcess = false

    private fun monitorDirectory() {
        launch(Dispatchers.IO) {
            while (isActive) {
                if (!isDeleteInProcess) {
                    val driveSpaceInfo = getDriveSpaceInfo(getRootDriveOfSelectedPath(selectedPath).path)
                    val usePercent = driveSpaceInfo!!.usedPercentage
                    updateNotification(usePercent)
                    debugLog("driveSpaceInfo: $driveSpaceInfo")
                    if (usePercent > threshold) {
                        deleteOldestFiles()
                    }
                }
                delay(interval)
            }
        }
    }

    private fun deleteOldestFiles() {
        isDeleteInProcess = true

        val directory = File(selectedPath)

        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()

            if (files != null && files.isNotEmpty()) {
                files.sortBy { it.lastModified() }

                var usePercent = getDriveSpaceInfo(getRootDriveOfSelectedPath(selectedPath).path)!!.usedPercentage
                var index = 0

                while (usePercent > threshold && index < files.size) {
                    val fileToDelete = files[index]
                    deleteFile(fileToDelete)
                    usePercent = getDriveSpaceInfo(getRootDriveOfSelectedPath(selectedPath).path)!!.usedPercentage
                    updateNotification(usePercent)
                    index++
                }
            }
        }
        isDeleteInProcess = false
    }


    private fun updateNotification(percentage: Float) {
        notificationBuilderInstance!!.setContentText( "${getRootDriveOfSelectedPath(selectedPath).path}: $percentage%")
        notificationManager.notify(NOTIFICATION_ID, notificationBuilderInstance!!.build())
    }

    private fun deleteFile(file: File) {
        val log = "${file.name} (${file.lastModified()})"
        if (file.delete()) {
            debugLog("Delete $log: successful")
        } else {
            if (deleteDirectoryRecursivelyAsRoot(file.path, true)) {
                debugLog("Delete $log: successful using root")
            } else {
                debugLog("Delete $log: unsuccessful")
            }
        }
    }
}
