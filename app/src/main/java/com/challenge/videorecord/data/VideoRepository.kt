package com.challenge.videorecord.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.challenge.videorecord.db.Video
import com.challenge.videorecord.db.VideoDatabase
import com.challenge.videorecord.ui.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface VideoRepository {
    fun observeAll(): Flow<List<Video>>
    fun observeById(id: Long): Flow<Video?>
    suspend fun allUris(): List<String>
    suspend fun insert(uri: String, displayName: String, createdAt: Long, thumbnailUri: String?, width: Int?, height: Int?)
    suspend fun setUploadStatus(id: Long, status: UploadStatus)
    suspend fun setUploadProgress(id: Long, progress: Int)
    suspend fun resetUpload(id: Long)
}

class DefaultVideoRepository(private val db: VideoDatabase) : VideoRepository {
    init {
        // mark UPLOADING videos as FAILED (app closed during upload)
        db.videoQueries.failInterruptedUploads()
    }

    override fun observeAll(): Flow<List<Video>> = db.videoQueries.selectAll().asFlow().mapToList(Dispatchers.IO)

    override fun observeById(id: Long): Flow<Video?> = db.videoQueries.selectById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    override suspend fun allUris(): List<String> = withContext(Dispatchers.IO) {
        db.videoQueries.selectAllUris().executeAsList()
    }

    override suspend fun insert(uri: String, displayName: String, createdAt: Long, thumbnailUri: String?, width: Int?, height: Int?) {
        withContext(Dispatchers.IO) {
            db.videoQueries.insert(uri, displayName, createdAt, thumbnailUri, width?.toLong(), height?.toLong())
        }
    }

    override suspend fun setUploadStatus(id: Long, status: UploadStatus) {
        withContext(Dispatchers.IO) { db.videoQueries.updateUploadStatus(status.name, id) }
    }

    override suspend fun setUploadProgress(id: Long, progress: Int) {
        withContext(Dispatchers.IO) { db.videoQueries.updateUploadProgress(progress.toLong(), id) }
    }

    override suspend fun resetUpload(id: Long) {
        withContext(Dispatchers.IO) { db.videoQueries.resetUpload(id) }
    }
}
