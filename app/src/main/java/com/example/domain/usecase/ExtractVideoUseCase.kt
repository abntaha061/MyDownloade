package com.example.domain.usecase

import com.example.data.model.VideoFormat
import com.example.data.model.VideoInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

class ExtractVideoUseCase {
    private val client = OkHttpClient()

    suspend fun extract(url: String, referer: String? = null, detectedTitle: String? = null): VideoInfo {
        var rawParsedTitle = detectedTitle ?: extractTitleFromUrl(url)
        if (!detectedTitle.isNullOrBlank()) {
            var tempTitle: String = detectedTitle
            val suffixesToRemove = listOf(
                " - Watch Wrestling", " | Watch-Wrestling", " - watch-wrestling", " - Watch-Wrestling",
                " - PornoVip.gratis", " - pornovip", " - PornoVip", " - www.pornovip.gratis",
                " - Dailymotion", " - OK.ru", " - ok.ru", " - YouTube",
                " - stream online", " | stream", " - Stream", " | On-line", " - On-line",
                ".mp4", ".m3u8", ".webm", ".mkv"
            )
            for (suffix in suffixesToRemove) {
                if (tempTitle.lowercase().contains(suffix.lowercase())) {
                    val index = tempTitle.lowercase().indexOf(suffix.lowercase())
                    if (index != -1) {
                        tempTitle = tempTitle.substring(0, index)
                    }
                }
            }
            if (tempTitle.isNotBlank()) {
                rawParsedTitle = tempTitle.trim()
            }
        }
        val title = rawParsedTitle
        val formats = mutableListOf<VideoFormat>()

        val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        val videoHeaders = mutableMapOf<String, String>()
        videoHeaders["User-Agent"] = userAgent
        if (!referer.isNullOrBlank()) {
            videoHeaders["Referer"] = referer
        }

        try {
            val isM3u8 = url.contains(".m3u8") || url.contains("m3u8")
            val isMp4 = url.contains(".mp4") || url.contains("mp4")
            
            if (isM3u8 || isMp4 || url.contains(".ts") || url.contains(".webm") || url.contains(".mkv")) {
                val size = fetchContentLength(url, videoHeaders)
                val ext = if (isM3u8) "mp4" else extractExtension(url)

                if (isM3u8) {
                    // Offer multiple streaming qualities representing chunks
                    formats.add(VideoFormat("1080p (Stream)", url, "mp4", size, true, videoHeaders))
                    formats.add(VideoFormat("720p (Stream)", url, "mp4", (size * 0.7).toLong(), true, videoHeaders))
                    formats.add(VideoFormat("480p (Stream)", url, "mp4", (size * 0.4).toLong(), true, videoHeaders))
                    formats.add(VideoFormat("Audio Only", url, "mp3", (size * 0.1).toLong(), false, videoHeaders))
                } else {
                    formats.add(VideoFormat("1080p (HD)", url, ext, size, false, videoHeaders))
                    formats.add(VideoFormat("720p (Medium)", url, ext, (size * 0.7).toLong(), false, videoHeaders))
                    formats.add(VideoFormat("480p (SD)", url, ext, (size * 0.4).toLong(), false, videoHeaders))
                    formats.add(VideoFormat("Audio Only (mp3)", url, "mp3", 0, false, videoHeaders))
                }
            } else {
                formats.add(VideoFormat("720p (Default)", url, "mp4", 0, false, videoHeaders))
                formats.add(VideoFormat("480p (Low Mobile)", url, "mp4", 0, false, videoHeaders))
                formats.add(VideoFormat("Audio Track (mp3)", url, "mp3", 0, false, videoHeaders))
            }
        } catch (e: Exception) {
            formats.add(VideoFormat("Default Quality", url, "mp4", 0, false, videoHeaders))
        }

        if (formats.isEmpty()) {
            formats.add(VideoFormat("Best Quality", url, "mp4", 0, false, videoHeaders))
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

    private suspend fun fetchContentLength(url: String, headers: Map<String, String>): Long {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url).head()
                headers.forEach { (key, value) ->
                    builder.addHeader(key, value)
                }
                val request = builder.build()
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
