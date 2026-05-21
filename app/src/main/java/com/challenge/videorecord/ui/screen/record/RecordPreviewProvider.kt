package com.challenge.videorecord.ui.screen.record

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class RecordPreviewProvider : PreviewParameterProvider<RecordState> {
    override val values = sequenceOf(
        RecordState.Recording,
        RecordState.PendingDecision,
        RecordState.Loading,
        RecordState.Idle,
    )
}
