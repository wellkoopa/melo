package com.louis.melo.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.louis.melo.player.AndroidAudioEngine
import com.louis.melo.player.AudioEngine
import java.io.File

@SuppressLint("StaticFieldLeak")
object AndroidPlatform {
    lateinit var context: Context
        private set

    /** Call once from Application.onCreate. */
    fun init(context: Context) {
        this.context = context.applicationContext
        Library.init()
    }
}

actual fun dataDir(): String = AndroidPlatform.context.filesDir.absolutePath

actual fun fileUri(path: String): String = Uri.fromFile(File(path)).toString()

actual fun createAudioEngine(): AudioEngine = AndroidAudioEngine(AndroidPlatform.context)

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
