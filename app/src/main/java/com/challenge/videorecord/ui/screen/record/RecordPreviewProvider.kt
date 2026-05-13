package com.challenge.videorecord.ui.screen.record

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.challenge.videorecord.ui.UploadStatus
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.dateTimeFormat

class RecordPreviewProvider : PreviewParameterProvider<RecordState> {
    override val values = sequenceOf(
        RecordState.Recording,
        RecordState.PendingDecision("u", "d", 0L),
        RecordState.Process,
        RecordState.Idle,
    )
}
