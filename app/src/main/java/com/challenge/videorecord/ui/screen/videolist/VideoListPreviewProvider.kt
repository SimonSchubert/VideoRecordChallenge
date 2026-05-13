package com.challenge.videorecord.ui.screen.videolist

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.challenge.videorecord.db.Video
import com.challenge.videorecord.ui.UploadStatus
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.dateTimeFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.text.toLong

class VideoListPreviewProvider : PreviewParameterProvider<ImmutableList<VideoUi>> {
    override val values =
        sequenceOf(
            UploadStatus.entries.mapIndexed { index, status ->
                VideoUi(
                    id = index.toLong(),
                    uri = "u",
                    displayName = "VIDEO 00$index",
                    recordedAt = dateTimeFormat.format(System.currentTimeMillis()),
                    uploadStatus = status,
                    uploadProgress = 33,
                    thumbnailUri = "t",
                    aspectRatio = 16f / 9f,
                )
            }.toImmutableList(),
            persistentListOf(),
        )
}
