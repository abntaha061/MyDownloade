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

    private val _isAdBlockEnabled = MutableStateFlow(repository.getSettingsAdBlock())
    val isAdBlockEnabled: StateFlow<Boolean> = _isAdBlockEnabled.asStateFlow()

    fun toggleAdBlock() {
        val nextMode = !_isAdBlockEnabled.value
        repository.saveSettingsAdBlock(nextMode)
        _isAdBlockEnabled.value = nextMode
    }

    private val _currentPageTitle = MutableStateFlow("")
    val currentPageTitle: StateFlow<String> = _currentPageTitle.asStateFlow()

    fun updatePageTitle(title: String) {
        val cleanedTitle = title.trim()
        if (cleanedTitle.isNotEmpty() && 
            !cleanedTitle.lowercase().contains("about:blank") && 
            !cleanedTitle.lowercase().contains("http") &&
            !cleanedTitle.lowercase().contains("index-v1-a1")) {
            _currentPageTitle.value = cleanedTitle
        }
    }

    private fun isGenericTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val lower = title.lowercase()
        return lower == "index" || 
               lower == "master" || 
               lower == "playlist" || 
               lower == "index-v1-a1" || 
               lower == "video" || 
               lower == "videoplayback" || 
               lower == "stream" || 
               lower.startsWith("video_") || 
               lower.contains("about:blank")
    }

    fun updateUrl(url: String) {
        if (_currentUrl.value != url) {
            _currentUrl.value = url
            clearDetectedVideo()
            _currentPageTitle.value = ""
        }
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun onVideoLinkDetected(url: String, detectedTitle: String? = null) {
        val lowerUrl = url.lowercase()
        // Reject non-http/https schemas and obvious placeholder stubs
        if (url.startsWith("blob:") || 
            url.startsWith("data:") || 
            url.startsWith("chrome:") || 
            lowerUrl.contains("stub.mp4") || 
            lowerUrl.contains("/stub") ||
            lowerUrl.contains("empty.mp4") ||
            lowerUrl.contains("black.mp4") ||
            lowerUrl.contains("loading.mp4")) {
            return
        }

        // Guard against instant double popup
        val currentDetected = _detectedVideo.value
        if (currentDetected != null && currentDetected.sourceUrl == url) return

        // If we already detected an .m3u8 (HLS master playlist), do not overwrite it with a standard video unless it is also an .m3u8
        if (currentDetected != null && currentDetected.sourceUrl.contains("m3u8") && !url.contains("m3u8")) {
            return
        }
        
        viewModelScope.launch {
            try {
                val finalTitleToUse = if (isGenericTitle(detectedTitle)) {
                    if (_currentPageTitle.value.isNotBlank() && !isGenericTitle(_currentPageTitle.value)) {
                        _currentPageTitle.value
                    } else {
                        detectedTitle ?: ""
                    }
                } else {
                    detectedTitle ?: ""
                }
                val videoInfo = extractUseCase.extract(url, _currentUrl.value, finalTitleToUse)
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
                ext = format.ext,
                referer = format.headers["Referer"],
                userAgent = format.headers["User-Agent"]
            )
            clearDetectedVideo()
        }
    }
}
