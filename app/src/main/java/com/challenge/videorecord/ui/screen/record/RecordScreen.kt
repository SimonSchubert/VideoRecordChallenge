package com.challenge.videorecord.ui.screen.record

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.challenge.videorecord.findActivity
import com.challenge.videorecord.hasPermission
import com.challenge.videorecord.openAppSettings
import com.challenge.videorecord.ui.components.TopBar
import com.challenge.videorecord.ui.theme.VideoUploadTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RecordScreen(
    viewModel: RecordViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    RecordContent(
        state = state,
        onRecordingStarted = viewModel::onRecordingStarted,
        onStopRequested = viewModel::onStopRequested,
        onRecordingFinalized = viewModel::onRecordingFinalized,
        onRecordingError = viewModel::onRecordingError,
        onAbortRecording = viewModel::abortRecording,
        onSave = viewModel::save,
        onDiscard = viewModel::discard,
        onNavigateBack = onNavigateBack,
    )
}

private val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@Composable
private fun RecordContent(
    state: RecordState,
    onRecordingStarted: (stop: () -> Unit) -> Unit,
    onStopRequested: () -> Unit,
    onRecordingFinalized: (uri: String, displayName: String, createdAt: Long) -> Unit,
    onRecordingError: () -> Unit,
    onAbortRecording: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
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
            onRecordingStarted = onRecordingStarted,
            onStopRequested = onStopRequested,
            onRecordingFinalized = onRecordingFinalized,
            onRecordingError = onRecordingError,
            onAbortRecording = onAbortRecording,
            onSave = onSave,
            onDiscard = onDiscard,
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
                Button(onClick = onRequestPermission) { Text("Grant permission") }
            } else {
                Button(onClick = onOpenSettings) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun CameraRecorder(
    state: RecordState,
    onRecordingStarted: (stop: () -> Unit) -> Unit,
    onStopRequested: () -> Unit,
    onRecordingFinalized: (uri: String, displayName: String, createdAt: Long) -> Unit,
    onRecordingError: () -> Unit,
    onAbortRecording: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
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
        val recorder =
            Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build()
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
        onAbortRecording()
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
            onRecord = {
                val recording = startRecording(
                    context = context,
                    videoCapture = videoCapture,
                    onFinalized = onRecordingFinalized,
                    onError = onRecordingError,
                )
                if (recording != null) {
                    onRecordingStarted { recording.stop() }
                } else {
                    onRecordingError()
                }
            },
            onStop = onStopRequested,
            onSave = onSave,
            onDiscard = onDiscard,
        )
    }
}

@Composable
private fun CameraActions(
    modifier: Modifier = Modifier,
    state: RecordState,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is RecordState.Idle -> Button(onClick = onRecord) { Text("Record") }

            is RecordState.Recording -> Button(onClick = onStop) { Text("Stop") }

            is RecordState.PendingDecision -> {
                Button(onClick = onSave) { Text("Save") }
                Button(onClick = onDiscard) { Text("Discard") }
            }

            RecordState.Process -> CircularProgressIndicator()
        }
    }
}

private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    onFinalized: (uri: String, displayName: String, createdAt: Long) -> Unit,
    onError: () -> Unit,
): Recording? {
    val displayName = "VID_${System.currentTimeMillis()}.mp4"
    val opts = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, displayName) })
        .build()
    return videoCapture?.output
        ?.prepareRecording(context, opts)
        ?.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    onError()
                } else {
                    onFinalized(event.outputResults.outputUri.toString(), displayName, System.currentTimeMillis())
                }
            }
        }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun RecordPreview(@PreviewParameter(RecordPreviewProvider::class) state: RecordState) {
    VideoUploadTheme {
        RecordContent(
            state = state,
            onRecordingStarted = {},
            onStopRequested = {},
            onRecordingFinalized = { _, _, _ -> },
            onRecordingError = {},
            onAbortRecording = {},
            onSave = {},
            onDiscard = {},
            onNavigateBack = {},
        )
    }
}
