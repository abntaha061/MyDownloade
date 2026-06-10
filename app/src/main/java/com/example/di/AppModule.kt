package com.example.di

import android.content.Context
import com.example.data.database.DownloadDatabase
import com.example.data.repository.DownloadRepository

object AppModule {
    private var database: DownloadDatabase? = null
    private var repository: DownloadRepository? = null

    fun getRepository(context: Context): DownloadRepository {
        return repository ?: synchronized(this) {
            val repo = DownloadRepository(
                context = context.applicationContext,
                downloadDao = getDatabase(context).downloadDao()
            )
            repository = repo
            repo
        }
    }

    private fun getDatabase(context: Context): DownloadDatabase {
        return database ?: synchronized(this) {
            val db = DownloadDatabase.getDatabase(context.applicationContext)
            database = db
            db
        }
    }
}
