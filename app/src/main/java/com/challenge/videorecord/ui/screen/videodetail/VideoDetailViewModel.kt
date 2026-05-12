package com.challenge.videorecord.ui.screen.videodetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challenge.videorecord.data.UploadManager
import com.challenge.videorecord.data.VideoRepository
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.toUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class VideoDetailViewModel(
    private val id: Long,
    private val videoRepository: VideoRepository,
    private val uploadManager: UploadManager,
) : ViewModel() {
    val video: StateFlow<VideoUi?> =
        videoRepository
            .observeById(id)
            .map { it?.toUi() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startUpload() = uploadManager.start(id)

    fun cancelUpload() = uploadManager.cancel(id)
}
