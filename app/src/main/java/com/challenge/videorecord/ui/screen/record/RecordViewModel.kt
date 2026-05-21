package com.challenge.videorecord.ui.screen.record

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challenge.videorecord.data.MediaStorage
import com.challenge.videorecord.data.ThumbnailExtractor
import com.challenge.videorecord.data.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface RecordState {
    data object Idle : RecordState
    data object Recording : RecordState
    data object Loading : RecordState
    data object PendingDecision : RecordState
}

data class CurrentRecordingMeta(
    val uri: String,
    val displayName: String,
    val finishedAt: Long,
)

class RecordViewModel(
    private val repo: VideoRepository,
    private val thumbnails: ThumbnailExtractor,
    private val storage: MediaStorage,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow<RecordState>(RecordState.Idle)
    val state: StateFlow<RecordState> = _state.asStateFlow()

    private var currentRecording: Recording? = null
    private var currentRecordingMetaData: CurrentRecordingMeta? = null
    private var discardOnFinalize: Boolean = false

    fun startRecording(
        context: Context,
        videoCapture: VideoCapture<Recorder>,
    ) {
        _state.value = RecordState.Loading
        discardOnFinalize = false

        val displayName = "VID_${System.currentTimeMillis()}.mp4"
        val opts = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, displayName) })
            .build()
        currentRecording = videoCapture.output
            .prepareRecording(context, opts)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _state.value = RecordState.Recording
                    }

                    is VideoRecordEvent.Finalize -> {
                        currentRecording = null
                        val discarded = discardOnFinalize
                        discardOnFinalize = false

                        if (event.hasError()) {
                            // could show error state
                            _state.value = RecordState.Idle
                        } else if (discarded) {
                            val uri = event.outputResults.outputUri.toString()
                            appScope.launch(Dispatchers.IO) { storage.delete(uri) }
                            _state.value = RecordState.Idle
                        } else {
                            currentRecordingMetaData = CurrentRecordingMeta(
                                uri = event.outputResults.outputUri.toString(),
                                displayName = displayName,
                                finishedAt = System.currentTimeMillis(),
                            )
                            _state.value = RecordState.PendingDecision
                        }
                    }
                }
            }
    }

    fun stopRecording() {
        currentRecording?.stop()
    }

    fun saveRecording() {
        val metaData = currentRecordingMetaData ?: return // edge case and show error?

        _state.value = RecordState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val meta = thumbnails.extract(metaData.uri, metaData.displayName)
            repo.insert(metaData.uri, metaData.displayName, metaData.finishedAt, meta.thumbnailUri, meta.width, meta.height)
            currentRecordingMetaData = null
            _state.value = RecordState.Idle // could show success state/redirect to video detail
        }
    }

    fun discardRecording() {
        val recording = currentRecording
        if (recording != null) {
            discardOnFinalize = true
            recording.stop()
            // finalize callback deletes file
            return
        }
        currentRecordingMetaData?.let { meta ->
            currentRecordingMetaData = null
            appScope.launch(Dispatchers.IO) { storage.delete(meta.uri) }
        }
        _state.value = RecordState.Idle
    }

    override fun onCleared() {
        discardRecording()
    }
}
