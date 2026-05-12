package com.challenge.videorecord.data

import com.challenge.videorecord.db.Video
import com.challenge.videorecord.ui.UploadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class UploadManagerTest {

    private class FakeRepo : VideoRepository {
        val statuses = mutableListOf<Pair<Long, UploadStatus>>()
        val resets = mutableListOf<Long>()
        override fun observeAll(): Flow<List<Video>> = emptyFlow()
        override fun observeById(id: Long): Flow<Video?> = emptyFlow()
        override suspend fun insert(uri: String, displayName: String, createdAt: Long, thumbnailUri: String?, width: Int?, height: Int?) {}
        override suspend fun setUploadStatus(id: Long, status: UploadStatus) {
            statuses += id to status
        }
        override suspend fun setUploadProgress(id: Long, progress: Int) {}
        override suspend fun resetUpload(id: Long) {
            resets += id
        }
    }

    @Test
    fun `cancel resets upload before completion`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repo = FakeRepo()
        val mgr = UploadManager(repository = repo, scope = scope)
        mgr.start(3)
        scope.advanceTimeBy(15.milliseconds)
        mgr.cancel(3)
        scope.advanceUntilIdle()

        assertEquals(listOf(3L), repo.resets)
        assertTrue(repo.statuses.none { it == 3L to UploadStatus.UPLOADED })
    }
}
