@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.challenge.videorecord.ui.screen.videodetail

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.challenge.videorecord.ui.UploadStatus
import com.challenge.videorecord.ui.VideoUi
import com.challenge.videorecord.ui.components.ThumbnailImage
import com.challenge.videorecord.ui.components.TopBar
import com.challenge.videorecord.ui.theme.VideoUploadTheme
import com.challenge.videorecord.ui.tint
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VideoDetailScreen(
    id: Long,
    onNavigateBack: () -> Unit,
    viewModel: VideoDetailViewModel = koinViewModel<VideoDetailViewModel>(key = id.toString()) {
        parametersOf(id)
    },
) {
    val video by viewModel.video.collectAsStateWithLifecycle()
    VideoDetailContent(
        video = video,
        onUpload = viewModel::startUpload,
        onCancel = viewModel::cancelUpload,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun VideoDetailContent(
    video: VideoUi?,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    if (video == null) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            TopBar(onNavigateBack = onNavigateBack)
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            title = video.displayName,
            onNavigateBack = onNavigateBack,
        )
        VideoPlayer(
            uri = video.uri,
            thumbnailUri = video.thumbnailUri,
            aspectRatio = video.aspectRatio,
        )
        Metadata(
            video = video,
            onUpload = onUpload,
            onCancel = onCancel,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Metadata(
    video: VideoUi,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Recorded ${video.recordedAt}",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Upload Status",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = video.uploadStatus.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = video.uploadStatus.tint,
                    )
                    Icon(
                        imageVector = video.uploadStatus.icon,
                        contentDescription = video.uploadStatus.label,
                        tint = video.uploadStatus.tint,
                    )
                }
                when (video.uploadStatus) {
                    UploadStatus.RECORDED -> {
                        Button(onClick = onUpload) {
                            Text(text = "Upload")
                        }
                    }

                    UploadStatus.UPLOADING -> {
                        LinearProgressIndicator(
                            progress = { video.uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = onCancel) {
                            Text(text = "Cancel")
                        }
                    }

                    UploadStatus.UPLOADED -> {}

                    UploadStatus.FAILED -> {
                        Button(onClick = onUpload) {
                            Text(text = "Retry")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    uri: String,
    thumbnailUri: String?,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier
            .heightIn(max = 300.dp)
            .aspectRatio(aspectRatio)
            .background(Color.Black),
    ) {
        var started by remember(uri) { mutableStateOf(false) }

        val playPauseState: PlayPauseButtonState
        if (!LocalInspectionMode.current) {
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) {
                onDispose { player.release() }
            }
            LaunchedEffect(player, uri) {
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
            }

            playPauseState = rememberPlayPauseButtonState(player)
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            playPauseState = rememberPlayPauseButtonState(null)
        }

        if (!started && thumbnailUri != null) {
            ThumbnailImage(
                modifier = Modifier.fillMaxSize(),
                url = thumbnailUri,
            )
        }

        IconButton(
            onClick = {
                started = true
                playPauseState.onClick()
            },
            enabled = playPauseState.isEnabled,
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
        ) {
            Icon(
                imageVector = if (playPauseState.showPlay) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (playPauseState.showPlay) "Play" else "Pause",
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Preview
@Composable
fun VideoDetailPreview(@PreviewParameter(VideoDetailPreviewProvider::class) video: VideoUi) {
    VideoUploadTheme {
        VideoDetailContent(
            video = video,
            onUpload = {},
            onCancel = {},
            onNavigateBack = {},
        )
    }
}
