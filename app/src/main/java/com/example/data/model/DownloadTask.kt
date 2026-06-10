package com.example.data.model

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val filePath: String,
    val status: DownloadStatus,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speed: String, // e.g. "1.5 MB/s"
    val timeLeft: String, // e.g. "00:25"
    val progress: Float, // 0.0 to 1.0
    val errorMessage: String? = null,
    val addedTimestamp: Long
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}
