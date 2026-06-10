package com.example.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DownloadTask
import com.example.di.AppModule
import com.example.domain.usecase.ExtractVideoUseCase
import com.example.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(context: Context) : ViewModel() {
    private val repository = AppModule.getRepository(context)
    private val extractUseCase = ExtractVideoUseCase()
    private val startDownloadUseCase = StartDownloadUseCase(repository)

    val downloads: StateFlow<List<DownloadTask>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addManualDownload(url: String, customTitle: String) {
        viewModelScope.launch {
            try {
                val videoInfo = extractUseCase.extract(url, customTitle)
                val format = videoInfo.formats.firstOrNull() ?: return@launch
                startDownloadUseCase.execute(
                    url = format.url,
                    title = customTitle.ifBlank { videoInfo.title },
                    quality = format.quality,
                    ext = format.ext
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
