package com.challenge.videorecord.ui.screen.record

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challenge.videorecord.data.MediaStorage
import com.challenge.videorecord.data.ThumbnailExtractor
import com.challenge.videorecord.data.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface RecordState {
    data object Idle : RecordState
    data object Recording : RecordState
    data object Process : RecordState
    data class PendingDecision(val uri: String, val displayName: String, val createdAt: Long) : RecordState
}

class RecordViewModel(
    private val repo: VideoRepository,
    private val thumbnails: ThumbnailExtractor,
    private val storage: MediaStorage,
) : ViewModel() {
    private val _state = MutableStateFlow<RecordState>(RecordState.Idle)
    val state: StateFlow<RecordState> = _state.asStateFlow()

    private var stopActiveRecording: (() -> Unit)? = null
    private var discardOnFinalize: Boolean = false

    fun onRecordingStarted(stop: () -> Unit) {
        stopActiveRecording = stop
        discardOnFinalize = false
        _state.value = RecordState.Recording
    }

    fun onStopRequested() {
        stopActiveRecording?.invoke()
        _state.value = RecordState.Process
    }

    fun onRecordingError() {
        stopActiveRecording = null
        discardOnFinalize = false
        _state.value = RecordState.Idle
    }

    fun onRecordingFinalized(uri: String, displayName: String, createdAt: Long) {
        stopActiveRecording = null
        if (discardOnFinalize) {
            discardOnFinalize = false
            _state.value = RecordState.Idle
            viewModelScope.launch { storage.delete(uri) }
        } else {
            _state.value = RecordState.PendingDecision(uri, displayName, createdAt)
        }
    }

    fun abortRecording() {
        if (_state.value !is RecordState.Recording) return
        discardOnFinalize = true
        _state.value = RecordState.Process
        stopActiveRecording?.invoke()
    }

    fun save() {
        val pending = _state.value as? RecordState.PendingDecision ?: return
        _state.value = RecordState.Process
        viewModelScope.launch {
            val meta = thumbnails.extract(pending.uri, pending.displayName)
            repo.insert(pending.uri, pending.displayName, pending.createdAt, meta.thumbnailUri, meta.width, meta.height)
            _state.value = RecordState.Idle
        }
    }

    fun discard() {
        val pending = _state.value as? RecordState.PendingDecision ?: return
        _state.value = RecordState.Idle
        viewModelScope.launch { storage.delete(pending.uri) }
    }

    override fun onCleared() {
        stopActiveRecording?.invoke()
        stopActiveRecording = null
    }
}
