package com.challenge.videorecord.ui.screen.videolist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.components.ThumbnailImage
import com.challenge.videorecord.ui.components.rememberDebouncedClick
import com.challenge.videorecord.ui.theme.VideoUploadTheme
import com.challenge.videorecord.ui.tint
import kotlinx.collections.immutable.ImmutableList
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel = koinViewModel(),
    onRecordVideo: () -> Unit,
    onVideoSelected: (Long) -> Unit,
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    VideoListContent(
        videos = videos,
        onRecordVideo = onRecordVideo,
        onVideoSelected = onVideoSelected,
    )
}

@Composable
private fun VideoListContent(
    videos: ImmutableList<VideoUi>,
    onRecordVideo: () -> Unit,
    onVideoSelected: (Long) -> Unit,
) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (videos.isEmpty()) {
            Text("No videos yet.", Modifier.align(Alignment.Center))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(videos, key = { it.id }) { video ->
                    VideoRow(
                        video = video,
                        onSelect = onVideoSelected,
                    )
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeContentPadding(),
            onClick = rememberDebouncedClick(onClick = onRecordVideo),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.secondary,
        ) {
            Icon(Icons.Filled.VideoCall, "Record video")
        }
    }
}

@Composable
private fun VideoRow(
    video: VideoUi,
    onSelect: (Long) -> Unit,
) {
    val onRowClick = rememberDebouncedClick { onSelect(video.id) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThumbnailImage(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)),
            url = video.thumbnailUri,
        )

        Column(Modifier.weight(1f)) {
            Text(video.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(video.recordedAt, style = MaterialTheme.typography.bodySmall)
        }

        Icon(
            imageVector = video.uploadStatus.icon,
            contentDescription = video.uploadStatus.label,
            tint = video.uploadStatus.tint,
        )
    }
}

@Preview
@Composable
fun VideoDetailPreview(@PreviewParameter(VideoListPreviewProvider::class) videos: ImmutableList<VideoUi>) {
    VideoUploadTheme {
        VideoListContent(
            videos = videos,
            onRecordVideo = {},
            onVideoSelected = {},
        )
    }
}
