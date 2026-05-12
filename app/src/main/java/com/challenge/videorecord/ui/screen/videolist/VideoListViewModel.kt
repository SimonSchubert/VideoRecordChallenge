package com.challenge.videorecord.ui.screen.videolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challenge.videorecord.data.VideoRepository
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.toUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class VideoListViewModel(
    val videoRepository: VideoRepository,
) : ViewModel() {
    val videos: StateFlow<List<VideoUi>> =
        videoRepository
            .observeAll()
            .map { list -> list.map { it.toUi() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
