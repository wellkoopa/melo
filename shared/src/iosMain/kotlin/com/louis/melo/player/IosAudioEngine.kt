package com.louis.melo.player

import com.louis.melo.data.Song
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatus
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.NSObjectProtocol

/** AVPlayer-based engine with lock-screen / Control Center integration and background audio. */
@OptIn(ExperimentalForeignApi::class)
class IosAudioEngine : AudioEngine {
    override var listener: AudioEngine.Listener? = null

    private val player = AVPlayer()
    private val scope: CoroutineScope = MainScope()
    private var endObserver: NSObjectProtocol? = null
    private var song: Song? = null
    private var artwork: MPMediaItemArtwork? = null
    private var failedReported = false

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
        setupRemoteCommands()
        scope.launch {
            while (true) {
                val item = player.currentItem
                if (item != null && item.status == AVPlayerItemStatusFailed && !failedReported) {
                    failedReported = true
                    listener?.onError(item.error?.localizedDescription ?: "lecture impossible")
                }
                updateNowPlaying()
                delay(1000)
            }
        }
    }

    override fun load(uri: String, song: Song) {
        this.song = song
        artwork = null
        failedReported = false
        val url = NSURL.URLWithString(uri) ?: return listener?.onError("URL invalide") ?: Unit
        val item = AVPlayerItem(uRL = url)
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> listener?.onEnded() }
        player.replaceCurrentItemWithPlayerItem(item)
        player.play()
        loadArtwork(song)
        updateNowPlaying()
    }

    override fun play() {
        player.play()
        updateNowPlaying()
    }

    override fun pause() {
        player.pause()
        updateNowPlaying()
    }

    override fun stop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }

    override fun seekTo(ms: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, 1000))
    }

    override val positionMs: Long
        get() = seconds(CMTimeGetSeconds(player.currentTime()))

    override val durationMs: Long
        get() = player.currentItem?.let { seconds(CMTimeGetSeconds(it.duration)) } ?: 0

    override val isPlaying: Boolean
        get() = player.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override val isBuffering: Boolean
        get() = player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate

    private fun seconds(s: Double): Long = if (s.isNaN() || s.isInfinite()) 0 else (s * 1000).toLong()

    // ---- lock screen / Control Center ----
    private fun setupRemoteCommands() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        fun ok(): MPRemoteCommandHandlerStatus = MPRemoteCommandHandlerStatusSuccess
        center.playCommand.addTargetWithHandler { _ -> play(); ok() }
        center.pauseCommand.addTargetWithHandler { _ -> pause(); ok() }
        center.togglePlayPauseCommand.addTargetWithHandler { _ -> if (isPlaying) pause() else play(); ok() }
        center.nextTrackCommand.addTargetWithHandler { _ -> listener?.onNext(); ok() }
        center.previousTrackCommand.addTargetWithHandler { _ -> listener?.onPrevious(); ok() }
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            (event as? MPChangePlaybackPositionCommandEvent)?.let { seekTo((it.positionTime * 1000).toLong()) }
            ok()
        }
    }

    private fun updateNowPlaying() {
        val s = song ?: return
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to s.title,
            MPMediaItemPropertyArtist to s.artist,
            MPMediaItemPropertyPlaybackDuration to durationMs / 1000.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to positionMs / 1000.0,
            MPNowPlayingInfoPropertyPlaybackRate to if (isPlaying) 1.0 else 0.0,
        )
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    private fun loadArtwork(song: Song) {
        val url = song.thumbnail?.let { NSURL.URLWithString(it) } ?: return
        scope.launch {
            val image = withContext(Dispatchers.Default) {
                NSData.dataWithContentsOfURL(url)?.let { UIImage(data = it) }
            } ?: return@launch
            if (this@IosAudioEngine.song?.id != song.id) return@launch
            val size = image.size
            artwork = MPMediaItemArtwork(boundsSize = size) { _ -> image }
            updateNowPlaying()
        }
    }
}
