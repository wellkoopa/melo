package com.louis.melo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.louis.melo.data.Downloads
import com.louis.melo.data.Library
import com.louis.melo.data.Messages
import com.louis.melo.data.Song
import com.louis.melo.data.formatDuration
import com.louis.melo.player.LocalPlayer
import kotlinx.coroutines.CancellationException

// ---- async loading ----
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
}

@Composable
fun <T> LoadContent(key: Any?, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<Load<T>>(Load.Loading, key, attempt) {
        value = Load.Loading
        value = try {
            Load.Ok(load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Error(e.message ?: e::class.simpleName ?: "Erreur")
        }
    }
    when (val s = state) {
        Load.Loading -> CenteredProgress()
        is Load.Error -> ErrorView(s.message) { attempt++ }
        is Load.Ok -> content(s.data)
    }
}

@Composable
fun CenteredProgress(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Impossible de charger le contenu", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        Button(onClick = onRetry) { Text("Réessayer") }
    }
}

@Composable
fun EmptyView(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- building blocks ----
@Composable
fun Thumb(url: String?, size: Dp, shape: Shape = RoundedCornerShape(8.dp)) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    extraMenu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    val player = LocalPlayer.current
    val playing = player.current?.id == song.id
    val downloads by Library.downloads.collectAsState()
    val progress = Downloads.progress.collectAsState().value[song.id]
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(song.thumbnail, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                listOfNotNull(song.artist.ifBlank { null }, song.duration.takeIf { it > 0 }?.let(::formatDuration))
                    .joinToString(" • "),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            progress != null -> CircularProgressIndicator(progress = { progress }, Modifier.size(18.dp), strokeWidth = 2.dp)
            downloads.any { it.id == song.id } ->
                Icon(Icons.Rounded.DownloadDone, "Téléchargé", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
            SongMenu(song, menu, { menu = false }, extraMenu)
        }
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extra: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    val player = LocalPlayer.current
    val favorites by Library.favorites.collectAsState()
    val downloads by Library.downloads.collectAsState()
    var addTo by remember { mutableStateOf(false) }
    fun act(block: () -> Unit): () -> Unit = { onDismiss(); block() }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("Lire ensuite", Icons.AutoMirrored.Rounded.PlaylistPlay, act { player.playNext(song) })
        MenuItem("Ajouter à la file d'attente", Icons.AutoMirrored.Rounded.QueueMusic, act { player.addToQueue(listOf(song)) })
        MenuItem("Radio de l'artiste", Icons.Rounded.Radio, act { player.playSong(song) })
        if (favorites.any { it.id == song.id }) {
            MenuItem("Retirer des favoris", Icons.Rounded.Favorite, act { Library.toggleFavorite(song) })
        } else {
            MenuItem("Ajouter aux favoris", Icons.Rounded.FavoriteBorder, act { Library.toggleFavorite(song) })
        }
        MenuItem("Ajouter à une playlist", Icons.AutoMirrored.Rounded.PlaylistAdd, act { addTo = true })
        if (downloads.any { it.id == song.id }) {
            MenuItem("Supprimer le téléchargement", Icons.Rounded.Delete, act { Downloads.delete(song) })
        } else {
            MenuItem("Télécharger", Icons.Rounded.Download, act { Downloads.download(song) })
        }
        extra?.invoke(this, onDismiss)
    }
    if (addTo) AddToPlaylistDialog(listOf(song)) { addTo = false }
}

@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val playlists by Library.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("Nouvelle playlist", "", onDismiss) { name ->
            Library.createPlaylist(name, songs)
            Messages.show("Playlist « $name » créée")
            onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter à une playlist") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("Nouvelle playlist") },
                        leadingContent = { Icon(Icons.Rounded.Add, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.clickable { creating = true },
                    )
                }
                items(playlists, key = { it.id }) { pl ->
                    ListItem(
                        headlineContent = { Text(pl.name) },
                        supportingContent = { Text("${pl.songs.size} titres") },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.clickable {
                            Library.addToPlaylist(pl.id, songs)
                            Messages.show("Ajouté à « ${pl.name} »")
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nom") }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun Card(title: String, subtitle: String, thumbnail: String?, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp)) {
        Thumb(thumbnail, 138.dp, RoundedCornerShape(12.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
