package com.challenge.videorecord.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.challenge.videorecord.ui.screen.record.RecordScreen
import com.challenge.videorecord.ui.screen.videodetail.VideoDetailScreen
import com.challenge.videorecord.ui.screen.videolist.VideoListScreen
import kotlinx.serialization.Serializable

@PreviewScreenSizes
@Composable
fun App() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = VideoList,
    ) {
        composable<VideoList> {
            VideoListScreen(
                onRecordVideo = {
                    navController.navigate(VideoRecord)
                },
                onVideoSelected = {
                    navController.navigate(VideoDetail(it))
                },
            )
        }
        composable<VideoDetail> { backStackEntry ->
            val route: VideoDetail = backStackEntry.toRoute()
            VideoDetailScreen(
                id = route.id,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable<VideoRecord> {
            RecordScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }
    }
}

@Serializable
object VideoList

@Serializable
data class VideoDetail(val id: Long)

@Serializable
object VideoRecord
