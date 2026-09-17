package com.tvboxpremium

import android.app.Activity
import android.graphics.Color
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

@UnstableApi
class PlayerActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
        }

        root.addView(
            playerView,
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

        root.addView(progressBar, progressParams)

        setContentView(root)

        val streamUrl = intent
            .getStringExtra("STREAM_URL")
            ?.trim()
            ?: ""

        val streamTitle = intent
            .getStringExtra("STREAM_TITLE")
            ?.trim()
            ?: "Reproduciendo"

        if (streamUrl.isEmpty()) {
            Toast.makeText(
                this,
                "URL no válida",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        title = streamTitle

        initPlayer(streamUrl)
    }

    private fun initPlayer(url: String) {

        val renderersFactory =
            DefaultRenderersFactory(this).apply {
                setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                )
                setEnableDecoderFallback(true)
            }

        val userAgent = intent.getStringExtra("STREAM_USER_AGENT")
            ?.takeIf { it.isNotBlank() }
            ?: "VLC/3.0.21"

        val httpFactory =
            DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)

        val headers = mutableMapOf<String, String>()

        intent.getStringExtra("STREAM_REFERER")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["Referer"] = it
            }

        intent.getStringExtra("STREAM_ORIGIN")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["Origin"] = it
            }

        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }

        val mediaSource = createMediaSource(
            url,
            httpFactory
        )

        exoPlayer = ExoPlayer.Builder(
            this,
            renderersFactory
        ).build().apply {

            setMediaSource(mediaSource)

            playWhenReady = true

            addListener(
                object : Player.Listener {

                    override fun onPlaybackStateChanged(
                        playbackState: Int
                    ) {
                        when (playbackState) {

                            Player.STATE_BUFFERING -> {
                                progressBar.visibility = View.VISIBLE
                            }

                            Player.STATE_READY,
                            Player.STATE_ENDED -> {
                                progressBar.visibility = View.GONE
                            }
                        }
                    }

                    override fun onIsLoadingChanged(
                        isLoading: Boolean
                    ) {
                        progressBar.visibility =
                            if (isLoading) View.VISIBLE else View.GONE
                    }

                    override fun onPlayerError(
                        error: PlaybackException
                    ) {
                        progressBar.visibility = View.GONE
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
            lowerUrl.contains("m3u8?")

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .build()

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

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "No se pudo conectar al canal"

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Tiempo de espera agotado"

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "El servidor rechazó la conexión"

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "Formato de stream no compatible"

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "No se pudo iniciar el decodificador"

            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "Error de decodificación"

            else ->
                "Source error"
        }

        Toast.makeText(
            this,
            "$message\n$causeText",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {

                exoPlayer?.let {
                    if (it.isPlaying) {
                        it.pause()
                    } else {
                        it.play()
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

        return super.onKeyDown(keyCode, event)
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
}
