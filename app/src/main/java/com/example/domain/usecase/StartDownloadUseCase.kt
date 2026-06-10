package com.example.domain.usecase

import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.repository.DownloadRepository
import java.io.File
import java.util.UUID

class StartDownloadUseCase(
    private val downloadRepository: DownloadRepository
) {
    suspend fun execute(
        url: String, 
        title: String, 
        quality: String, 
        ext: String,
        referer: String? = null,
        userAgent: String? = null
    ): DownloadTask {
        val id = UUID.randomUUID().toString()
        val cleanedTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .trim()
            .ifBlank { "Video_${System.currentTimeMillis() % 100000}" }
        
        val saveDir = downloadRepository.getSettingsSaveDir()
        
        var finalFile = File(saveDir, "${cleanedTitle}_$quality.$ext")
        var finalTitle = cleanedTitle
        var counter = 1
        while (finalFile.exists() || downloadRepository.isFilePathInUse(finalFile.absolutePath)) {
            finalTitle = "${cleanedTitle} ($counter)"
            finalFile = File(saveDir, "${finalTitle}_$quality.$ext")
            counter++
        }
        
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
            title = finalTitle,
            filePath = finalFile.absolutePath,
            status = DownloadStatus.PENDING,
            totalBytes = 0L,
            downloadedBytes = 0L,
            speed = "0 KB/s",
            timeLeft = "Pending",
            progress = 0.0f,
            addedTimestamp = System.currentTimeMillis(),
            referer = referer,
            userAgent = userAgent
        )

        downloadRepository.insertDownload(task)
        downloadRepository.startDownload(task)
        return task
    }
}
