package com.challenge.videorecord.ui

import androidx.compose.runtime.Immutable
import com.challenge.videorecord.db.Video
import java.text.DateFormat
import java.util.Date

@Immutable
data class VideoUi(
    val id: Long,
    val displayName: String,
    val uri: String,
    val thumbnailUri: String?,
    val recordedAt: String,
    val uploadStatus: UploadStatus,
    val uploadProgress: Int,
    val aspectRatio: Float,
)

fun Video.toUi(): VideoUi {
    val w = width?.toInt()
    val h = height?.toInt()
    val ratio = if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h else null
    return VideoUi(
        id = id,
        displayName = displayName,
        uri = uri,
        thumbnailUri = thumbnailUri,
        recordedAt = dateTimeFormat.format(Date(createdAt)),
        uploadStatus = UploadStatus.fromDb(uploadStatus),
        uploadProgress = uploadProgress.toInt(),
        aspectRatio = ratio ?: (16f / 9f),
    )
}

private val dateTimeFormat: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
