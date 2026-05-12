package com.challenge.videorecord.data

import com.challenge.videorecord.ui.UploadStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class UploadManager(
    private val repository: VideoRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {
    private val jobs = mutableMapOf<Long, Job>()

    @Synchronized
    fun start(id: Long) {
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch { runUpload(id) }
    }

    @Synchronized
    fun cancel(id: Long) {
        val job = jobs.remove(id) ?: return
        scope.launch {
            job.cancelAndJoin()
            repository.resetUpload(id)
        }
    }

    private suspend fun runUpload(id: Long) {
        val failureProbability = 0.5f

        val failAt = if (Random.nextFloat() <= failureProbability) Random.nextInt(20, 81) else null
        repository.setUploadProgress(id, 0)
        repository.setUploadStatus(id, UploadStatus.UPLOADING)
        var progress = 5
        while (progress <= 100) {
            delay(200.milliseconds)
            if (failAt != null && progress >= failAt) {
                repository.setUploadStatus(id, UploadStatus.FAILED)
                return
            }
            repository.setUploadProgress(id, progress)
            progress += 5
        }
        repository.setUploadStatus(id, UploadStatus.UPLOADED)
    }
}
