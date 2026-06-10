package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val filePath: String,
    val status: String, // PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speed: String,
    val timeLeft: String,
    val progress: Float,
    val errorMessage: String?,
    val addedTimestamp: Long,
    val referer: String? = null,
    val userAgent: String? = null
) {
    fun toDomain(): DownloadTask {
        val statusEnum = try {
            DownloadStatus.valueOf(status)
        } catch (e: Exception) {
            DownloadStatus.FAILED
        }
        return DownloadTask(
            id = id,
            url = url,
            title = title,
            filePath = filePath,
            status = statusEnum,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            speed = speed,
            timeLeft = timeLeft,
            progress = progress,
            errorMessage = errorMessage,
            addedTimestamp = addedTimestamp,
            referer = referer,
            userAgent = userAgent
        )
    }

    companion object {
        fun fromDomain(task: DownloadTask): DownloadEntity {
            return DownloadEntity(
                id = task.id,
                url = task.url,
                title = task.title,
                filePath = task.filePath,
                status = task.status.name,
                totalBytes = task.totalBytes,
                downloadedBytes = task.downloadedBytes,
                speed = task.speed,
                timeLeft = task.timeLeft,
                progress = task.progress,
                errorMessage = task.errorMessage,
                addedTimestamp = task.addedTimestamp,
                referer = task.referer,
                userAgent = task.userAgent
            )
        }
    }
}
