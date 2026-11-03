package com.louis.melo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.louis.melo.data.Downloads
import com.louis.melo.data.Library
import com.louis.melo.data.Repo
import com.louis.melo.data.Song
import com.louis.melo.data.formatDuration
import com.louis.melo.player.LocalPlayer
import kotlinx.coroutines.delay

private val moods = listOf(
    "Hits du moment" to "top hits 2026",
    "Rap FR" to "rap français",
    "Chill" to "chill",
    "Sport" to "workout",
    "Soirée" to "party hits",
    "Lo-fi" to "lofi beats",
    "Années 2000" to "hits années 2000",
    "Rock" to "rock classics",
    "Électro" to "electro",
    "Jazz" to "jazz",
)

private const val FAVORITES = "favorites"
private const val DOWNLOADS = "downloads"
private const val HISTORY = "history"

@Composable
fun HomeScreen() {
    val player = LocalPlayer.current
    val nav = LocalNav.current
    val history by Library.history.collectAsState()
    val favorites by Library.favorites.collectAsState()
    val seedArtist = history.firstOrNull()?.artist

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                "Melo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(moods) { (label, query) ->
                    AssistChip(onClick = { nav.push(Screen.Search(query)) }, label = { Text(label) })
                }
            }
        }
        if (history.isNotEmpty()) {
            item {
                SectionTitle("Écoutés récemment")
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    items(history.take(15), key = { it.id }) { song ->
                        Card(song.title, song.artist, song.thumbnail) { player.playSong(song) }
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item {
                SectionTitle("Vos favoris", "Tout lire") { player.playSongs(favorites) }
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    itemsIndexed(favorites.take(15), key = { _, s -> s.id }) { i, song ->
                        Card(song.title, song.artist, song.thumbnail) { player.playSongs(favorites, i) }
                    }
                }
            }
        }
        if (!seedArtist.isNullOrBlank()) {
            item { SectionTitle("Plus de $seedArtist") }
            item {
                LoadContent(seedArtist, { Repo.search(seedArtist, Repo.Filter.SONGS) }) { songs ->
                    Column {
                        songs.take(8).forEachIndexed { i, song -> SongRow(song, onClick = { player.playSongs(songs, i) }) }
                    }
                }
            }
        } else {
            item {
                EmptyView("Cherchez un titre ou choisissez une ambiance pour commencer")
            }
        }
    }
}

@Composable
fun SearchScreen(screen: Screen.Search) {
    val player = LocalPlayer.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var text by remember(screen) { mutableStateOf(screen.query) }
    var submitted by remember(screen) { mutableStateOf(screen.query) }
    var filter by remember(screen) { mutableStateOf(screen.filter) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    val recentSearches by Library.searches.collectAsState()

    fun submit(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        text = q
        submitted = q
        Library.addSearch(q)
        focus.clearFocus()
    }

    LaunchedEffect(Unit) { if (screen.query.isEmpty()) focusRequester.requestFocus() }
    LaunchedEffect(text) {
        if (text.isBlank() || text == submitted) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = runCatching { Repo.suggestions(text) }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Titres, artistes…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = ""; focusRequester.requestFocus() }) { Icon(Icons.Rounded.Close, "Effacer") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(text) }),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focusRequester),
        )

        when {
            text != submitted && text.isNotBlank() -> LazyColumn {
                items(suggestions) { s ->
                    ListItem(
                        headlineContent = { Text(s) },
                        leadingContent = { Icon(Icons.Rounded.Search, null) },
                        trailingContent = { IconButton(onClick = { text = s }) { Icon(Icons.Rounded.NorthWest, "Compléter") } },
                        modifier = Modifier.clickable { submit(s) },
                    )
                }
            }

            submitted.isBlank() || text.isBlank() -> LazyColumn {
                items(recentSearches) { s ->
                    ListItem(
                        headlineContent = { Text(s) },
                        leadingContent = { Icon(Icons.Rounded.History, null) },
                        trailingContent = { IconButton(onClick = { Library.removeSearch(s) }) { Icon(Icons.Rounded.Close, "Supprimer") } },
                        modifier = Modifier.clickable { submit(s) },
                    )
                }
            }

            else -> {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Repo.Filter.entries) { f ->
                        FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
                LoadContent(submitted to filter, { Repo.search(submitted, filter) }) { songs ->
                    if (songs.isEmpty()) {
                        EmptyView("Aucun résultat")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            itemsIndexed(songs, key = { i, s -> "$i${s.id}" }) { i, song ->
                                SongRow(song, onClick = {
                                    if (filter == Repo.Filter.SONGS) player.playSong(song) else player.playSongs(songs, i)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    val favorites by Library.favorites.collectAsState()
    val downloads by Library.downloads.collectAsState()
    val history by Library.history.collectAsState()
    val playlists by Library.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                "Bibliothèque",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
            )
        }
        item { Entry(Icons.Rounded.Favorite, "Favoris", "${favorites.size} titres") { nav.push(Screen.Songs(FAVORITES)) } }
        item { Entry(Icons.Rounded.Download, "Téléchargements", "${downloads.size} titres") { nav.push(Screen.Songs(DOWNLOADS)) } }
        item { Entry(Icons.Rounded.History, "Historique", "${history.size} titres") { nav.push(Screen.Songs(HISTORY)) } }
        item { SectionTitle("Mes playlists", "Nouvelle") { creating = true } }
        if (playlists.isEmpty()) item { EmptyView("Aucune playlist pour l'instant") }
        items(playlists, key = { it.id }) { pl ->
            ListItem(
                headlineContent = { Text(pl.name) },
                supportingContent = { Text("${pl.songs.size} titres") },
                leadingContent = {
                    val cover = pl.songs.firstOrNull()?.thumbnail
                    if (cover != null) Thumb(cover, 56.dp) else IconTile(Icons.AutoMirrored.Rounded.QueueMusic)
                },
                modifier = Modifier.clickable { nav.push(Screen.Songs(pl.id)) },
            )
        }
    }

    if (creating) {
        NameDialog("Nouvelle playlist", "", { creating = false }) {
            Library.createPlaylist(it)
            creating = false
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconTile(icon) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun SongsScreen(kind: String) {
    val player = LocalPlayer.current
    val nav = LocalNav.current
    val favorites by Library.favorites.collectAsState()
    val downloads by Library.downloads.collectAsState()
    val history by Library.history.collectAsState()
    val playlists by Library.playlists.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val playlist = playlists.firstOrNull { it.id == kind }
    val (title, songs) = when (kind) {
        FAVORITES -> "Favoris" to favorites
        DOWNLOADS -> "Téléchargements" to downloads
        HISTORY -> "Historique" to history
        else -> (playlist?.name ?: "") to (playlist?.songs ?: emptyList())
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(Modifier.weight(1f))
            when {
                kind == HISTORY -> IconButton(onClick = { Library.clearHistory() }) { Icon(Icons.Rounded.DeleteSweep, "Effacer") }
                playlist != null -> {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Renommer") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Supprimer") }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            collectionHeader(title, songs) {
                if (kind != DOWNLOADS) {
                    IconButton(onClick = { Downloads.download(songs) }, enabled = songs.isNotEmpty()) {
                        Icon(Icons.Rounded.Download, "Tout télécharger")
                    }
                }
            }
            if (songs.isEmpty()) item { EmptyView("Rien ici pour l'instant") }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                SongRow(
                    song,
                    onClick = { player.playSongs(songs, i) },
                    extraMenu = if (playlist != null) { close ->
                        MenuItem("Retirer de la playlist", Icons.Rounded.RemoveCircleOutline) {
                            close()
                            Library.removeFromPlaylist(playlist.id, song.id)
                        }
                    } else null,
                )
            }
        }
    }

    if (renaming && playlist != null) {
        NameDialog("Renommer", playlist.name, { renaming = false }) {
            Library.renamePlaylist(playlist.id, it)
            renaming = false
        }
    }
    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${playlist.name} » ?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    nav.pop()
                    Library.deletePlaylist(playlist.id)
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private fun LazyListScope.collectionHeader(title: String, songs: List<Song>, extraActions: @Composable () -> Unit) {
    item {
        val player = LocalPlayer.current
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            songs.firstOrNull()?.thumbnail?.let { Thumb(it, 200.dp, RoundedCornerShape(16.dp)) }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            val total = songs.sumOf { it.duration }
            Text(
                listOfNotNull("${songs.size} titres", total.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { player.playSongs(songs) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Text("Lire")
                }
                FilledTonalButton(onClick = { player.playSongs(songs.shuffled()) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Text(" Aléatoire")
                }
                extraActions()
            }
        }
    }
}
