package app.foyer.launcher.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged

fun Modifier.onFocusGained(block: () -> Unit): Modifier = onFocusChanged { if (it.isFocused) block() }

/** Focuses the target now, or after the next frame if a lazy list hasn't placed it yet. */
suspend fun FocusRequester.requestFocusWhenReady() {
    if (runCatching { requestFocus() }.isSuccess) return
    withFrameNanos { }
    runCatching { requestFocus() }
}
