package com.challenge.videorecord.ui.screen.record

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.challenge.videorecord.data.AndroidMediaStorage
import com.challenge.videorecord.data.ThumbnailExtractor
import com.challenge.videorecord.data.VideoMetadata
import com.challenge.videorecord.data.VideoRepository
import com.challenge.videorecord.db.Video
import com.challenge.videorecord.ui.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Instrumented tests for the recording states/flows.
 *
 * Wires CameraX to a TestLifecycleOwner so we don't need an activity, then checks
 * MediaStore to see if the save/discard paths actually do the right thing.
 *
 * Needs a back camera. Timeouts are generous - first cold camera start on the
 * emulator can take a few seconds.
 */
@RunWith(AndroidJUnit4::class)
class RecordViewModelInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    private lateinit var context: Context
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var storage: AndroidMediaStorage
    private lateinit var repo: FakeRepo
    private lateinit var viewModel: RecordViewModel
    private var testStartSec: Long = 0L

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        lifecycleOwner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        cameraProvider = ProcessCameraProvider.awaitInstance(context)
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                videoCapture,
            )
        }
        storage = AndroidMediaStorage(context)
        repo = FakeRepo()
        viewModel = RecordViewModel(repo, FakeThumbnails(), storage)
        testStartSec = System.currentTimeMillis() / 1000
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) { cameraProvider.unbindAll() }
        videosAddedSince(context, testStartSec).forEach { storage.delete(it.toString()) }
    }

    @Test
    fun startRecording_transitionsToRecording(): Unit = runBlocking {
        withContext(Dispatchers.Main) { viewModel.startRecording(context, videoCapture) }
        awaitState("Recording") { it is RecordState.Recording }

        withContext(Dispatchers.Main) { viewModel.discardRecording() }
        awaitState("Idle (after mid-record discard)") { it is RecordState.Idle }
    }

    @Test
    fun saveFlow_insertsRepoRowAndKeepsFile(): Unit = runBlocking {
        recordForAboutOneSecond()
        viewModel.saveRecording()
        awaitState("Idle (after save)") { it is RecordState.Idle }

        assertEquals(1, repo.inserts.size)
        val savedUri = repo.inserts[0].first.toUri()
        assertTrue("saved file should still exist", mediaStoreHas(context, savedUri))
    }

    @Test
    fun discardAfterStop_deletesFile(): Unit = runBlocking {
        recordForAboutOneSecond()
        withContext(Dispatchers.Main) { viewModel.discardRecording() }
        awaitState("Idle (after post-stop discard)") { it is RecordState.Idle }

        eventually(5_000) { videosAddedSince(context, testStartSec).isEmpty() }
        assertTrue("nothing should be persisted to repo", repo.inserts.isEmpty())
    }

    @Test
    fun discardDuringRecording_deletesFile(): Unit = runBlocking {
        withContext(Dispatchers.Main) { viewModel.startRecording(context, videoCapture) }
        awaitState("Recording") { it is RecordState.Recording }
        delay(0.8.seconds) // give CameraX time to write some frames

        withContext(Dispatchers.Main) { viewModel.discardRecording() }
        awaitState("Idle (after mid-record discard)") { it is RecordState.Idle }

        eventually(5_000) { videosAddedSince(context, testStartSec).isEmpty() }
    }

    private suspend fun recordForAboutOneSecond() {
        withContext(Dispatchers.Main) { viewModel.startRecording(context, videoCapture) }
        awaitState("Recording") { it is RecordState.Recording }
        delay(1.2.seconds)
        withContext(Dispatchers.Main) { viewModel.stopRecording() }
        awaitState("PendingDecision (after stop)") { it is RecordState.PendingDecision }
    }

    private suspend fun awaitState(label: String, predicate: (RecordState) -> Boolean) {
        val reached = withTimeoutOrNull(10.seconds) { viewModel.state.first(predicate) }
        if (reached == null) {
            error("Timed out waiting for $label; current state = ${viewModel.state.value}")
        }
    }

    private class FakeRepo : VideoRepository {
        val inserts = mutableListOf<Triple<String, String, Long>>()
        override fun observeAll(): Flow<List<Video>> = emptyFlow()
        override fun observeById(id: Long): Flow<Video?> = emptyFlow()
        override suspend fun insert(uri: String, displayName: String, createdAt: Long, thumbnailUri: String?, width: Int?, height: Int?) {
            inserts += Triple(uri, displayName, createdAt)
        }
        override suspend fun setUploadStatus(id: Long, status: UploadStatus) {}
        override suspend fun setUploadProgress(id: Long, progress: Int) {}
        override suspend fun resetUpload(id: Long) {}
    }

    private class FakeThumbnails : ThumbnailExtractor {
        override suspend fun extract(videoUri: String, displayName: String) = VideoMetadata("t.jpg", 100, 200)
    }
}

private fun mediaStoreHas(context: Context, uri: Uri): Boolean = context.contentResolver
    .query(uri, arrayOf(MediaStore.Video.Media._ID), null, null, null)
    ?.use { it.moveToFirst() } == true

private fun videosAddedSince(context: Context, sinceSec: Long): List<Uri> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Video.Media._ID)
    val selection = "${MediaStore.Video.Media.DATE_ADDED} >= ? AND " +
        "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'VID\\_%' ESCAPE '\\'"
    val args = arrayOf(sinceSec.toString())
    return context.contentResolver
        .query(collection, projection, selection, args, null)
        ?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            buildList {
                while (cursor.moveToNext()) {
                    add(ContentUris.withAppendedId(collection, cursor.getLong(idCol)))
                }
            }
        }
        .orEmpty()
}

private suspend fun eventually(timeoutMs: Long, predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return
        delay(0.1.seconds)
    }
    assertTrue("condition not met within ${timeoutMs}ms", predicate())
}
