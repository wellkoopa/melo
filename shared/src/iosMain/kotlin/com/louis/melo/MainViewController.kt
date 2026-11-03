package com.louis.melo

import androidx.compose.ui.window.ComposeUIViewController
import com.louis.melo.data.Library
import com.louis.melo.data.Repo
import com.louis.melo.player.PlayerController
import com.louis.melo.ui.App
import platform.UIKit.UIViewController

private val player: PlayerController by lazy {
    Repo.requireM4a = true
    Library.init()
    PlayerController()
}

/** Entry point called from Swift (iosApp/Melo/MeloApp.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { App(player) }
