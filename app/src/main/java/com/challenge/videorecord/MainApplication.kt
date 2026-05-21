package com.challenge.videorecord

import android.app.Application
import com.challenge.videorecord.data.MediaStorage
import com.challenge.videorecord.data.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@MainApplication)
            modules(appModule)
        }

        val appScope: CoroutineScope = get()
        val repo: VideoRepository = get()
        val storage: MediaStorage = get()
        appScope.launch(Dispatchers.IO) { storage.deleteOrphans(repo.allUris().toSet()) }
    }
}
