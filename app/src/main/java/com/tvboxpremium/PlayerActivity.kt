package com.tvboxpremium

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pantalla completa y mantener la pantalla encendida
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.BLACK)

        playerView = PlayerView(this)
        playerView.useController = true
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
            gravity = android.view.Gravity.CENTER
        }
        root.addView(progressBar, progressParams)

        setContentView(root)

        val streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        val streamTitle = intent.getStringExtra("STREAM_TITLE") ?: "Reproduciendo"

        if (streamUrl.isEmpty()) {
            Toast.makeText(this, "URL no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Toast.makeText(this, streamTitle, Toast.LENGTH_SHORT).show()
        initPlayer(streamUrl)
    }

    private fun initPlayer(url: String) {
        // Decodificación por software prioritaria para procesadores de TV Box chinas
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        exoPlayer = ExoPlayer.Builder(this, renderersFactory).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        progressBar.visibility = View.GONE
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        progressBar.visibility = View.VISIBLE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@PlayerActivity,
                        "Error en el stream: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }

        playerView.player = exoPlayer
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                exoPlayer?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                exoPlayer?.seekBack()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
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
