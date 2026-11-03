package com.louis.melo.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.louis.melo.data.Song

/** One ExoPlayer, exposed to the system through [PlaybackService] for the notification and lock screen. */
class AndroidAudioEngine(private val context: Context) : AudioEngine {
    override var listener: AudioEngine.Listener? = null

    private val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) listener?.onEnded()
                }

                override fun onPlayerError(error: PlaybackException) {
                    listener?.onError(error.errorCodeName)
                }
            })
        }

    /** What the media session sees: next/previous are forwarded to the shared queue. */
    @OptIn(UnstableApi::class)
    val sessionPlayer: Player = object : ForwardingPlayer(exo) {
        private val extra = setOf(
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*extra.toIntArray()).build()

        override fun isCommandAvailable(command: Int) = command in extra || super.isCommandAvailable(command)
        override fun hasNextMediaItem() = true
        override fun hasPreviousMediaItem() = true
        override fun seekToNext() { listener?.onNext() }
        override fun seekToNextMediaItem() { listener?.onNext() }
        override fun seekToPrevious() { listener?.onPrevious() }
        override fun seekToPreviousMediaItem() { listener?.onPrevious() }
    }

    init {
        instance = this
    }

    override fun load(uri: String, song: Song) {
        val item = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.thumbnail?.let(Uri::parse))
                    .build()
            )
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.play()
        // Media3 promotes the service to foreground once it is started and playing.
        context.startService(Intent(context, PlaybackService::class.java))
    }

    override fun play() {
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        exo.play()
    }

    override fun pause() = exo.pause()
    override fun stop() = exo.stop()
    override fun seekTo(ms: Long) = exo.seekTo(ms)

    override val positionMs get() = exo.currentPosition
    override val durationMs get() = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
    override val isPlaying get() = exo.isPlaying
    override val isBuffering get() = exo.playbackState == Player.STATE_BUFFERING

    companion object {
        var instance: AndroidAudioEngine? = null
            private set
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val engine = AndroidAudioEngine.instance ?: return
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val builder = MediaSession.Builder(this, engine.sessionPlayer)
        if (launch != null) {
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
