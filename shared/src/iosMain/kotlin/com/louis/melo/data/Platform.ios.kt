package com.louis.melo.data

import androidx.compose.runtime.Composable
import com.louis.melo.player.AudioEngine
import com.louis.melo.player.IosAudioEngine
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

actual fun dataDir(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

actual fun fileUri(path: String): String = NSURL.fileURLWithPath(path).absoluteString ?: "file://$path"

actual fun createAudioEngine(): AudioEngine = IosAudioEngine()

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/** iOS has no system back button; screens provide their own back arrows. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
