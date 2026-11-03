package com.louis.melo.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String? = null,
    /** Duration in seconds, 0 when unknown. */
    val duration: Long = 0,
)

@Serializable
data class LocalPlaylist(val id: String, val name: String, val songs: List<Song>)

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

/** Short user-facing messages, shown as snackbars. */
object Messages {
    private val _flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val flow: SharedFlow<String> = _flow
    fun show(message: String) {
        _flow.tryEmit(message)
    }
}
