package com.tvboxpremium

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@UnstableApi
class PlayerActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null

    private var libVLC: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var vlcVideoLayout: VLCVideoLayout
    private lateinit var progressBar: ProgressBar

    private var streamUrl: String = ""
    private var streamTitle: String = "Reproduciendo"
    private var userAgent: String = "VLC/3.0.21"
    private var referer: String? = null
    private var origin: String? = null

    private var isLive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        @Suppress("DEPRECATION")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        streamUrl = intent
            .getStringExtra("STREAM_URL")
            ?.trim()
            .orEmpty()

        streamTitle = intent
            .getStringExtra("STREAM_TITLE")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Reproduciendo"

        userAgent = intent
            .getStringExtra("STREAM_USER_AGENT")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "VLC/3.0.21"

        referer = intent
            .getStringExtra("STREAM_REFERER")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        origin = intent
            .getStringExtra("STREAM_ORIGIN")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (streamUrl.isEmpty()) {
            Toast.makeText(
                this,
                "URL no válida",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        title = streamTitle

        isLive = streamUrl
            .lowercase()
            .contains("/live/")

        createInterface()
    }

    private fun createInterface() {

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
            visibility = if (isLive) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        vlcVideoLayout = VLCVideoLayout(this).apply {
            keepScreenOn = true
            visibility = if (isLive) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        root.addView(
            vlcVideoLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        progressBar = ProgressBar(this)

        val progressParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        root.addView(
            progressBar,
            progressParams
        )

        setContentView(root)
    }

    private fun initPlayer() {

        if (isLive) {
            initVlcPlayer()
        } else {
            initMedia3Player()
        }
    }

    private fun initVlcPlayer() {

        if (vlcPlayer != null) {
            return
        }

        progressBar.visibility = View.VISIBLE

        val options = arrayListOf<String>()

        options.add("--network-caching=1000")
        options.add("--live-caching=1000")
        options.add("--http-reconnect")
        options.add("--clock-jitter=0")
        options.add("--clock-synchro=0")

        libVLC = LibVLC(
            this,
            options
        )

        vlcPlayer = MediaPlayer(
            libVLC
        )

        vlcPlayer?.setEventListener { event ->

            runOnUiThread {

                when (event.type) {

                    MediaPlayer.Event.Opening -> {
                        progressBar.visibility = View.VISIBLE
                    }

                    MediaPlayer.Event.Buffering -> {
                        progressBar.visibility = View.VISIBLE
                    }

                    MediaPlayer.Event.Playing -> {
                        progressBar.visibility = View.GONE
                    }

                    MediaPlayer.Event.Paused -> {
                        progressBar.visibility = View.GONE
                    }

                    MediaPlayer.Event.Stopped -> {
                        progressBar.visibility = View.GONE
                    }

                    MediaPlayer.Event.EndReached -> {
                        progressBar.visibility = View.GONE
                    }

                    MediaPlayer.Event.EncounteredError -> {

                        progressBar.visibility = View.GONE

                        Toast.makeText(
                            this,
                            "Error de reproducción Live",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        vlcPlayer?.attachViews(
            vlcVideoLayout,
            null,
            false,
            false
        )

        val media = Media(
            libVLC,
            streamUrl
        )

        media.addOption(
            ":network-caching=1000"
        )

        media.addOption(
            ":live-caching=1000"
        )

        media.addOption(
            ":http-user-agent=$userAgent"
        )

        referer?.let {
            media.addOption(
                ":http-referrer=$it"
            )
        }

        origin?.let {
            media.addOption(
                ":http-origin=$it"
            )
        }

        media.addOption(
            ":http-reconnect=true"
        )

        media.addOption(
            ":clock-jitter=0"
        )

        media.addOption(
            ":clock-synchro=0"
        )

        vlcPlayer?.media = media

        media.release()

        vlcPlayer?.play()
    }

    private fun initMedia3Player() {

        if (exoPlayer != null) {
            return
        }

        progressBar.visibility = View.VISIBLE

        val renderersFactory =
            DefaultRenderersFactory(this).apply {

                setExtensionRendererMode(
                    DefaultRenderersFactory
                        .EXTENSION_RENDERER_MODE_PREFER
                )

                setEnableDecoderFallback(true)
            }

        val httpFactory =
            DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)

        val headers = mutableMapOf<String, String>()

        referer?.let {
            headers["Referer"] = it
        }

        origin?.let {
            headers["Origin"] = it
        }

        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(
                headers
            )
        }

        val mediaSource = createMediaSource(
            streamUrl,
            httpFactory
        )

        exoPlayer = ExoPlayer.Builder(
            this,
            renderersFactory
        )
            .build()
            .apply {

                setMediaSource(mediaSource)

                playWhenReady = true

                addListener(
                    object : Player.Listener {

                        override fun onPlaybackStateChanged(
                            playbackState: Int
                        ) {

                            when (playbackState) {

                                Player.STATE_BUFFERING -> {
                                    progressBar.visibility =
                                        View.VISIBLE
                                }

                                Player.STATE_READY -> {
                                    progressBar.visibility =
                                        View.GONE
                                }

                                Player.STATE_ENDED -> {
                                    progressBar.visibility =
                                        View.GONE
                                }
                            }
                        }

                        override fun onPlayerError(
                            error: PlaybackException
                        ) {

                            progressBar.visibility =
                                View.GONE

                            showPlaybackError(error)
                        }
                    }
                )

                prepare()
            }

        playerView.player = exoPlayer
    }

    private fun createMediaSource(
        url: String,
        httpFactory: DefaultHttpDataSource.Factory
    ): MediaSource {

        val lowerUrl = url.lowercase()

        val isHls =
            lowerUrl.contains(".m3u8") ||
            lowerUrl.contains("m3u8?") ||
            lowerUrl.contains("/hls/")

        val mediaItem = MediaItem.fromUri(url)

        return if (isHls) {

            HlsMediaSource.Factory(httpFactory)
                .setAllowChunklessPreparation(false)
                .createMediaSource(mediaItem)

        } else {

            ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(mediaItem)
        }
    }

    private fun showPlaybackError(
        error: PlaybackException
    ) {

        val cause = error.cause

        val causeText =
            cause?.message
                ?: cause?.javaClass?.simpleName
                ?: error.message
                ?: "Error desconocido"

        val message = when (error.errorCode) {

            PlaybackException
                .ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "No se pudo conectar al canal"

            PlaybackException
                .ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Tiempo de espera agotado"

            PlaybackException
                .ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "El servidor rechazó la conexión"

            PlaybackException
                .ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "Formato de stream no compatible"

            PlaybackException
                .ERROR_CODE_DECODER_INIT_FAILED ->
                "No se pudo iniciar el decodificador"

            PlaybackException
                .ERROR_CODE_DECODING_FAILED ->
                "Error de decodificación"

            else ->
                "Error de reproducción"
        }

        Toast.makeText(
            this,
            "$message\n$causeText",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun releasePlayer() {

        vlcPlayer?.let { player ->

            try {
                player.stop()
            } catch (_: Exception) {
            }

            try {
                player.detachViews()
            } catch (_: Exception) {
            }

            try {
                player.release()
            } catch (_: Exception) {
            }
        }

        vlcPlayer = null

        libVLC?.let { vlc ->

            try {
                vlc.release()
            } catch (_: Exception) {
            }
        }

        libVLC = null

        exoPlayer?.let { player ->

            try {
                player.release()
            } catch (_: Exception) {
            }
        }

        exoPlayer = null
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= 24) {
            initPlayer()
        }
    }

    override fun onResume() {
        super.onResume()

        if (
            Build.VERSION.SDK_INT < 24 ||
            (exoPlayer == null && vlcPlayer == null)
        ) {
            initPlayer()
        }
    }

    override fun onPause() {
        super.onPause()

        if (Build.VERSION.SDK_INT < 24) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()

        if (Build.VERSION.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    override fun dispatchKeyEvent(
        event: KeyEvent
    ): Boolean {

        return if (isLive) {
            super.dispatchKeyEvent(event)
        } else {
            playerView.dispatchKeyEvent(event) ||
                super.dispatchKeyEvent(event)
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {

        if (isLive) {

            when (keyCode) {

                KeyEvent.KEYCODE_BACK -> {

                    releasePlayer()
                    finish()

                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {

                    vlcPlayer?.let { player ->

                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    }

                    return true
                }
            }

            return super.onKeyDown(
                keyCode,
                event
            )
        }

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {

                exoPlayer?.let { player ->

                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {

                exoPlayer?.seekBack()

                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {

                exoPlayer?.seekForward()

                return true
            }

            KeyEvent.KEYCODE_BACK -> {

                releasePlayer()
                finish()

                return true
            }
        }

        return super.onKeyDown(
            keyCode,
            event
        )
    }
}
