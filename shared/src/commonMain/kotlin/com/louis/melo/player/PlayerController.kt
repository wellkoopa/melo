package com.louis.melo.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.louis.melo.data.Downloads
import com.louis.melo.data.Library
import com.louis.melo.data.Messages
import com.louis.melo.data.PlaybackStore
import com.louis.melo.data.Repo
import com.louis.melo.data.Song
import com.louis.melo.data.createAudioEngine
import com.louis.melo.data.currentTimeMillis
import com.louis.melo.data.fileUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("PlayerController not provided") }

enum class Repeat { OFF, ALL, ONE }

/** Queue logic shared by Android and iOS; the platform engine only plays one track at a time. */
class PlayerController(private val engine: AudioEngine = createAudioEngine()) : AudioEngine.Listener {
    private val scope = MainScope()
    private var loadJob: Job? = null
    private var radioJob: Job? = null
    private var sleepJob: Job? = null
    private var original: List<Song>? = null
    private var retried = false

    var queue by mutableStateOf<List<Song>>(emptyList()); private set
    var index by mutableIntStateOf(-1); private set
    val current: Song? get() = queue.getOrNull(index)

    var isPlaying by mutableStateOf(false); private set
    var isBuffering by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableStateOf(Repeat.OFF); private set
    var sleepAt by mutableStateOf<Long?>(null); private set

    /** Song currently loaded in the engine; differs from [current] right after a restore. */
    private var loadedId: String? = null
    private var pendingSeek: Long? = null

    init {
        engine.listener = this
        PlaybackStore.load()?.let { saved ->
            queue = saved.queue
            index = saved.index
            position = saved.position
            duration = saved.queue[saved.index].duration * 1000
        }
        scope.launch {
            var tick = 0
            while (true) {
                isPlaying = engine.isPlaying
                isBuffering = loadJob?.isActive == true || engine.isBuffering
                if (loadedId != null && loadedId == current?.id) {
                    pendingSeek?.let { target ->
                        if (engine.durationMs > 0) {
                            engine.seekTo(target)
                            pendingSeek = null
                        }
                    }
                    if (pendingSeek == null) position = engine.positionMs
                    engine.durationMs.takeIf { it > 0 }?.let { duration = it }
                }
                if (++tick % 20 == 0 && current != null) PlaybackStore.save(PlaybackStore.State(queue, index, position))
                delay(250)
            }
        }
    }

    // ---- queue ----
    fun playSongs(songs: List<Song>, start: Int = 0) {
        if (songs.isEmpty()) return
        original = null
        shuffle = false
        queue = songs
        load(start.coerceIn(songs.indices))
    }

    /** Plays one song, then keeps going with more songs from the same artist. */
    fun playSong(song: Song) = playSongs(listOf(song))

    fun playNext(song: Song) {
        if (current == null) return playSong(song)
        queue = queue.toMutableList().apply { add(index + 1, song) }
        Messages.show("Lu ensuite")
    }

    fun addToQueue(songs: List<Song>) {
        if (current == null) return playSongs(songs)
        queue = queue + songs
        Messages.show("Ajouté à la file d'attente")
    }

    fun skipTo(i: Int) {
        if (i in queue.indices) load(i)
    }

    fun removeAt(i: Int) {
        if (i == index || i !in queue.indices) return
        queue = queue.toMutableList().apply { removeAt(i) }
        if (i < index) index--
    }

    fun toggleShuffle() {
        val song = current ?: return
        if (!shuffle) {
            original = queue
            queue = listOf(song) + queue.filterIndexed { i, _ -> i != index }.shuffled()
            index = 0
        } else {
            original?.let { orig ->
                queue = orig
                index = orig.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            }
            original = null
        }
        shuffle = !shuffle
    }

    fun cycleRepeat() {
        repeat = Repeat.entries[(repeat.ordinal + 1) % Repeat.entries.size]
    }

    // ---- transport ----
    fun togglePlay() {
        val song = current ?: return
        when {
            loadedId != song.id -> {
                // Restored from a previous session: load it and jump back to where we were.
                pendingSeek = position.takeIf { it > 0 }
                start(song, forceRefresh = false)
            }
            engine.isPlaying -> engine.pause()
            loadJob?.isActive == true -> Unit
            else -> engine.play()
        }
    }

    fun next() {
        when {
            index < queue.lastIndex -> load(index + 1)
            repeat == Repeat.ALL && queue.isNotEmpty() -> load(0)
            else -> extendWithRadio(andPlay = true)
        }
    }

    fun previous() {
        if (engine.positionMs > 3000 || index <= 0) engine.seekTo(0) else load(index - 1)
    }

    fun seekTo(ms: Long) {
        engine.seekTo(ms)
        position = ms
    }

    private fun load(i: Int) {
        index = i
        val song = queue[i]
        position = 0
        pendingSeek = null
        duration = song.duration * 1000
        retried = false
        Library.addHistory(song)
        if (queue.size - i <= 2) extendWithRadio(andPlay = false)
        start(song, forceRefresh = false)
    }

    private fun start(song: Song, forceRefresh: Boolean) {
        loadJob?.cancel()
        loadedId = song.id
        isBuffering = true
        loadJob = scope.launch {
            try {
                val uri = Downloads.file(song.id)?.let { fileUri(it.toString()) }
                    ?: Repo.audioSource(song.id, forceRefresh).url
                engine.load(uri, song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "erreur réseau")
            }
        }
    }

    /** NewPipeExtractor-KMP has no radio/mix support, so continue with songs by the same artist. */
    private fun extendWithRadio(andPlay: Boolean) {
        if (repeat != Repeat.OFF || radioJob?.isActive == true) return
        val seed = current ?: queue.lastOrNull() ?: return
        if (seed.artist.isBlank()) return
        radioJob = scope.launch {
            val known = queue.map { it.id }.toSet()
            val more = runCatching { Repo.search(seed.artist, Repo.Filter.SONGS) }.getOrDefault(emptyList())
                .filter { it.id !in known }.shuffled().take(15)
            if (more.isEmpty()) return@launch
            val wasLast = index == queue.lastIndex
            queue = queue + more
            if (andPlay && wasLast) load(index + 1)
        }
    }

    // ---- engine callbacks ----
    override fun onEnded() {
        if (repeat == Repeat.ONE) {
            engine.seekTo(0)
            engine.play()
        } else {
            next()
        }
    }

    override fun onError(message: String) {
        val song = current ?: return
        if (!retried) {
            // Stream URLs expire: fetch a fresh one once before giving up.
            retried = true
            start(song, forceRefresh = true)
            return
        }
        Messages.show("Lecture impossible : ${song.title}")
        if (index < queue.lastIndex) load(index + 1) else engine.stop()
    }

    override fun onNext() = next()
    override fun onPrevious() = previous()

    // ---- sleep timer ----
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            sleepAt = null
            return
        }
        val at = currentTimeMillis() + minutes * 60_000L
        sleepAt = at
        sleepJob = scope.launch {
            delay(at - currentTimeMillis())
            engine.pause()
            sleepAt = null
        }
        Messages.show("Arrêt dans $minutes min")
    }
}
