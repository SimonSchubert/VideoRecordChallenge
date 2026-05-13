package com.challenge.videorecord.ui.screen.videodetail

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.challenge.videorecord.ui.UploadStatus
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.dateTimeFormat

class VideoDetailPreviewProvider : PreviewParameterProvider<VideoUi> {
    override val values = UploadStatus.entries.mapIndexed { index, status ->
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
    }.asSequence()
}
