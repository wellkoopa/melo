package com.louis.melo.data

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Remembers the queue and position so playback can resume after the app is restarted. */
object PlaybackStore {
    @Serializable
    data class State(val queue: List<Song>, val index: Int, val position: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val file by lazy { Path(dataDir(), "playback.json") }
    private var last: State? = null

    fun load(): State? = runCatching {
        if (!SystemFileSystem.exists(file)) return null
        json.decodeFromString<State>(SystemFileSystem.source(file).buffered().use { it.readString() })
    }.getOrNull()?.takeIf { it.index in it.queue.indices }

    fun save(state: State) {
        if (state == last) return
        last = state
        runCatching {
            val tmp = Path(dataDir(), "playback.json.tmp")
            SystemFileSystem.sink(tmp).buffered().use { it.writeString(json.encodeToString(State.serializer(), state)) }
            SystemFileSystem.atomicMove(tmp, file)
        }
    }
}
