package com.example.presentation.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.VideoFormat
import com.example.data.model.VideoInfo
import com.example.di.AppModule
import com.example.domain.usecase.ExtractVideoUseCase
import com.example.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowserViewModel(context: Context) : ViewModel() {
    private val repository = AppModule.getRepository(context)
    private val extractUseCase = ExtractVideoUseCase()
    private val startDownloadUseCase = StartDownloadUseCase(repository)

    private val _currentUrl = MutableStateFlow("https://google.com")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _detectedVideo = MutableStateFlow<VideoInfo?>(null)
    val detectedVideo: StateFlow<VideoInfo?> = _detectedVideo.asStateFlow()

    fun updateUrl(url: String) {
        _currentUrl.value = url
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun onVideoLinkDetected(url: String, detectedTitle: String? = null) {
        // Guard against instant double popup
        val currentDetected = _detectedVideo.value
        if (currentDetected != null && currentDetected.sourceUrl == url) return
        
        viewModelScope.launch {
            try {
                val videoInfo = extractUseCase.extract(url, detectedTitle)
                _detectedVideo.value = videoInfo
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearDetectedVideo() {
        _detectedVideo.value = null
    }

    fun startDownload(format: VideoFormat, customTitle: String) {
        val video = _detectedVideo.value ?: return
        viewModelScope.launch {
            startDownloadUseCase.execute(
                url = format.url,
                title = customTitle.ifBlank { video.title },
                quality = format.quality,
                ext = format.ext
            )
            clearDetectedVideo()
        }
    }
}
