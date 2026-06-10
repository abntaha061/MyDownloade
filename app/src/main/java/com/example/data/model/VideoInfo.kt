package com.example.data.model

data class VideoFormat(
    val quality: String, // e.g. "1080p", "720p", "480p", "360p", "Audio Only"
    val url: String,
    val ext: String = "mp4",
    val sizeBytes: Long = 0,
    val isM3U8: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

data class VideoInfo(
    val title: String,
    val sourceUrl: String,
    val thumbnailUrl: String? = null,
    val duration: String? = null,
    val formats: List<VideoFormat> = emptyList()
)
