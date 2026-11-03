package com.louis.melo.player

import com.louis.melo.data.Song

/** Minimal single-track player implemented per platform (Media3 on Android, AVPlayer on iOS). */
interface AudioEngine {
    /** Loads [uri] and starts playing it. */
    fun load(uri: String, song: Song)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(ms: Long)

    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    val isBuffering: Boolean

    var listener: Listener?

    interface Listener {
        fun onEnded()
        fun onError(message: String)
        /** Next/previous pressed in the notification, lock screen or headphones. */
        fun onNext()
        fun onPrevious()
    }
}
