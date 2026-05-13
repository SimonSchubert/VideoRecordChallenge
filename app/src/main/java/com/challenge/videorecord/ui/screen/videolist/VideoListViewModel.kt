package com.challenge.videorecord.ui.screen.videolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.challenge.videorecord.data.VideoRepository
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class VideoListViewModel(
    val videoRepository: VideoRepository,
) : ViewModel() {
    val videos: StateFlow<ImmutableList<VideoUi>> =
        videoRepository
            .observeAll()
            .map { list -> list.map { it.toUi() }.toImmutableList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())
}
