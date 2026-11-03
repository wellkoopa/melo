package com.louis.melo.data

import androidx.compose.runtime.Composable
import com.louis.melo.player.AudioEngine

/** Absolute path of a private, persistent directory for the app's files. */
expect fun dataDir(): String

/** Turns a local file path into a URI the audio engine can play. */
expect fun fileUri(path: String): String

expect fun createAudioEngine(): AudioEngine

expect fun currentTimeMillis(): Long

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
