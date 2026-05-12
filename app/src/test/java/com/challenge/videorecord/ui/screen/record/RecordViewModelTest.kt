package com.challenge.videorecord.ui.screen.record

import app.cash.turbine.test
import com.challenge.videorecord.data.MediaStorage
import com.challenge.videorecord.data.ThumbnailExtractor
import com.challenge.videorecord.data.VideoMetadata
import com.challenge.videorecord.data.VideoRepository
import com.challenge.videorecord.db.Video
import com.challenge.videorecord.ui.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {

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

    private class FakeStorage : MediaStorage {
        val deleted = mutableListOf<String>()
        override suspend fun delete(uri: String) {
            deleted += uri
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state transitions Idle to Recording to PendingDecision`() {
        val viewModel = RecordViewModel(FakeRepo(), FakeThumbnails(), FakeStorage())
        viewModel.onRecordingStarted {}
        assertEquals(RecordState.Recording, viewModel.state.value)
        viewModel.onRecordingFinalized("u", "n", 42L)
        assertEquals(RecordState.PendingDecision("u", "n", 42L), viewModel.state.value)
    }

    @Test
    fun `discard deletes media and returns to Idle`() {
        val repo = FakeRepo()
        val storage = FakeStorage()
        val viewModel = RecordViewModel(repo, FakeThumbnails(), storage)
        viewModel.onRecordingFinalized("u", "n", 1L)
        viewModel.discard()

        assertEquals(listOf("u"), storage.deleted)
        assertTrue(repo.inserts.isEmpty())
        assertEquals(RecordState.Idle, viewModel.state.value)
    }
}
