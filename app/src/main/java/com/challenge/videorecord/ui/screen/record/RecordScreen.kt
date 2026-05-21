package com.challenge.videorecord.ui.screen.record

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.challenge.videorecord.findActivity
import com.challenge.videorecord.hasPermission
import com.challenge.videorecord.openAppSettings
import com.challenge.videorecord.ui.components.TopBar
import com.challenge.videorecord.ui.components.rememberDebouncedClick
import com.challenge.videorecord.ui.theme.VideoUploadTheme
import org.koin.compose.viewmodel.koinViewModel
import kotlin.Unit

@Composable
fun RecordScreen(
    viewModel: RecordViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    RecordContent(
        state = state,
        onStartRecording = viewModel::startRecording,
        onStopRecording = viewModel::stopRecording,
        onDiscardRecording = viewModel::discardRecording,
        onSaveRecording = viewModel::saveRecording,
        onNavigateBack = onNavigateBack,
    )

    // lock orientation to portrait for camera screen. make recordings survive orientation changes is fiddly and not best practice
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        context.findActivity()?.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
    }
    DisposableEffect(Unit) {
        onDispose { context.findActivity()?.requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

private val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@Composable
private fun RecordContent(
    state: RecordState,
    onStartRecording: (Context, VideoCapture<Recorder>) -> Unit,
    onStopRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    onSaveRecording: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermissions by remember {
        mutableStateOf(CAMERA_PERMISSIONS.all(context::hasPermission))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermissions = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) permissionLauncher.launch(CAMERA_PERMISSIONS)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermissions = CAMERA_PERMISSIONS.all(context::hasPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasPermissions) {
        CameraRecorder(
            state = state,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onDiscardRecording = onDiscardRecording,
            onSaveRecording = onSaveRecording,
            onNavigateBack = onNavigateBack,
        )
    } else {
        PermissionGate(
            onNavigateBack = onNavigateBack,
            onRequestPermission = { permissionLauncher.launch(CAMERA_PERMISSIONS) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

@Composable
private fun PermissionGate(
    onNavigateBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        TopBar(
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding(),
            onNavigateBack = onNavigateBack,
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val activity = LocalContext.current.findActivity()
            val canRequest = (
                activity != null && CAMERA_PERMISSIONS.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                }
                )
            Text("Camera and microphone permissions are required.")
            if (canRequest) {
                Button(onClick = rememberDebouncedClick(onClick = onRequestPermission)) { Text("Grant permission") }
            } else {
                Button(onClick = rememberDebouncedClick(onClick = onOpenSettings)) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun CameraRecorder(
    state: RecordState,
    onStartRecording: (Context, VideoCapture<Recorder>) -> Unit,
    onStopRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    onSaveRecording: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview =
            Preview.Builder().build().also { it.setSurfaceProvider { surfaceRequest = it } }
        // cap quality at 1080p, accept any lower
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityThan(Quality.SD),
        )
        val recorder =
            Recorder.Builder().setQualitySelector(qualitySelector).build()
        videoCapture = VideoCapture.withOutput(recorder)
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            videoCapture,
        )
    }

    // stop the camera and discard
    BackHandler(enabled = state is RecordState.Recording) {
        // could trigger user confirmation
        onDiscardRecording()
    }

    Box(Modifier.fillMaxSize()) {
        surfaceRequest?.let { CameraXViewfinder(it, Modifier.fillMaxSize()) }

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding(),
            onNavigateBack = onNavigateBack,
        )

        CameraActions(
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding(),
            state = state,
            videoCapture = videoCapture,
            onStart = { capture -> onStartRecording(context, capture) },
            onStop = onStopRecording,
            onSave = onSaveRecording,
            onDiscard = onDiscardRecording,
        )
    }
}

@Composable
private fun CameraActions(
    modifier: Modifier = Modifier,
    state: RecordState,
    videoCapture: VideoCapture<Recorder>?,
    onStart: (VideoCapture<Recorder>) -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is RecordState.Idle -> {
                if (videoCapture != null) {
                    val onRecordClick = rememberDebouncedClick { onStart(videoCapture) }
                    Button(onClick = onRecordClick) { Text("Record") }
                } else {
                    CircularProgressIndicator()
                }
            }

            is RecordState.Recording -> Button(onClick = rememberDebouncedClick(onClick = onStop)) { Text("Stop") }

            is RecordState.PendingDecision -> {
                Button(onClick = rememberDebouncedClick(onClick = onSave)) { Text("Save") }
                Button(onClick = rememberDebouncedClick(onClick = onDiscard)) { Text("Discard") }
            }

            RecordState.Loading -> CircularProgressIndicator()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun RecordPreview(@PreviewParameter(RecordPreviewProvider::class) state: RecordState) {
    VideoUploadTheme {
        RecordContent(
            state = state,
            onStartRecording = { _, _ -> },
            onStopRecording = {},
            onDiscardRecording = {},
            onSaveRecording = {},
            onNavigateBack = {},
        )
    }
}
