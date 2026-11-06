package com.louis.melo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.louis.melo.data.Downloads
import com.louis.melo.data.Library
import com.louis.melo.data.Lyrics
import com.louis.melo.data.Song
import com.louis.melo.data.currentTimeMillis
import com.louis.melo.data.formatDuration
import com.louis.melo.player.LocalPlayer
import com.louis.melo.player.PlayerController
import com.louis.melo.player.Repeat

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalPlayer.current
    val song = player.current ?: return
    val progress = if (player.duration > 0) (player.position.toFloat() / player.duration).coerceIn(0f, 1f) else 0f

    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).swipeToSkip(player),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(song.thumbnail, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = player::togglePlay, modifier = Modifier.size(44.dp)) {
                    when {
                        player.isBuffering -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        player.isPlaying -> Icon(Icons.Rounded.Pause, "Pause", Modifier.size(28.dp))
                        else -> Icon(Icons.Rounded.PlayArrow, "Lecture", Modifier.size(28.dp))
                    }
                }
                IconButton(onClick = player::next) { Icon(Icons.Rounded.SkipNext, "Suivant") }
            }
        }
    }
}

/** Swipe left for the next song, right for the previous one. */
private fun Modifier.swipeToSkip(player: PlayerController): Modifier = pointerInput(player) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            val threshold = 60.dp.toPx()
            if (total < -threshold) player.next() else if (total > threshold) player.previous()
        },
    ) { _, amount -> total += amount }
}

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val player = LocalPlayer.current
    val song = player.current ?: return
    val favorites by Library.favorites.collectAsState()
    val downloads by Library.downloads.collectAsState()
    val downloadProgress = Downloads.progress.collectAsState().value[song.id]
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.6f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.9f)))
            )
        )
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Fermer", Modifier.size(32.dp), tint = Color.White) }
                Text(
                    "En cours de lecture",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options", tint = Color.White) }
                    SongMenu(song, menu, { menu = false })
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                if (showLyrics) {
                    LyricsView(song, player.position, player::seekTo)
                } else {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val fav = favorites.any { it.id == song.id }
                IconButton(onClick = { Library.toggleFavorite(song) }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Favori",
                        tint = if (fav) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }

            val duration = player.duration.coerceAtLeast(1)
            Slider(
                value = dragging ?: (player.position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row {
                val shown = dragging?.let { (it * duration).toLong() } ?: player.position
                Text(formatDuration(shown / 1000), style = MaterialTheme.typography.bodySmall, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(player.duration.coerceAtLeast(0) / 1000), style = MaterialTheme.typography.bodySmall, color = Color.White)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, "Aléatoire", tint = if (player.shuffle) MaterialTheme.colorScheme.primary else Color.White)
                }
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Précédent", Modifier.size(40.dp), tint = Color.White)
                }
                FilledIconButton(
                    onClick = player::togglePlay,
                    modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    when {
                        player.isBuffering -> CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
                        player.isPlaying -> Icon(Icons.Rounded.Pause, "Pause", Modifier.size(40.dp))
                        else -> Icon(Icons.Rounded.PlayArrow, "Lecture", Modifier.size(40.dp))
                    }
                }
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Suivant", Modifier.size(40.dp), tint = Color.White)
                }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (player.repeat == Repeat.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Répéter",
                        tint = if (player.repeat != Repeat.OFF) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(Icons.Rounded.Lyrics, "Paroles", tint = if (showLyrics) MaterialTheme.colorScheme.primary else Color.White)
                }
                IconButton(onClick = { showTimer = true }) {
                    Icon(Icons.Rounded.Bedtime, "Minuteur", tint = if (player.sleepAt != null) MaterialTheme.colorScheme.primary else Color.White)
                }
                when {
                    downloadProgress != null -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { downloadProgress }, Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    downloads.any { it.id == song.id } -> IconButton(onClick = {}) {
                        Icon(Icons.Rounded.DownloadDone, "Téléchargé", tint = MaterialTheme.colorScheme.primary)
                    }
                    else -> IconButton(onClick = { Downloads.download(song) }) { Icon(Icons.Rounded.Download, "Télécharger", tint = Color.White) }
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, "File d'attente", tint = Color.White)
                }
            }
        }
    }

    if (showQueue) QueueSheet(player) { showQueue = false }
    if (showTimer) SleepTimerDialog(player) { showTimer = false }
}

@Composable
private fun LyricsView(song: Song, position: Long, onSeek: (Long) -> Unit) {
    val result by produceState<Lyrics.Result?>(null, song.id) {
        value = runCatching { Lyrics.get(song) }.getOrNull() ?: Lyrics.Result(emptyList(), null)
    }
    val lyrics = result
    when {
        lyrics == null -> CircularProgressIndicator()
        lyrics.synced.isNotEmpty() -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val lines = lyrics.synced
            val current = lines.indexOfLast { it.time <= position + 300 }
            val listState = rememberLazyListState()
            LaunchedEffect(current) { if (current >= 0) listState.animateScrollToItem(current) }
            LazyColumn(state = listState, contentPadding = PaddingValues(vertical = maxHeight / 3)) {
                itemsIndexed(lines) { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (i == current) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { onSeek(line.time) }.padding(vertical = 8.dp),
                    )
                }
            }
        }
        lyrics.plain != null -> Text(
            lyrics.plain,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
        else -> Text("Paroles introuvables", color = Color.White.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(player: PlayerController, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "File d'attente (${player.queue.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val state = rememberLazyListState(initialFirstVisibleItemIndex = (player.index - 1).coerceAtLeast(0))
        LazyColumn(state = state) {
            itemsIndexed(player.queue, key = { i, s -> "$i${s.id}" }) { i, song ->
                val isCurrent = i == player.index
                ListItem(
                    headlineContent = {
                        Text(
                            song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Thumb(song.thumbnail, 48.dp) },
                    trailingContent = {
                        if (!isCurrent) IconButton(onClick = { player.removeAt(i) }) { Icon(Icons.Rounded.Close, "Retirer") }
                    },
                    modifier = Modifier.clickable { player.skipTo(i) },
                )
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(player: PlayerController, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Minuteur de sommeil") },
        text = {
            Column {
                player.sleepAt?.let {
                    val left = ((it - currentTimeMillis()) / 60_000).coerceAtLeast(0)
                    Text("Arrêt dans environ $left min", modifier = Modifier.padding(bottom = 8.dp))
                }
                listOf(10, 15, 30, 45, 60, 90).forEach { min ->
                    TextButton(onClick = { player.setSleepTimer(min); onDismiss() }) { Text("$min minutes") }
                }
            }
        },
        confirmButton = {
            if (player.sleepAt != null) TextButton(onClick = { player.setSleepTimer(null); onDismiss() }) { Text("Désactiver") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
