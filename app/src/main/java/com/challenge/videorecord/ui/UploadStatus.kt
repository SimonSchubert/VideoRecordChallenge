package com.challenge.videorecord.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
enum class UploadStatus(
    val label: String,
    val icon: ImageVector,
) {
    RECORDED("Recorded", Icons.Filled.PhotoCameraFront),
    UPLOADING("Uploading", Icons.Filled.Sync),
    UPLOADED("Uploaded", Icons.Filled.Done),
    FAILED("Failed", Icons.Filled.Warning),
    ;

    companion object {
        fun fromDb(value: String): UploadStatus = entries.firstOrNull { it.name == value } ?: RECORDED
    }
}

val UploadStatus.tint: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        UploadStatus.RECORDED -> MaterialTheme.colorScheme.onSurfaceVariant
        UploadStatus.UPLOADING -> MaterialTheme.colorScheme.primary
        UploadStatus.UPLOADED -> MaterialTheme.colorScheme.tertiary
        UploadStatus.FAILED -> MaterialTheme.colorScheme.error
    }
