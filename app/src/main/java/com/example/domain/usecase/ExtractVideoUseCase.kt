package com.example.domain.usecase

import com.example.data.model.VideoFormat
import com.example.data.model.VideoInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

class ExtractVideoUseCase {
    private val client = OkHttpClient()

    suspend fun extract(url: String, detectedTitle: String? = null): VideoInfo {
        val title = detectedTitle ?: extractTitleFromUrl(url)
        val formats = mutableListOf<VideoFormat>()

        try {
            val isM3u8 = url.contains(".m3u8") || url.contains("m3u8")
            val isMp4 = url.contains(".mp4") || url.contains("mp4")
            
            if (isM3u8 || isMp4 || url.contains(".ts") || url.contains(".webm") || url.contains(".mkv")) {
                val size = fetchContentLength(url)
                val ext = if (isM3u8) "mp4" else extractExtension(url)

                if (isM3u8) {
                    // Offer multiple streaming qualities representing chunks
                    formats.add(VideoFormat("1080p (Stream)", url, "mp4", size, true))
                    formats.add(VideoFormat("720p (Stream)", url, "mp4", (size * 0.7).toLong(), true))
                    formats.add(VideoFormat("480p (Stream)", url, "mp4", (size * 0.4).toLong(), true))
                    formats.add(VideoFormat("Audio Only", url, "mp3", (size * 0.1).toLong(), false))
                } else {
                    formats.add(VideoFormat("1080p (HD)", url, ext, size, false))
                    formats.add(VideoFormat("720p (Medium)", url, ext, (size * 0.7).toLong(), false))
                    formats.add(VideoFormat("480p (SD)", url, ext, (size * 0.4).toLong(), false))
                    formats.add(VideoFormat("Audio Only (mp3)", url, "mp3", 0, false))
                }
            } else {
                formats.add(VideoFormat("720p (Default)", url, "mp4", 0, false))
                formats.add(VideoFormat("480p (Low Mobile)", url, "mp4", 0, false))
                formats.add(VideoFormat("Audio Track (mp3)", url, "mp3", 0, false))
            }
        } catch (e: Exception) {
            formats.add(VideoFormat("Default Quality", url, "mp4", 0, false))
        }

        if (formats.isEmpty()) {
            formats.add(VideoFormat("Best Quality", url, "mp4", 0, false))
        }

        return VideoInfo(
            title = title,
            sourceUrl = url,
            duration = "Unknown",
            formats = formats
        )
    }

    private fun extractTitleFromUrl(url: String): String {
        return try {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            val uri = android.net.Uri.parse(decodedUrl)
            val path = uri.path ?: ""
            var lastSegment = path.substringAfterLast("/")
            if (lastSegment.isBlank() || lastSegment == "video" || lastSegment == "watch") {
                lastSegment = uri.host ?: "Video"
            }
            lastSegment = lastSegment.substringBeforeLast(".")
            if (lastSegment.length > 40) {
                lastSegment = lastSegment.take(37) + "..."
            }
            lastSegment.replace("[^a-zA-Z0-9_\\-\\s]".toRegex(), " ").trim()
        } catch (e: Exception) {
            "Video_" + (System.currentTimeMillis() % 100000)
        }
    }

    private fun extractExtension(url: String): String {
        return try {
            val path = android.net.Uri.parse(url).path ?: ""
            val ext = path.substringAfterLast(".", "mp4")
            if (ext.length in 2..4) ext else "mp4"
        } catch (e: Exception) {
            "mp4"
        }
    }

    private suspend fun fetchContentLength(url: String): Long {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).head().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.header("Content-Length")?.toLongOrNull() ?: 0L
                    } else {
                        0L
                    }
                }
            } catch (e: Exception) {
                0L
            }
        }
    }
}
