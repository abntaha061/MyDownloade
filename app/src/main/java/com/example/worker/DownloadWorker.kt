package com.example.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.data.model.DownloadStatus
import com.example.data.repository.DownloadRepository
import com.example.di.AppModule
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository: DownloadRepository by lazy {
        AppModule.getRepository(appContext)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        })
        .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
        .build()
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val channelId = "idm_downloads_channel"
    private var notificationId = 0
    private var downloadTitle = "Downloading File"
    
    private var workerReferer: String? = null
    private var workerUserAgent: String = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, "Preparing download...")
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString("DOWNLOAD_ID") ?: return Result.failure()
        val downloadUrl = inputData.getString("DOWNLOAD_URL") ?: return Result.failure()
        val downloadPath = inputData.getString("DOWNLOAD_PATH") ?: return Result.failure()
        downloadTitle = inputData.getString("DOWNLOAD_TITLE") ?: "Video File"
        
        workerReferer = inputData.getString("DOWNLOAD_REFERER")
        workerUserAgent = inputData.getString("DOWNLOAD_USER_AGENT") ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        
        notificationId = downloadId.hashCode()
        createNotificationChannel()

        // Initialize progress notification in Foreground Mode
        try {
            setForeground(createForegroundInfo(0, "Preparing download..."))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        repository.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "IDM:DownloadWakeLock"
        )

        var attempt = 0
        var success = false
        var exceptionMsg = ""

        try {
            wakeLock.acquire(15 * 60 * 1000L) // 15 mins lock timeout
            while (attempt < 3 && !success && !isStopped) {
                try {
                    attempt++
                    if (downloadUrl.contains(".m3u8") || downloadUrl.contains("m3u8")) {
                        downloadHlsStream(downloadId, downloadUrl, downloadPath)
                    } else {
                        downloadMultipartFile(downloadId, downloadUrl, downloadPath)
                    }
                    success = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    exceptionMsg = e.message ?: "Network error"
                    delay(2000) // Sleep before retry
                }
            }
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }

        return if (success) {
            try {
                android.media.MediaScannerConnection.scanFile(appContext, arrayOf(downloadPath), null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val finalFileLength = File(downloadPath).length()
            repository.updateProgress(
                downloadId,
                finalFileLength,
                finalFileLength,
                1.0f,
                "0 KB/s",
                "Completed",
                DownloadStatus.COMPLETED
            )
            repository.updateStatus(downloadId, DownloadStatus.COMPLETED)
            updateFinishedNotification(true)
            Result.success()
        } else {
            val task = repository.getDownloadById(downloadId)
            val finalDownloaded = task?.downloadedBytes ?: 0L
            val finalTotal = task?.totalBytes ?: 0L
            val finalProgress = task?.progress ?: 0f

            repository.updateProgress(
                downloadId,
                finalDownloaded,
                finalTotal,
                finalProgress,
                "0 KB/s",
                if (isStopped) "Paused" else "Failed",
                if (isStopped) DownloadStatus.PAUSED else DownloadStatus.FAILED
            )
            updateFinishedNotification(false)
            Result.failure()
        }
    }

    /**
     * Downloads files using Multi-Part Segment requests (Chunked IDM Logic)
     */
    private suspend fun downloadMultipartFile(id: String, urlString: String, path: String) {
        val totalBytes = fetchContentLength(urlString)
        val file = File(path)
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        // Check if server supports Accept-Ranges and has content length
        val supportsRanges = checkRangeSupport(urlString)
        val concurrentChunks = repository.getSettingsConcurrent().coerceIn(1, 16)

        if (totalBytes > 1024 * 1024 && supportsRanges && concurrentChunks > 1) {
            // High speed Chunked/Multi-segment download
            val chunkSize = totalBytes / concurrentChunks
            val jobs = mutableListOf<Deferred<Boolean>>()
            
            val tempFiles = List(concurrentChunks) { index ->
                File(path + "_" + id + ".part$index")
            }

            // High precision Resume Support: seed downloadedBytes with the sums of already existing part files
            val initialBytes = tempFiles.sumOf { if (it.exists()) it.length() else 0L }
            val downloadedBytes = AtomicLong(initialBytes)
            val startTime = System.currentTimeMillis()
            val lastUpdate = AtomicLong(System.currentTimeMillis())
            val lastSpeedBytes = AtomicLong(initialBytes)

            coroutineScope {
                for (i in 0 until concurrentChunks) {
                    val startByte = i * chunkSize
                    val endByte = if (i == concurrentChunks - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
                    
                    val job = async(Dispatchers.IO) {
                        var chunkSuccess = false
                        var chunkRetries = 0
                        val partFile = tempFiles[i]
                        
                        while (chunkRetries < 3 && !chunkSuccess && !isStopped) {
                            val existingLength = if (partFile.exists()) partFile.length() else 0L
                            val currentStartByte = startByte + existingLength

                            // If this chunk has already completed downloading, skip
                            if (currentStartByte > endByte) {
                                chunkSuccess = true
                                break
                            }

                            try {
                                val request = createRequestBuilder(urlString)
                                    .addHeader("Range", "bytes=$currentStartByte-$endByte")
                                    .build()

                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful || response.code == 206) {
                                        val body = response.body ?: throw Exception("Null body")
                                        val stream = body.byteStream()
                                        
                                        RandomAccessFile(partFile, "rw").use { raf ->
                                            raf.seek(existingLength) // Resume precisely where it left off
                                            val buffer = ByteArray(65536)
                                            var bytesRead: Int
                                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                                if (isStopped) {
                                                    throw CancellationException("Download stopped")
                                                }
                                                raf.write(buffer, 0, bytesRead)
                                                val currentlyDownloaded = downloadedBytes.addAndGet(bytesRead.toLong())
                                                
                                                // Throttle progress and speed updates to prevent UI stutter and database write lock-up
                                                val now = System.currentTimeMillis()
                                                val lastVal = lastUpdate.get()
                                                if (now - lastVal > 700) {
                                                    if (lastUpdate.compareAndSet(lastVal, now)) {
                                                        val prevBytes = lastSpeedBytes.getAndSet(currentlyDownloaded)
                                                        val durationSec = (now - lastVal) / 1000.0
                                                        val speedBps = if (durationSec > 0) (currentlyDownloaded - prevBytes) / durationSec else 0.0
                                                        val progress = currentlyDownloaded.toFloat() / totalBytes
                                                        
                                                        val speedStr = formatSpeed(speedBps)
                                                        val timeLeftStr = formatTimeLeft(totalBytes - currentlyDownloaded, speedBps)
                                                        
                                                        repository.updateProgress(
                                                            id,
                                                            currentlyDownloaded,
                                                            totalBytes,
                                                            progress,
                                                            speedStr,
                                                            timeLeftStr,
                                                            DownloadStatus.DOWNLOADING
                                                        )
                                                        
                                                        updateNotificationProgress((progress * 100).toInt(), speedStr)
                                                    }
                                                }
                                            }
                                        }
                                        chunkSuccess = true
                                    } else {
                                        chunkRetries++
                                        delay(1000)
                                    }
                                }
                            } catch (e: Exception) {
                                chunkRetries++
                                delay(1000)
                            }
                        }
                        chunkSuccess
                    }
                    jobs.add(job)
                }

                val results = jobs.awaitAll()
                if (results.all { it }) {
                    // Combine part files
                    mergePartFiles(tempFiles, file)
                } else {
                    throw Exception("One or more chunks failed to download")
                }
            }
        } else {
            // Fallback: Default Stream Downloader
            val request = createRequestBuilder(urlString).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Response failing: ${response.code}")
                val body = response.body ?: throw Exception("Null stream body")
                val stream = body.byteStream()
                val finalTotalBytes = if (totalBytes > 0) totalBytes else body.contentLength()

                file.outputStream().use { fos ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    var totalRead = 0L
                    val startTime = System.currentTimeMillis()
                    var lastUpdate = System.currentTimeMillis()

                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) throw CancellationException("Worker Interrupted")
                        fos.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 600) {
                            lastUpdate = now
                            val elapsedSec = (now - startTime) / 1000.0
                            val speedBps = if (elapsedSec > 0) totalRead / elapsedSec else 0.0
                            val progress = if (finalTotalBytes > 0) totalRead.toFloat() / finalTotalBytes else 0.0f
                            
                            val speedStr = formatSpeed(speedBps)
                            val timeLeftStr = if (finalTotalBytes > 0) formatTimeLeft(finalTotalBytes - totalRead, speedBps) else "Calc..."
                            
                            repository.updateProgress(
                                id,
                                totalRead,
                                finalTotalBytes,
                                progress,
                                speedStr,
                                timeLeftStr,
                                DownloadStatus.DOWNLOADING
                            )
                            updateNotificationProgress((progress * 100).toInt(), speedStr)
                        }
                    }
                }
            }
        }
    }

    /**
     * Advanced HLS Video Parser & Progressive Downloader for `.m3u8`
     */
    private suspend fun downloadHlsStream(id: String, m3u8Url: String, path: String) {
        // Fetch raw m3u8 playlist index
        val request = createRequestBuilder(m3u8Url).build()
        val playlistContent = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("m3u8 index fetch failed: ${response.code}")
            response.body?.string() ?: throw Exception("Empty m3u8 payload")
        }

        // Enumerate list of segment references (usually .ts or similar segments)
        val segments = mutableListOf<String>()
        playlistContent.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segments.add(trimmed)
            }
        }

        if (segments.isEmpty()) {
            throw Exception("No video TS segments enumerated inside playlist config")
        }

        val totalSegments = segments.size
        val finalFile = File(path)
        val parentDir = finalFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        // Temporary folder for TS chunks to support full, reliable resume
        val segmentsDir = File(path + "_" + id + "_temp_segments")
        if (!segmentsDir.exists()) {
            segmentsDir.mkdirs()
        }

        val baseUrl = m3u8Url.substringBeforeLast("/")
        val startTime = System.currentTimeMillis()
        val lastUpdate = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

        // Calculate size of already downloaded fragments
        val segmentFiles = List(totalSegments) { index ->
            File(segmentsDir, "seg_$index.ts")
        }

        var previouslyDownloadedBytes = 0L
        segmentFiles.forEach { f ->
            if (f.exists()) {
                previouslyDownloadedBytes += f.length()
            }
        }

        val completedSegments = java.util.concurrent.atomic.AtomicInteger(0)
        val completedSegmentCount = segmentFiles.count { it.exists() && it.length() > 0 }
        completedSegments.set(completedSegmentCount)

        val totalBytesDownloaded = java.util.concurrent.atomic.AtomicLong(previouslyDownloadedBytes)
        val lastSpeedBytes = java.util.concurrent.atomic.AtomicLong(previouslyDownloadedBytes)

        // Parallel HLS segment downloading with concurrency limit of 5
        val semaphore = Semaphore(5)

        coroutineScope {
            val jobs = List(totalSegments) { index ->
                async(Dispatchers.IO) {
                    if (isStopped) return@async false

                    val segmentFile = segmentFiles[index]
                    var segmentUrl = segments[index]
                    if (!segmentUrl.startsWith("http://") && !segmentUrl.startsWith("https://")) {
                        segmentUrl = "$baseUrl/$segmentUrl"
                    }

                    // Skip if this specific segment is already fully downloaded
                    if (segmentFile.exists() && segmentFile.length() > 0L) {
                        return@async true
                    }

                    var success = false
                    semaphore.withPermit {
                        var tryCount = 0
                        while (tryCount < 3 && !success && !isStopped) {
                            try {
                                tryCount++
                                val segRequest = createRequestBuilder(segmentUrl).build()
                                client.newCall(segRequest).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val body = response.body ?: throw Exception("Null TS chunk")
                                        val stream = body.byteStream()
                                        
                                        segmentFile.outputStream().use { fos ->
                                            val buffer = ByteArray(65536)
                                            var bytesRead: Int
                                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                                if (isStopped) throw CancellationException("HLS session paused")
                                                fos.write(buffer, 0, bytesRead)
                                                val currentBytes = totalBytesDownloaded.addAndGet(bytesRead.toLong())
                                                
                                                // Throttle progress updates to database and UI
                                                val now = System.currentTimeMillis()
                                                val lastVal = lastUpdate.get()
                                                if (now - lastVal > 800) {
                                                    if (lastUpdate.compareAndSet(lastVal, now)) {
                                                        val currentCompleted = completedSegments.get()
                                                        val prevBytes = lastSpeedBytes.getAndSet(currentBytes)
                                                        val durationSec = (now - lastVal) / 1000.0
                                                        val speedBps = if (durationSec > 0) (currentBytes - prevBytes) / durationSec else 0.0
                                                        
                                                        val progress = currentCompleted.toFloat() / totalSegments
                                                        val speedStr = formatSpeed(speedBps)
                                                        
                                                        val remainingBytes = if (progress > 0) ((currentBytes / progress) - currentBytes).toLong() else 0L
                                                        val timeLeftStr = formatTimeLeft(remainingBytes, speedBps)

                                                        repository.updateProgress(
                                                            id,
                                                            currentBytes,
                                                            if (progress > 0) (currentBytes / progress).toLong() else 0L,
                                                            progress,
                                                            speedStr,
                                                            timeLeftStr,
                                                            DownloadStatus.DOWNLOADING
                                                        )
                                                        updateNotificationProgress((progress * 100).toInt(), speedStr)
                                                    }
                                                }
                                            }
                                        }
                                        success = true
                                    }
                                }
                            } catch (e: Exception) {
                                if (isStopped) throw CancellationException("HLS stopped")
                                delay(500)
                            }
                        }
                    }

                    if (success) {
                        completedSegments.getAndIncrement()
                        true
                    } else {
                        false
                    }
                }
            }

            val results = jobs.awaitAll()
            if (!results.all { it } && !isStopped) {
                throw Exception("Some HLS TS segments failed to download completely")
            }
        }

        if (!isStopped) {
            // Sequence merge in a separate block to guarantee stream consolidation
            finalFile.outputStream().use { finalFos ->
                segmentFiles.forEach { segmentFile ->
                    if (segmentFile.exists()) {
                        segmentFile.inputStream().use { input ->
                            input.copyTo(finalFos)
                        }
                    }
                }
            }
            // Safely wipe temp segments to clean space
            segmentFiles.forEach { it.delete() }
            segmentsDir.delete()
        }
    }

    private fun mergePartFiles(parts: List<File>, targetFile: File) {
        targetFile.outputStream().use { output ->
            parts.forEach { part ->
                part.inputStream().use { input ->
                    input.copyTo(output)
                }
                part.delete() // Clear temp chunks
            }
        }
    }

    private fun createRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        builder.addHeader("User-Agent", workerUserAgent)
        workerReferer?.let {
            builder.addHeader("Referer", it)
        }
        return builder
    }

    private fun checkRangeSupport(url: String): Boolean {
        return try {
            val request = createRequestBuilder(url).head().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful && response.header("Accept-Ranges") == "bytes"
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchContentLength(url: String): Long {
        return try {
            val request = createRequestBuilder(url).head().build()
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

    private fun formatSpeed(bytesPerSec: Double): String {
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.2f MB/s", mb)
            kb >= 1.0 -> String.format("%.1f KB/s", kb)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun formatTimeLeft(remainingBytes: Long, bytesPerSec: Double): String {
        if (bytesPerSec <= 0 || remainingBytes <= 0) return "Calculating..."
        val seconds = (remainingBytes / bytesPerSec).toLong()
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0) {
            String.format("%02d:%02d mins", min, sec)
        } else {
            String.format("%d secs", sec)
        }
    }

    // Foreground Service Notification methods
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "IDM Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status of files currently downloading"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int, message: String): ForegroundInfo {
        val stopPendingIntent = androidx.work.multiprocess.RemoteWorkManager.getInstance(appContext)
            // Just general pending layouts
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(downloadTitle)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()

        return ForegroundInfo(notificationId, notification)
    }

    private fun updateNotificationProgress(progress: Int, speed: String) {
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(downloadTitle)
            .setContentText("Downloading... $progress% | $speed")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun updateFinishedNotification(isSuccess: Boolean) {
        val message = if (isSuccess) "Download completed successfully" else "Download aborted / failed"
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(downloadTitle)
            .setContentText(message)
            .setSmallIcon(
                if (isSuccess) android.R.drawable.stat_sys_download_done 
                else android.R.drawable.stat_notify_error
            )
            .setProgress(0, 0, false)
            .setOngoing(false)
            .build()
        notificationManager.notify(notificationId, notification)
    }
}
