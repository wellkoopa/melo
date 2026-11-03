package com.louis.melo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.louis.melo.data.Messages
import com.louis.melo.data.PlatformBackHandler
import com.louis.melo.data.Repo
import com.louis.melo.player.LocalPlayer
import com.louis.melo.player.PlayerController

/** Screens of the app; a tiny back stack replaces a navigation library. */
sealed interface Screen {
    data object Home : Screen
    data class Search(val query: String = "", val filter: Repo.Filter = Repo.Filter.SONGS) : Screen
    data object Library : Screen
    data class Songs(val kind: String) : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun push(screen: Screen) {
        stack.add(screen)
    }
    fun pop(): Boolean = if (stack.size > 1) {
        stack.removeAt(stack.lastIndex)
        true
    } else false
    fun tab(root: Screen) {
        stack.clear()
        stack.add(root)
    }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

private data class Tab(val root: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Home, "Accueil", Icons.Rounded.Home),
    Tab(Screen.Search(), "Recherche", Icons.Rounded.Search),
    Tab(Screen.Library, "Bibliothèque", Icons.Rounded.LibraryMusic),
)

private val colors = darkColorScheme(
    primary = Color(0xFF9FD3FF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF1D4A66),
    onPrimaryContainer = Color(0xFFCDE6FF),
    secondary = Color(0xFFC9B6FF),
    background = Color(0xFF0B0B0F),
    surface = Color(0xFF0B0B0F),
)

@Composable
fun App(player: PlayerController) {
    val nav = remember { Navigator() }
    val snackbar = remember { SnackbarHostState() }
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { Messages.flow.collect { snackbar.showSnackbar(it) } }
    PlatformBackHandler(enabled = playerOpen || nav.stack.size > 1) {
        if (playerOpen) playerOpen = false else nav.pop()
    }

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalPlayer provides player, LocalNav provides nav) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        Column {
                            if (player.current != null) MiniPlayer(onOpen = { playerOpen = true })
                            NavigationBar {
                                tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = nav.stack.first()::class == tab.root::class,
                                        onClick = { nav.tab(tab.root) },
                                        icon = { Icon(tab.icon, null) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (val screen = nav.current) {
                            Screen.Home -> HomeScreen()
                            is Screen.Search -> SearchScreen(screen)
                            Screen.Library -> LibraryScreen()
                            is Screen.Songs -> SongsScreen(screen.kind)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = playerOpen && player.current != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    PlayerScreen(onClose = { playerOpen = false })
                }
            }
        }
    }
}
