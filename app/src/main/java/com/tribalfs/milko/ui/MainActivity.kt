package com.tribalfs.milko.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.tribalfs.milko.app.MilkoService
import com.tribalfs.milko.app.MilkoService.Companion.COMMAND_START
import com.tribalfs.milko.app.MilkoService.Companion.KEY_COMMAND
import com.tribalfs.milko.databinding.ActivityMainBinding
import com.tribalfs.milko.app.util.LogHelper.debugLog
import com.tribalfs.milko.app.util.getDriveSpaceInfo
import com.tribalfs.milko.ui.model.DriveSpaceInfo.Companion.formatBytes
import com.tribalfs.milko.ui.util.getRootDriveOfSelectedPath
import com.tribalfs.milko.ui.util.isMilkoServiceRunning
import com.tribalfs.milko.ui.util.showDirectoryPickerFromRoot
import com.tribalfs.milko.ui.util.showStorageVolumePicker
import com.tribalfs.milko.ui.util.semToast
import dagger.hilt.android.AndroidEntryPoint
import dev.oneuiproject.oneui.ktx.onProgressChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


private const val REQUEST_PERMISSION_CODE = 1

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val mainViewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setSubtitle("Developed by Tribalfs")
        requestPermissions()
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initializeUI()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECEIVE_BOOT_COMPLETED
                ),
                REQUEST_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeUI()
                } else {
                    //TODO (handle permission denied)
                }
            }
        }
    }

    fun choosePath() {
        showStorageVolumePicker(
            this,
            onVolumeSelected = { selectedVolumeRoot ->
                showDirectoryPickerFromRoot(
                    this,
                    selectedVolumeRoot,
                    onDirectorySelected = { selectedDir ->
                        semToast("Selected: ${selectedDir.absolutePath}", Toast.LENGTH_LONG)
                        mainViewModel.setDocUri(selectedDir.toUri())
                    },
                    onPermissionDenied = {
                        semToast("Permission was denied.", Toast.LENGTH_SHORT)
                    }
                )
            },
            onPermissionDenied = {
                semToast("Permission was denied for volume selection.", Toast.LENGTH_SHORT)
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_PERMISSION_CODE
                )
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun initializeUI() {
        lifecycleScope.launch {
            mainViewModel.milkoSettingsStateFlow.collectLatest {
                if (it.docUri != null) {
                    val selectedPath = it.docUri.path
                    binding.selectDirectoryButton.text = "Monitoring path: $selectedPath"
                    val spaceInfo =
                        getDriveSpaceInfo(getRootDriveOfSelectedPath(selectedPath!!).path)!!
                    debugLog("spaceInfo: $spaceInfo")
                    val thresholdSize =
                        (spaceInfo.totalSpace * (it.thresholdPercent / 100f)).toLong()
                    binding.thresholdLabel.text =
                        "Deletion Threshold: ${it.thresholdPercent}% (${formatBytes(thresholdSize)})"
                } else {
                    binding.thresholdLabel.text = "Deletion Threshold: ${it.thresholdPercent}%"
                }
                binding.thresholdSeekBar.progress = it.thresholdPercent

                binding.intervalLabel.text = "Pooling Interval: ${it.interval} minutes"
                binding.intervalSeekBar.progress = it.interval
            }
        }

        binding.selectDirectoryButton.setOnClickListener { choosePath() }

        binding.thresholdSeekBar.onProgressChanged { progress ->
            mainViewModel.setThresholdPercent(progress)
        }

        binding.intervalSeekBar.onProgressChanged { progress -> mainViewModel.setInterval(progress) }

        binding.startServiceButton.setOnClickListener {
            val serviceIntent = Intent(applicationContext, MilkoService::class.java)
            if (isMilkoServiceRunning()) {
                stopService(serviceIntent)
            } else {
                val selectedUri = mainViewModel.milkoSettingsStateFlow.value.docUri
                if (selectedUri == null) {
                    semToast("Please select the dashcam recording directory first.")
                    return@setOnClickListener
                }
                serviceIntent.putExtra(KEY_COMMAND, COMMAND_START)
                startService(serviceIntent)
            }
            updateServiceStatus(isMilkoServiceRunning())
        }

        updateServiceStatus(isMilkoServiceRunning())
    }

    private fun updateServiceStatus(serviceRunning: Boolean) {
        binding.selectDirectoryButton.isEnabled = !serviceRunning
        binding.thresholdSeekBar.isEnabled = !serviceRunning
        binding.intervalSeekBar.isEnabled = !serviceRunning
        binding.startServiceButton.text = if (serviceRunning) "Stop Service" else "Start Service"
    }
}