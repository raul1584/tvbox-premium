package com.tvboxpremium

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.KeyEvent
import android.content.Context

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(3, 10, 20)
        window.navigationBarColor = Color.rgb(3, 10, 20)

        setContentView(HomeView(this))
    }
}

class HomeView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var selectedSection = 0
    private var selectedCard = 0

    private val sections = arrayOf(
        "Inicio",
        "TV",
        "Películas",
        "Buscar",
        "Favoritos",
        "Configuración"
    )

    private val channels = arrayOf(
        "TNT Sports",
        "ESPN",
        "CHV",
        "TVN",
        "Mega",
        "Canal 13",
        "Discovery",
        "HBO"
    )

    private val movies = arrayOf(
        "Dune",
        "Deadpool",
        "John Wick",
        "Oppenheimer",
        "Top Gun",
        "Batman",
        "Interstellar",
        "Avatar"
    )

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(3, 12, 25))

        drawSidebar(canvas)
        drawHeader(canvas)
        drawHero(canvas)
        drawChannels(canvas)
        drawMovies(canvas)
    }

    private fun drawSidebar(canvas: android.graphics.Canvas) {

        paint.color = Color.rgb(4, 18, 35)
        canvas.drawRect(0f, 0f, 285f, height.toFloat(), paint)

        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.isFakeBoldText = true

        canvas.drawText(
            "▶ TVBOX",
            35f,
            55f,
            paint
        )

        paint.textSize = 14f
        paint.color = Color.rgb(120, 190, 255)

        canvas.drawText(
            "PREMIUM",
            91f,
            76f,
            paint
        )

        val startY = 150f
        val spacing = 65f

        sections.forEachIndexed { index, section ->

            val y = startY + index * spacing

            if (index == selectedSection) {

                paint.color = Color.rgb(20, 125, 245)

                canvas.drawRoundRect(
                    RectF(
                        18f,
                        y - 38f,
                        260f,
                        y + 15f
                    ),
                    14f,
                    14f,
                    paint
                )
            }

            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.isFakeBoldText = index == selectedSection

            canvas.drawText(
                section,
                55f,
                y,
                paint
            )
        }
    }

    private fun drawHeader(canvas: android.graphics.Canvas) {

        paint.color = Color.WHITE
        paint.textSize = 17f
        paint.isFakeBoldText = false

        canvas.drawText(
            "TVBOX PREMIUM",
            330f,
            45f,
            paint
        )

        paint.color = Color.rgb(150, 170, 190)
        paint.textSize = 15f

        canvas.drawText(
            "⌕   Buscar",
            width - 220f,
            45f,
            paint
        )

        canvas.drawText(
            "◯",
            width - 55f,
            45f,
            paint
        )
    }

    private fun drawHero(canvas: android.graphics.Canvas) {

        val left = 315f
        val top = 80f
        val right = width - 35f
        val bottom = 335f

        paint.color = Color.rgb(8, 28, 48)

        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            18f,
            18f,
            paint
        )

        paint.color = Color.rgb(90, 160, 220)
        paint.textSize = 15f

        canvas.drawText(
            "CONTENIDO DESTACADO",
            left + 35f,
            top + 45f,
            paint
        )

        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.isFakeBoldText = true

        canvas.drawText(
            "TVBOX PREMIUM",
            left + 35f,
            top + 100f,
            paint
        )

        paint.textSize = 18f
        paint.isFakeBoldText = false

        canvas.drawText(
            "Tu entretenimiento en un solo lugar.",
            left + 35f,
            top + 140f,
            paint
        )

        paint.color = Color.rgb(15, 125, 245)

        canvas.drawRoundRect(
            RectF(
                left + 35f,
                top + 175f,
                left + 190f,
                top + 225f
            ),
            10f,
            10f,
            paint
        )

        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.isFakeBoldText = true

        canvas.drawText(
            "▶  VER AHORA",
            left + 55f,
            top + 207f,
            paint
        )
    }

    private fun drawChannels(canvas: android.graphics.Canvas) {

        paint.color = Color.WHITE
        paint.textSize = 23f
        paint.isFakeBoldText = true

        canvas.drawText(
            "TV EN VIVO",
            315f,
            375f,
            paint
        )

        val cardWidth = 145f
        val cardHeight = 115f
        val gap = 12f

        channels.forEachIndexed { index, channel ->

            val x = 315f + index * (cardWidth + gap)
            val y = 395f

            paint.color =
                if (index == selectedCard && selectedSection == 1)
                    Color.rgb(20, 135, 255)
                else
                    Color.rgb(8, 35, 62)

            canvas.drawRoundRect(
                RectF(
                    x,
                    y,
                    x + cardWidth,
                    y + cardHeight
                ),
                12f,
                12f,
                paint
            )

            paint.color = Color.WHITE
            paint.textSize = 15f
            paint.isFakeBoldText = true

            canvas.drawText(
                channel,
                x + 15f,
                y + 60f,
                paint
            )

            paint.textSize = 12f
            paint.isFakeBoldText = false

            canvas.drawText(
                "EN VIVO",
                x + 15f,
                y + 85f,
                paint
            )
        }
    }

    private fun drawMovies(canvas: android.graphics.Canvas) {

        paint.color = Color.WHITE
        paint.textSize = 23f
        paint.isFakeBoldText = true

        canvas.drawText(
            "PELÍCULAS POPULARES",
            315f,
            555f,
            paint
        )

        val cardWidth = 120f
        val cardHeight = 165f
        val gap = 15f

        movies.forEachIndexed { index, movie ->

            val x = 315f + index * (cardWidth + gap)
            val y = 575f

            paint.color = Color.rgb(
                12 + (index * 8),
                28 + (index * 4),
                48 + (index * 6)
            )

            canvas.drawRoundRect(
                RectF(
                    x,
                    y,
                    x + cardWidth,
                    y + cardHeight
                ),
                10f,
                10f,
                paint
            )

            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.isFakeBoldText = true

            canvas.drawText(
                movie,
                x + 10f,
                y + 90f,
                paint
            )
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_UP -> {
                selectedSection =
                    if (selectedSection > 0)
                        selectedSection - 1
                    else
                        sections.lastIndex

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                selectedSection =
                    if (selectedSection < sections.lastIndex)
                        selectedSection + 1
                    else
                        0

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selectedCard > 0) {
                    selectedCard--
                }

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selectedCard < 7) {
                    selectedCard++
                }

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }
}
