package com.example.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(context: Context) : ViewModel() {
    private val repository = AppModule.getRepository(context)

    private val _concurrentDownloads = MutableStateFlow(repository.getSettingsConcurrent())
    val concurrentDownloads: StateFlow<Int> = _concurrentDownloads.asStateFlow()

    private val _wifiOnly = MutableStateFlow(repository.getSettingsWifiOnly())
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _defaultQuality = MutableStateFlow(repository.getSettingsDefaultQuality())
    val defaultQuality: StateFlow<String> = _defaultQuality.asStateFlow()

    private val _saveDirectory = MutableStateFlow(repository.getSettingsSaveDir())
    val saveDirectory: StateFlow<String> = _saveDirectory.asStateFlow()

    private val _adblockEnabled = MutableStateFlow(repository.getSettingsAdBlock())
    val adblockEnabled: StateFlow<Boolean> = _adblockEnabled.asStateFlow()

    fun updateSettings(concurrent: Int, wifiOnly: Boolean, quality: String, saveDirectory: String, adblockEnabled: Boolean) {
        repository.saveSettings(concurrent, wifiOnly, quality, saveDirectory)
        repository.saveSettingsAdBlock(adblockEnabled)
        _concurrentDownloads.value = concurrent
        _wifiOnly.value = wifiOnly
        _defaultQuality.value = quality
        _saveDirectory.value = saveDirectory
        _adblockEnabled.value = adblockEnabled
    }
}
