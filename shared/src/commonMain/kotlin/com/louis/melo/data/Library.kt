package com.louis.melo.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Favorites, history, playlists, downloads and recent searches, saved as JSON on the device. */
object Library {
    @Serializable
    private data class Snapshot(
        val favorites: List<Song> = emptyList(),
        val history: List<Song> = emptyList(),
        val playlists: List<LocalPlaylist> = emptyList(),
        val downloads: List<Song> = emptyList(),
        val searches: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val file by lazy { Path(dataDir(), "library.json") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val saves = Channel<String>(Channel.CONFLATED)

    private val _favorites = MutableStateFlow(emptyList<Song>())
    private val _history = MutableStateFlow(emptyList<Song>())
    private val _playlists = MutableStateFlow(emptyList<LocalPlaylist>())
    private val _downloads = MutableStateFlow(emptyList<Song>())
    private val _searches = MutableStateFlow(emptyList<String>())

    val favorites: StateFlow<List<Song>> = _favorites
    val history: StateFlow<List<Song>> = _history
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists
    val downloads: StateFlow<List<Song>> = _downloads
    val searches: StateFlow<List<String>> = _searches

    fun init() {
        runCatching {
            if (SystemFileSystem.exists(file)) {
                val s = json.decodeFromString<Snapshot>(SystemFileSystem.source(file).buffered().use { it.readString() })
                _favorites.value = s.favorites
                _history.value = s.history
                _playlists.value = s.playlists
                _downloads.value = s.downloads
                _searches.value = s.searches
            }
        }
        scope.launch {
            for (text in saves) {
                runCatching {
                    val tmp = Path(dataDir(), "library.json.tmp")
                    SystemFileSystem.sink(tmp).buffered().use { it.writeString(text) }
                    SystemFileSystem.atomicMove(tmp, file)
                }
            }
        }
    }

    fun isFavorite(id: String) = _favorites.value.any { it.id == id }

    fun toggleFavorite(song: Song) {
        _favorites.update { list -> if (list.any { it.id == song.id }) list.filter { it.id != song.id } else listOf(song) + list }
        save()
    }

    fun addHistory(song: Song) {
        _history.update { list -> (listOf(song) + list.filter { it.id != song.id }).take(300) }
        save()
    }

    fun clearHistory() {
        _history.value = emptyList()
        save()
    }

    fun addSearch(query: String) {
        _searches.update { list -> (listOf(query) + list.filter { !it.equals(query, true) }).take(20) }
        save()
    }

    fun removeSearch(query: String) {
        _searches.update { it - query }
        save()
    }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): String {
        val id = Random.nextLong().toULong().toString(16)
        _playlists.update { it + LocalPlaylist(id, name, songs.distinctBy(Song::id)) }
        save()
        return id
    }

    fun renamePlaylist(id: String, name: String) = editPlaylist(id) { it.copy(name = name) }

    fun deletePlaylist(id: String) {
        _playlists.update { list -> list.filter { it.id != id } }
        save()
    }

    fun addToPlaylist(id: String, songs: List<Song>) =
        editPlaylist(id) { pl -> pl.copy(songs = (pl.songs + songs).distinctBy(Song::id)) }

    fun removeFromPlaylist(id: String, songId: String) =
        editPlaylist(id) { pl -> pl.copy(songs = pl.songs.filter { it.id != songId }) }

    private fun editPlaylist(id: String, edit: (LocalPlaylist) -> LocalPlaylist) {
        _playlists.update { list -> list.map { if (it.id == id) edit(it) else it } }
        save()
    }

    fun addDownload(song: Song) {
        _downloads.update { list -> listOf(song) + list.filter { it.id != song.id } }
        save()
    }

    fun removeDownload(id: String) {
        _downloads.update { list -> list.filter { it.id != id } }
        save()
    }

    private fun save() {
        val snapshot = Snapshot(_favorites.value, _history.value, _playlists.value, _downloads.value, _searches.value)
        saves.trySend(json.encodeToString(Snapshot.serializer(), snapshot))
    }
}
