package com.challenge.videorecord.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ThumbnailImage(
    modifier: Modifier,
    url: String?,
) {
    val thumbnail = rememberBitmap(url)
    thumbnail?.let { thumbnail ->
        Image(
            modifier = modifier,
            bitmap = thumbnail.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun rememberBitmap(path: String?): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it) } }
    }
    return bitmap
}
