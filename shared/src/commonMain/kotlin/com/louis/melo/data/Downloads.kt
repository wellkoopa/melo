package com.louis.melo.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Saves songs in the app's private storage for offline listening. */
object Downloads {
    private const val CHUNK = 1L shl 20
    private val extensions = listOf("m4a", "webm")
    private val dir by lazy { Path(dataDir(), "downloads").also { SystemFileSystem.createDirectories(it) } }
    private val client by lazy { HttpClient() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val slots = Semaphore(2)

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** Songs being downloaded, with progress in 0..1. */
    val progress: StateFlow<Map<String, Float>> = _progress

    fun file(id: String): Path? = extensions.map { Path(dir, "$id.$it") }.firstOrNull { SystemFileSystem.exists(it) }

    fun download(songs: List<Song>) {
        val todo = songs.filter { file(it.id) == null && it.id !in _progress.value }
        if (todo.isEmpty()) return
        Messages.show(if (todo.size == 1) "Téléchargement de « ${todo[0].title} »" else "Téléchargement de ${todo.size} titres")
        todo.forEach(::start)
    }

    fun download(song: Song) = download(listOf(song))

    fun delete(song: Song) {
        file(song.id)?.let { SystemFileSystem.delete(it, mustExist = false) }
        Library.removeDownload(song.id)
    }

    private fun start(song: Song) {
        _progress.update { it + (song.id to 0f) }
        scope.launch {
            try {
                slots.withPermit { fetch(song) }
                Library.addDownload(song)
            } catch (e: Exception) {
                Messages.show("Échec du téléchargement : ${song.title}")
            } finally {
                _progress.update { it - song.id }
            }
        }
    }

    private suspend fun fetch(song: Song) {
        val source = Repo.audioSource(song.id)
        val part = Path(dir, "${song.id}.part")
        var pos = 0L
        var total = -1L
        SystemFileSystem.sink(part).buffered().use { out ->
            while (total < 0 || pos < total) {
                val resp = client.get(source.url) { header("Range", "bytes=$pos-${pos + CHUNK - 1}") }
                if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
                val bytes = resp.bodyAsBytes()
                out.write(bytes)
                pos += bytes.size
                total = if (resp.status.value == 206) {
                    resp.headers["Content-Range"]?.substringAfter('/')?.toLongOrNull() ?: pos
                } else {
                    pos
                }
                if (bytes.isEmpty()) total = pos
                if (total > 0) _progress.update { it + (song.id to pos.toFloat() / total) }
            }
        }
        SystemFileSystem.atomicMove(part, Path(dir, "${song.id}.${source.ext}"))
    }
}
