package app.foyer.launcher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

fun Modifier.onFocusGained(block: () -> Unit): Modifier = onFocusChanged { if (it.isFocused) block() }
