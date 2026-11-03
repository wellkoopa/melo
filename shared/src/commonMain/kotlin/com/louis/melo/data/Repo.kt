package com.louis.melo.data

import com.yushosei.newpipe.extractor.Image
import com.yushosei.newpipe.extractor.MediaFormat
import com.yushosei.newpipe.extractor.NewPipe
import com.yushosei.newpipe.extractor.ServiceList
import com.yushosei.newpipe.extractor.stream.DeliveryMethod
import com.yushosei.newpipe.extractor.stream.StreamInfoItem
import com.yushosei.newpipe.extractor.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as Q
import com.yushosei.newpipe.util.DefaultDownloaderImpl
import com.yushosei.newpipe.util.ExtractorHelper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** YouTube Music access, through NewPipeExtractor-KMP (search, suggestions, audio streams). */
object Repo {
    enum class Filter(val label: String, val value: String) {
        SONGS("Titres", Q.MUSIC_SONGS),
        VIDEOS("Vidéos", Q.MUSIC_VIDEOS),
    }

    data class AudioSource(val url: String, val ext: String, val expiresAt: Long)

    /** iOS cannot play WebM/Opus, so it must get M4A (AAC) streams. */
    var requireM4a = false

    private val initLock = Mutex()
    private var ready = false
    private val yt get() = ServiceList.YouTube.serviceId

    private suspend fun ensureInit() = initLock.withLock {
        if (!ready) {
            NewPipe.init(DefaultDownloaderImpl.initDefault())
            ready = true
        }
    }

    private val cacheLock = Mutex()
    private val searchCache = mutableMapOf<String, Pair<Long, List<Song>>>()
    private val sources = mutableMapOf<String, AudioSource>()

    suspend fun search(query: String, filter: Filter): List<Song> {
        val key = "$filter:$query"
        cacheLock.withLock { searchCache[key] }?.let { (time, songs) ->
            if (currentTimeMillis() - time < 10 * 60_000) return songs
        }
        ensureInit()
        val songs = ExtractorHelper.searchFor(yt, query, listOf(filter.value), "")
            .relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toSong() }
        cacheLock.withLock { searchCache[key] = currentTimeMillis() to songs }
        return songs
    }

    suspend fun suggestions(query: String): List<String> {
        ensureInit()
        return ExtractorHelper.suggestionsFor(yt, query)
    }

    /** A direct audio URL for a video id; M4A is preferred everywhere. */
    suspend fun audioSource(id: String, forceRefresh: Boolean = false): AudioSource {
        val now = currentTimeMillis()
        if (!forceRefresh) {
            cacheLock.withLock { sources[id] }?.takeIf { it.expiresAt - now > 10 * 60_000 }?.let { return it }
        }
        ensureInit()
        val info = ExtractorHelper.getStreamInfo(yt, watchUrl(id), forceLoad = forceRefresh)
        val streams = info.audioStreams.filter { it.isUrl() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val m4a = streams.filter { it.format == MediaFormat.M4A }
        val pick = m4a.ifEmpty { if (requireM4a) emptyList() else streams }
            .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
            ?: throw IllegalStateException("Aucun flux audio compatible")
        val expire = Regex("[?&]expire=(\\d+)").find(pick.content)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
            ?: (now + 3 * 3600_000)
        val source = AudioSource(pick.content, if (pick.format == MediaFormat.M4A) "m4a" else "webm", expire)
        cacheLock.withLock { sources[id] = source }
        return source
    }

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    private val idPattern = Regex("(?:[?&]v=|youtu\\.be/|/shorts/)([\\w-]{11})")
    private val sizeParam = Regex("=w\\d+-h\\d+")

    private fun bestThumb(images: List<Image>): String? =
        images.maxByOrNull { it.width.toLong() * it.height }?.url?.let { sizeParam.replace(it, "=w544-h544") }

    private fun StreamInfoItem.toSong(): Song? {
        val id = idPattern.find(url)?.groupValues?.get(1) ?: return null
        return Song(
            id = id,
            title = name,
            artist = uploaderName.orEmpty().removeSuffix(" - Topic").trim(),
            thumbnail = bestThumb(thumbnails),
            duration = duration.coerceAtLeast(0),
        )
    }
}
