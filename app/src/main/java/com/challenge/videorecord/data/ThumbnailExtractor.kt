package com.challenge.videorecord.data

import android.content.Context
import android.graphics.Bitmap.CompressFormat.JPEG
import android.util.Size
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VideoMetadata(val thumbnailUri: String?, val width: Int?, val height: Int?)

interface ThumbnailExtractor {
    suspend fun extract(videoUri: String, displayName: String): VideoMetadata
}

interface MediaStorage {
    suspend fun delete(uri: String)
}

class AndroidThumbnailExtractor(private val context: Context) : ThumbnailExtractor {
    override suspend fun extract(videoUri: String, displayName: String) = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = context.contentResolver.loadThumbnail(videoUri.toUri(), Size(640, 360), null)
            val file = File(context.filesDir.resolve("thumbnails").apply { mkdirs() }, "$displayName.jpg")
            file.outputStream().use { bmp.compress(JPEG, 85, it) }
            VideoMetadata(file.absolutePath, bmp.width, bmp.height)
        }.getOrElse { VideoMetadata(null, null, null) }
    }
}

class AndroidMediaStorage(private val context: Context) : MediaStorage {
    override suspend fun delete(uri: String) {
        withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(uri.toUri(), null, null) } }
    }
}
