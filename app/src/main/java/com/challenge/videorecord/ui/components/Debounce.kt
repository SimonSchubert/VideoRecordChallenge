package com.challenge.videorecord.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val DEFAULT_DEBOUNCE_MS = 500L

@Composable
fun rememberDebouncedClick(
    debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    onClick: () -> Unit,
): () -> Unit {
    var lastClickAt by remember { mutableLongStateOf(0L) }
    return remember(onClick, debounceMs) {
        {
            val now = SystemClock.uptimeMillis()
            if (now - lastClickAt >= debounceMs) {
                lastClickAt = now
                onClick()
            }
        }
    }
}
