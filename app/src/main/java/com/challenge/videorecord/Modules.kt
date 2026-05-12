package com.challenge.videorecord

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.challenge.videorecord.data.AndroidMediaStorage
import com.challenge.videorecord.data.AndroidThumbnailExtractor
import com.challenge.videorecord.data.DefaultVideoRepository
import com.challenge.videorecord.data.MediaStorage
import com.challenge.videorecord.data.ThumbnailExtractor
import com.challenge.videorecord.data.UploadManager
import com.challenge.videorecord.data.VideoRepository
import com.challenge.videorecord.db.VideoDatabase
import com.challenge.videorecord.ui.screen.record.RecordViewModel
import com.challenge.videorecord.ui.screen.videodetail.VideoDetailViewModel
import com.challenge.videorecord.ui.screen.videolist.VideoListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<ThumbnailExtractor> { AndroidThumbnailExtractor(androidContext()) }
    single<MediaStorage> { AndroidMediaStorage(androidContext()) }
    single<VideoRepository> {
        val driver = AndroidSqliteDriver(VideoDatabase.Schema, androidContext(), "videos.db")
        DefaultVideoRepository(VideoDatabase(driver))
    }
    single { UploadManager(get()) }

    viewModelOf(::RecordViewModel)
    viewModelOf(::VideoListViewModel)
    viewModel<VideoDetailViewModel> { (id: Long) -> VideoDetailViewModel(id, get(), get()) }
}
