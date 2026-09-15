package com.tvboxpremium

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import android.content.Context
import kotlin.math.abs

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

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var cardScrollOffset = 0f

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(3, 12, 25))

        drawSidebar(canvas)
        drawHeader(canvas)
        drawHero(canvas)
        drawChannels(canvas)
        drawMovies(canvas)
    }

    // ---------------------------------------------------------
    // SIDEBAR
    // ---------------------------------------------------------

    private fun drawSidebar(canvas: Canvas) {

        paint.color = Color.rgb(4, 18, 35)

        canvas.drawRect(
            0f,
            0f,
            285f,
            height.toFloat(),
            paint
        )

        // Logo
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

            // Selección/foco
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

    // ---------------------------------------------------------
    // HEADER
    // ---------------------------------------------------------

    private fun drawHeader(canvas: Canvas) {

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

    // ---------------------------------------------------------
    // HERO
    // ---------------------------------------------------------

    private fun drawHero(canvas: Canvas) {

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

        // Botón VER AHORA
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

    // ---------------------------------------------------------
    // TV EN VIVO
    // ---------------------------------------------------------

    private fun drawChannels(canvas: Canvas) {

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

            val x =
                315f +
                        index * (cardWidth + gap) -
                        cardScrollOffset

            val y = 395f

            // No dibujar tarjetas fuera de pantalla
            if (x + cardWidth < 285f || x > width) {
                return@forEachIndexed
            }

            val selected =
                selectedSection == 1 &&
                        selectedCard == index

            paint.color =
                if (selected)
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

            // Borde de foco
            if (selected) {

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f,
                        y - 2f,
                        x + cardWidth + 2f,
                        y + cardHeight + 2f
                    ),
                    13f,
                    13f,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

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

            paint.color = Color.rgb(150, 210, 255)

            canvas.drawText(
                "EN VIVO",
                x + 15f,
                y + 85f,
                paint
            )
        }
    }

    // ---------------------------------------------------------
    // PELÍCULAS
    // ---------------------------------------------------------

    private fun drawMovies(canvas: Canvas) {

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

            val x =
                315f +
                        index * (cardWidth + gap) -
                        cardScrollOffset

            val y = 575f

            if (x + cardWidth < 285f || x > width) {
                return@forEachIndexed
            }

            val selected =
                selectedSection == 2 &&
                        selectedCard == index

            paint.color = Color.rgb(
                12 + (index * 8),
                28 + (index * 4),
                48 + (index * 6)
            )

            if (selected) {
                paint.color = Color.rgb(20, 100, 180)
            }

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

            // Borde de foco
            if (selected) {

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f,
                        y - 2f,
                        x + cardWidth + 2f,
                        y + cardHeight + 2f
                    ),
                    11f,
                    11f,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

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

    // ---------------------------------------------------------
    // TOUCH
    // ---------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                touchStartX = event.x
                touchStartY = event.y

                return true
            }

            MotionEvent.ACTION_UP -> {

                val endX = event.x
                val endY = event.y

                val deltaX = endX - touchStartX
                val deltaY = endY - touchStartY

                // -------------------------------------------------
                // SWIPE HORIZONTAL
                // -------------------------------------------------

                if (abs(deltaX) > 80f && abs(deltaX) > abs(deltaY)) {

                    if (deltaX < 0) {

                        cardScrollOffset += 180f

                    } else {

                        cardScrollOffset -= 180f
                    }

                    val maxScroll = 700f

                    cardScrollOffset =
                        cardScrollOffset.coerceIn(
                            0f,
                            maxScroll
                        )

                    invalidate()

                    return true
                }

                // -------------------------------------------------
                // SWIPE VERTICAL
                // -------------------------------------------------

                if (abs(deltaY) > 80f && abs(deltaY) > abs(deltaX)) {

                    if (deltaY < 0) {

                        selectedSection++

                        if (selectedSection > sections.lastIndex) {
                            selectedSection = 0
                        }

                    } else {

                        selectedSection--

                        if (selectedSection < 0) {
                            selectedSection = sections.lastIndex
                        }
                    }

                    selectedCard = 0

                    invalidate()

                    return true
                }

                // -------------------------------------------------
                // TAP
                // -------------------------------------------------

                handleTap(endX, endY)

                return true
            }
        }

        return true
    }

    // ---------------------------------------------------------
    // TAP
    // ---------------------------------------------------------

    private fun handleTap(x: Float, y: Float) {

        // -----------------------------
        // SIDEBAR
        // -----------------------------

        if (x <= 285f) {

            val startY = 112f
            val spacing = 65f

            sections.forEachIndexed { index, _ ->

                val itemTop =
                    startY + index * spacing

                val itemBottom =
                    itemTop + 60f

                if (y >= itemTop && y <= itemBottom) {

                    selectedSection = index
                    selectedCard = 0
                    cardScrollOffset = 0f

                    invalidate()

                    return
                }
            }
        }

        // -----------------------------
        // TV CARDS
        // -----------------------------

        if (y >= 395f && y <= 510f) {

            val cardWidth = 145f
            val gap = 12f

            channels.forEachIndexed { index, _ ->

                val cardLeft =
                    315f +
                            index * (cardWidth + gap) -
                            cardScrollOffset

                val cardRight =
                    cardLeft + cardWidth

                if (x >= cardLeft && x <= cardRight) {

                    selectedSection = 1
                    selectedCard = index

                    invalidate()

                    // Futuro:
                    // abrir reproductor del canal

                    return
                }
            }
        }

        // -----------------------------
        // MOVIE CARDS
        // -----------------------------

        if (y >= 575f && y <= 740f) {

            val cardWidth = 120f
            val gap = 15f

            movies.forEachIndexed { index, _ ->

                val cardLeft =
                    315f +
                            index * (cardWidth + gap) -
                            cardScrollOffset

                val cardRight =
                    cardLeft + cardWidth

                if (x >= cardLeft && x <= cardRight) {

                    selectedSection = 2
                    selectedCard = index

                    invalidate()

                    // Futuro:
                    // abrir detalle de película

                    return
                }
            }
        }

        // -----------------------------
        // VER AHORA
        // -----------------------------

        if (
            x >= 350f &&
            x <= 505f &&
            y >= 255f &&
            y <= 305f
        ) {

            selectedSection = 1
            selectedCard = 0

            invalidate()
        }
    }

    // ---------------------------------------------------------
    // CONTROL REMOTO / TECLADO
    // ---------------------------------------------------------

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        when (keyCode) {

            // -----------------------------------------
            // ARRIBA
            // -----------------------------------------

            KeyEvent.KEYCODE_DPAD_UP -> {

                selectedSection--

                if (selectedSection < 0) {
                    selectedSection = sections.lastIndex
                }

                selectedCard = 0
                cardScrollOffset = 0f

                invalidate()

                return true
            }

            // -----------------------------------------
            // ABAJO
            // -----------------------------------------

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                selectedSection++

                if (selectedSection > sections.lastIndex) {
                    selectedSection = 0
                }

                selectedCard = 0
                cardScrollOffset = 0f

                invalidate()

                return true
            }

            // -----------------------------------------
            // IZQUIERDA
            // -----------------------------------------

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                if (
                    selectedSection == 1 ||
                    selectedSection == 2
                ) {

                    if (selectedCard > 0) {
                        selectedCard--
                    }

                } else {

                    selectedSection--

                    if (selectedSection < 0) {
                        selectedSection = sections.lastIndex
                    }

                    selectedCard = 0
                }

                invalidate()

                return true
            }

            // -----------------------------------------
            // DERECHA
            // -----------------------------------------

            KeyEvent.KEYCODE_DPAD_RIGHT -> {

                if (selectedSection == 1) {

                    if (selectedCard < channels.lastIndex) {
                        selectedCard++
                    }

                } else if (selectedSection == 2) {

                    if (selectedCard < movies.lastIndex) {
                        selectedCard++
                    }

                } else {

                    selectedSection++

                    if (selectedSection > sections.lastIndex) {
                        selectedSection = 0
                    }

                    selectedCard = 0
                }

                invalidate()

                return true
            }

            // -----------------------------------------
            // OK / ENTER
            // -----------------------------------------

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {

                handleSelection()

                return true
            }

            // -----------------------------------------
            // BACK
            // -----------------------------------------

            KeyEvent.KEYCODE_BACK -> {

                // Por ahora vuelve al inicio
                selectedSection = 0
                selectedCard = 0
                cardScrollOffset = 0f

                invalidate()

                return true
            }
        }

        return super.onKeyDown(
            keyCode,
            event
        )
    }

    // ---------------------------------------------------------
    // SELECCIÓN CON OK
    // ---------------------------------------------------------

    private fun handleSelection() {

        when (selectedSection) {

            0 -> {
                // Inicio
            }

            1 -> {
                // Futuro:
                // abrir reproductor Live TV
            }

            2 -> {
                // Futuro:
                // abrir detalle de película
            }

            3 -> {
                // Buscar
            }

            4 -> {
                // Favoritos
            }

            5 -> {
                // Configuración
            }
        }

        invalidate()
    }
}
