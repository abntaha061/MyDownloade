package com.example.data.repository

import android.content.Context
import androidx.work.*
import com.example.data.database.DownloadDao
import com.example.data.database.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.worker.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    val allDownloads: Flow<List<DownloadTask>> = downloadDao.getAllDownloads().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getDownloadById(id: String): DownloadTask? {
        return downloadDao.getDownloadById(id)?.toDomain()
    }

    suspend fun insertDownload(task: DownloadTask) {
        downloadDao.insertDownload(DownloadEntity.fromDomain(task))
    }

    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        progress: Float,
        speed: String,
        timeLeft: String,
        status: DownloadStatus
    ) {
        downloadDao.updateProgress(
            id,
            downloadedBytes,
            totalBytes,
            progress,
            speed,
            timeLeft,
            status.name
        )
    }

    suspend fun updateStatus(id: String, status: DownloadStatus, error: String? = null) {
        downloadDao.updateStatus(id, status.name, error)
    }

    suspend fun deleteDownload(id: String) {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(id)
            WorkManager.getInstance(context).cancelUniqueWork(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val task = getDownloadById(id)
        if (task != null) {
            try {
                val file = File(task.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        downloadDao.deleteDownloadById(id)
    }

    // Settings Configuration (SharedPreferences)
    fun getSettingsConcurrent(): Int {
        return context.getSharedPreferences("idm_settings", Context.MODE_PRIVATE)
            .getInt("concurrent_downloads", 3)
    }

    fun getSettingsWifiOnly(): Boolean {
        return context.getSharedPreferences("idm_settings", Context.MODE_PRIVATE)
            .getBoolean("wifi_only", false)
    }

    fun getSettingsDefaultQuality(): String {
        return context.getSharedPreferences("idm_settings", Context.MODE_PRIVATE)
            .getString("default_quality", "720p") ?: "720p"
    }

    fun getSettingsSaveDir(): String {
        val prefs = context.getSharedPreferences("idm_settings", Context.MODE_PRIVATE)
        var path = prefs.getString("save_directory", null)
        if (path == null) {
            val defaultDir = context.getExternalFilesDir(null)?.absolutePath 
                ?: context.filesDir.absolutePath
            path = defaultDir
            prefs.edit().putString("save_directory", path).apply()
        }
        return path
    }

    fun saveSettings(concurrent: Int, wifiOnly: Boolean, quality: String, saveDir: String) {
        context.getSharedPreferences("idm_settings", Context.MODE_PRIVATE).edit()
            .putInt("concurrent_downloads", concurrent)
            .putBoolean("wifi_only", wifiOnly)
            .putString("default_quality", quality)
            .putString("save_directory", saveDir)
            .apply()
    }

    suspend fun pauseDownload(id: String) {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(id)
            WorkManager.getInstance(context).cancelUniqueWork(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateStatus(id, DownloadStatus.PAUSED)
    }

    suspend fun startDownload(task: DownloadTask) {
        // Enforce state in Database to PENDING/DOWNLOADING
        updateStatus(task.id, DownloadStatus.PENDING)

        val wifiOnly = getSettingsWifiOnly()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString("DOWNLOAD_ID", task.id)
            .putString("DOWNLOAD_URL", task.url)
            .putString("DOWNLOAD_PATH", task.filePath)
            .putString("DOWNLOAD_TITLE", task.title)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .addTag(task.id)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            task.id,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
