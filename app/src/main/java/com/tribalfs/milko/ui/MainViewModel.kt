package com.tribalfs.milko.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tribalfs.milko.data.MilkoSettings
import com.tribalfs.milko.data.Preference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preference: Preference
): ViewModel() {

    @OptIn(FlowPreview::class)
    val milkoSettingsStateFlow = preference.milkoSettingsFlow
        .debounce(80)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000),  initialValue = MilkoSettings())

    fun setDocUri(uri: Uri) = viewModelScope.launch {
        preference.setDocUri(uri)
    }

    fun setThresholdPercent(percent: Int) = viewModelScope.launch {
        if (percent == milkoSettingsStateFlow.value.thresholdPercent) return@launch
        preference.setThresholdPercent(percent)
    }

    fun setInterval(minutes: Int) = viewModelScope.launch {
        if (minutes == milkoSettingsStateFlow.value.interval) return@launch
        preference.setInterval(minutes)
    }
}