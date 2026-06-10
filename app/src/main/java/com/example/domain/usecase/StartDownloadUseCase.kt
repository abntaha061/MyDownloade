package com.example.domain.usecase

import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.repository.DownloadRepository
import java.io.File
import java.util.UUID

class StartDownloadUseCase(
    private val downloadRepository: DownloadRepository
) {
    suspend fun execute(url: String, title: String, quality: String, ext: String): DownloadTask {
        val id = UUID.randomUUID().toString()
        val cleanedTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .trim()
            .ifBlank { "Video_${System.currentTimeMillis() % 100000}" }
        val fileName = "${cleanedTitle}_$quality.$ext"
        
        val saveDir = downloadRepository.getSettingsSaveDir()
        val file = File(saveDir, fileName)
        
        try {
            val dir = File(saveDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val task = DownloadTask(
            id = id,
            url = url,
            title = cleanedTitle,
            filePath = file.absolutePath,
            status = DownloadStatus.PENDING,
            totalBytes = 0L,
            downloadedBytes = 0L,
            speed = "0 KB/s",
            timeLeft = "Pending",
            progress = 0.0f,
            addedTimestamp = System.currentTimeMillis()
        )

        downloadRepository.insertDownload(task)
        downloadRepository.startDownload(task)
        return task
    }
}
