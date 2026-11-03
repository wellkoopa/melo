package com.louis.melo.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs

/** Lyrics from LRCLIB (free, no API key). */
object Lyrics {
    data class Line(val time: Long, val text: String)
    data class Result(val synced: List<Line>, val plain: String?)

    private const val API = "https://lrclib.net/api"
    private val client by lazy { HttpClient() }
    private val cache = mutableMapOf<String, Result>()

    suspend fun get(song: Song): Result? {
        cache[song.id]?.let { return it }
        val (artist, title) = cleanup(song)
        val match = runCatching { exact(artist, title, song.duration) }.getOrNull()
            ?: runCatching { search(artist, title, song.duration) }.getOrNull()
        return match?.let(::parse)?.also { cache[song.id] = it }
    }

    private suspend fun fetch(path: String, params: Map<String, String>): String? {
        val resp = client.get("$API/$path") {
            header("User-Agent", "Melo/1.0 (music app)")
            params.forEach { (k, v) -> parameter(k, v) }
        }
        return if (resp.status.isSuccess()) resp.bodyAsText() else null
    }

    private suspend fun exact(artist: String, title: String, duration: Long): JsonObject? {
        val params = buildMap {
            put("artist_name", artist)
            put("track_name", title)
            if (duration > 0) put("duration", duration.toString())
        }
        return fetch("get", params)?.let { Json.parseToJsonElement(it).jsonObject }?.takeIf(::hasLyrics)
    }

    private suspend fun search(artist: String, title: String, duration: Long): JsonObject? {
        val queries = listOf(
            mapOf("track_name" to title, "artist_name" to artist),
            mapOf("q" to "$artist $title"),
        )
        for (params in queries) {
            val arr = fetch("search", params)?.let { Json.parseToJsonElement(it) } as? JsonArray ?: continue
            val best = arr.mapNotNull { it as? JsonObject }.filter(::hasLyrics)
                .sortedWith(
                    compareBy<JsonObject> { if (it.str("syncedLyrics") == null) 1 else 0 }
                        .thenBy { if (duration > 0) abs(it.num("duration") - duration) else 0.0 }
                )
                .firstOrNull { duration <= 0 || abs(it.num("duration") - duration) < 8 }
            if (best != null) return best
        }
        return null
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    private fun hasLyrics(o: JsonObject) =
        (o["instrumental"] as? JsonPrimitive)?.booleanOrNull != true &&
            (o.str("syncedLyrics") != null || o.str("plainLyrics") != null)

    private fun parse(o: JsonObject) = Result(
        synced = o.str("syncedLyrics")?.let(::parseLrc).orEmpty(),
        plain = o.str("plainLyrics"),
    )

    private val tag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    private fun parseLrc(lrc: String): List<Line> = lrc.lines().flatMap { raw ->
        val stamps = tag.findAll(raw).toList()
        if (stamps.isEmpty()) return@flatMap emptyList()
        val text = raw.substring(stamps.last().range.last + 1).trim()
        stamps.map { m ->
            val (mm, ss, frac) = m.destructured
            val ms = frac.padEnd(3, '0').take(3).toLong()
            Line(mm.toLong() * 60_000 + ss.toLong() * 1000 + ms, text)
        }
    }.sortedBy { it.time }

    private val junk = Regex(
        """\s*[(\[][^)\]]*(official|video|vidéo|audio|lyric|paroles|clip|visuali[sz]er|hd|4k|remaster|explicit)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val feat = Regex("""\s*[(\[]?\s*\b(feat|ft)\b\.?\s[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE)

    private fun cleanup(song: Song): Pair<String, String> {
        var title = feat.replace(junk.replace(song.title, ""), "").trim()
        var artist = song.artist.removeSuffix("VEVO").trim()
        if (" - " in title) {
            val (left, right) = title.split(" - ", limit = 2)
            if (left.contains(artist, true) || artist.contains(left, true) || artist.isBlank()) {
                artist = left.trim()
                title = right.trim()
            }
        }
        artist = artist.split(",", " & ", " x ", " X ").first().trim()
        return artist to title
    }
}
