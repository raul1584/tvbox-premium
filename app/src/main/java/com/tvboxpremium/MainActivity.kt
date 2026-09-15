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
import kotlin.math.min

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

    // =========================================================
    // DIMENSIONES RESPONSIVE
    // =========================================================

    private val sidebarWidth: Float
        get() = min(width * 0.19f, 300f)

    private val contentLeft: Float
        get() = sidebarWidth + width * 0.025f

    private val contentRight: Float
        get() = width - width * 0.025f

    private val contentWidth: Float
        get() = contentRight - contentLeft

    private val scale: Float
        get() = min(width / 1920f, height / 1080f).coerceAtLeast(0.65f)

    // =========================================================
    // DIBUJO PRINCIPAL
    // =========================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(3, 12, 25))

        drawSidebar(canvas)
        drawHeader(canvas)
        drawHero(canvas)
        drawChannels(canvas)
        drawMovies(canvas)
    }

    // =========================================================
    // SIDEBAR RESPONSIVE
    // =========================================================

    private fun drawSidebar(canvas: Canvas) {

        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(4, 18, 35)

        canvas.drawRect(
            0f,
            0f,
            sidebarWidth,
            height.toFloat(),
            paint
        )

        // Logo
        paint.color = Color.WHITE
        paint.textSize = 28f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "▶ TVBOX",
            sidebarWidth * 0.13f,
            55f * scale,
            paint
        )

        paint.color = Color.rgb(120, 190, 255)
        paint.textSize = 13f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "PREMIUM",
            sidebarWidth * 0.32f,
            78f * scale,
            paint
        )

        // Línea decorativa
        paint.color = Color.rgb(20, 70, 105)

        canvas.drawRect(
            sidebarWidth * 0.10f,
            100f * scale,
            sidebarWidth * 0.90f,
            101f * scale,
            paint
        )

        val startY = 155f * scale
        val spacing = 65f * scale

        sections.forEachIndexed { index, section ->

            val y = startY + index * spacing

            if (index == selectedSection) {

                paint.color = Color.rgb(20, 125, 245)

                canvas.drawRoundRect(
                    RectF(
                        sidebarWidth * 0.07f,
                        y - 38f * scale,
                        sidebarWidth * 0.92f,
                        y + 16f * scale
                    ),
                    14f * scale,
                    14f * scale,
                    paint
                )
            }

            paint.color = Color.WHITE
            paint.textSize = 19f * scale
            paint.isFakeBoldText = index == selectedSection

            canvas.drawText(
                section,
                sidebarWidth * 0.19f,
                y,
                paint
            )
        }
    }

    // =========================================================
    // HEADER
    // =========================================================

    private fun drawHeader(canvas: Canvas) {

        paint.color = Color.WHITE
        paint.textSize = 17f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "TVBOX PREMIUM",
            contentLeft,
            45f * scale,
            paint
        )

        paint.color = Color.rgb(150, 170, 190)

        paint.textSize = 15f * scale

        canvas.drawText(
            "⌕   Buscar",
            contentRight - 150f * scale,
            45f * scale,
            paint
        )

        canvas.drawText(
            "◯",
            contentRight - 30f * scale,
            45f * scale,
            paint
        )
    }

    // =========================================================
    // HERO RESPONSIVE
    // =========================================================

    private fun drawHero(canvas: Canvas) {

        val left = contentLeft
        val top = 75f * scale

        val right = contentRight

        val heroHeight = min(
            270f * scale,
            height * 0.30f
        )

        val bottom = top + heroHeight

        paint.color = Color.rgb(8, 28, 48)

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                right,
                bottom
            ),
            20f * scale,
            20f * scale,
            paint
        )

        // Decoración lateral
        paint.color = Color.rgb(10, 70, 120)

        canvas.drawRoundRect(
            RectF(
                right - 260f * scale,
                top,
                right,
                bottom
            ),
            20f * scale,
            20f * scale,
            paint
        )

        paint.color = Color.rgb(90, 160, 220)
        paint.textSize = 15f * scale

        canvas.drawText(
            "CONTENIDO DESTACADO",
            left + 35f * scale,
            top + 45f * scale,
            paint
        )

        paint.color = Color.WHITE
        paint.textSize = 40f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "TVBOX PREMIUM",
            left + 35f * scale,
            top + 105f * scale,
            paint
        )

        paint.textSize = 18f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "Tu entretenimiento en un solo lugar.",
            left + 35f * scale,
            top + 145f * scale,
            paint
        )

        // Botón
        val buttonLeft = left + 35f * scale
        val buttonTop = top + 175f * scale
        val buttonRight = buttonLeft + 160f * scale
        val buttonBottom = buttonTop + 52f * scale

        paint.color = Color.rgb(15, 125, 245)

        canvas.drawRoundRect(
            RectF(
                buttonLeft,
                buttonTop,
                buttonRight,
                buttonBottom
            ),
            11f * scale,
            11f * scale,
            paint
        )

        paint.color = Color.WHITE
        paint.textSize = 16f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "▶  VER AHORA",
            buttonLeft + 20f * scale,
            buttonTop + 33f * scale,
            paint
        )
    }

    // =========================================================
    // TV EN VIVO
    // =========================================================

    private fun drawChannels(canvas: Canvas) {

        val heroBottom =
            75f * scale +
                    min(
                        270f * scale,
                        height * 0.30f
                    )

        val titleY = heroBottom + 45f * scale

        paint.color = Color.WHITE
        paint.textSize = 23f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "TV EN VIVO",
            contentLeft,
            titleY,
            paint
        )

        val cardTop = titleY + 18f * scale

        val availableWidth = contentWidth

        // En pantallas grandes las tarjetas crecen.
        val cardWidth =
            (availableWidth - 5f * 16f * scale) / 6f

        val finalCardWidth =
            cardWidth.coerceIn(
                125f * scale,
                230f * scale
            )

        val cardHeight = finalCardWidth * 0.68f

        val gap = 14f * scale

        channels.forEachIndexed { index, channel ->

            val x =
                contentLeft +
                        index * (finalCardWidth + gap) -
                        cardScrollOffset

            val y = cardTop

            if (x + finalCardWidth < contentLeft ||
                x > contentRight
            ) {
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
                    x + finalCardWidth,
                    y + cardHeight
                ),
                14f * scale,
                14f * scale,
                paint
            )

            if (selected) {

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * scale
                paint.color = Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f * scale,
                        y - 2f * scale,
                        x + finalCardWidth + 2f * scale,
                        y + cardHeight + 2f * scale
                    ),
                    15f * scale,
                    15f * scale,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

            // Zona grande para logo
            paint.color = Color.rgb(13, 50, 82)

            canvas.drawCircle(
                x + finalCardWidth / 2f,
                y + cardHeight * 0.38f,
                min(finalCardWidth, cardHeight) * 0.20f,
                paint
            )

            paint.color = Color.WHITE
            paint.textSize = 15f * scale
            paint.isFakeBoldText = true

            val textWidth =
                paint.measureText(channel)

            canvas.drawText(
                channel,
                x + (finalCardWidth - textWidth) / 2f,
                y + cardHeight * 0.72f,
                paint
            )

            paint.color = Color.rgb(150, 210, 255)
            paint.textSize = 11f * scale
            paint.isFakeBoldText = false

            val liveText = "● EN VIVO"

            val liveWidth =
                paint.measureText(liveText)

            canvas.drawText(
                liveText,
                x + (finalCardWidth - liveWidth) / 2f,
                y + cardHeight * 0.88f,
                paint
            )
        }
    }

    // =========================================================
    // PELÍCULAS
    // =========================================================

    private fun drawMovies(canvas: Canvas) {

        val heroBottom =
            75f * scale +
                    min(
                        270f * scale,
                        height * 0.30f
                    )

        val tvTitleY =
            heroBottom + 45f * scale

        val tvCardsBottom =
            tvTitleY +
                    18f * scale +
                    115f * scale

        val titleY =
            tvCardsBottom + 45f * scale

        paint.color = Color.WHITE
        paint.textSize = 23f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "PELÍCULAS POPULARES",
            contentLeft,
            titleY,
            paint
        )

        val cardTop =
            titleY + 18f * scale

        val availableWidth = contentWidth

        val cardWidth =
            (availableWidth - 5f * 18f * scale) / 6f

        val finalCardWidth =
            cardWidth.coerceIn(
                115f * scale,
                190f * scale
            )

        val cardHeight =
            finalCardWidth * 1.35f

        val gap = 16f * scale

        movies.forEachIndexed { index, movie ->

            val x =
                contentLeft +
                        index * (finalCardWidth + gap) -
                        cardScrollOffset

            val y = cardTop

            if (x + finalCardWidth < contentLeft ||
                x > contentRight
            ) {
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
                    x + finalCardWidth,
                    y + cardHeight
                ),
                12f * scale,
                12f * scale,
                paint
            )

            if (selected) {

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * scale
                paint.color = Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f * scale,
                        y - 2f * scale,
                        x + finalCardWidth + 2f * scale,
                        y + cardHeight + 2f * scale
                    ),
                    13f * scale,
                    13f * scale,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

            // Área visual del póster
            paint.color = Color.rgb(
                18 + index * 5,
                42 + index * 3,
                68 + index * 4
            )

            canvas.drawRoundRect(
                RectF(
                    x + 8f * scale,
                    y + 8f * scale,
                    x + finalCardWidth - 8f * scale,
                    y + cardHeight * 0.72f
                ),
                9f * scale,
                9f * scale,
                paint
            )

            paint.color = Color.WHITE
            paint.textSize = 14f * scale
            paint.isFakeBoldText = true

            canvas.drawText(
                movie,
                x + 12f * scale,
                y + cardHeight * 0.84f,
                paint
            )
        }
    }

    // =========================================================
    // TOUCH
    // =========================================================

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

                // Swipe horizontal
                if (
                    abs(deltaX) > 80f &&
                    abs(deltaX) > abs(deltaY)
                ) {

                    if (deltaX < 0f) {
                        cardScrollOffset += 220f * scale
                    } else {
                        cardScrollOffset -= 220f * scale
                    }

                    val maxScroll =
                        850f * scale

                    cardScrollOffset =
                        cardScrollOffset.coerceIn(
                            0f,
                            maxScroll
                        )

                    invalidate()

                    return true
                }

                // Swipe vertical
                if (
                    abs(deltaY) > 80f &&
                    abs(deltaY) > abs(deltaX)
                ) {

                    if (deltaY < 0f) {

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
                    cardScrollOffset = 0f

                    invalidate()

                    return true
                }

                handleTap(endX, endY)

                performClick()

                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // =========================================================
    // TAP
    // =========================================================

    private fun handleTap(x: Float, y: Float) {

        // Sidebar
        if (x <= sidebarWidth) {

            val startY = 117f * scale
            val spacing = 65f * scale

            sections.forEachIndexed { index, _ ->

                val itemTop =
                    startY + index * spacing

                val itemBottom =
                    itemTop + 65f * scale

                if (
                    y >= itemTop &&
                    y <= itemBottom
                ) {

                    selectedSection = index
                    selectedCard = 0
                    cardScrollOffset = 0f

                    invalidate()

                    return
                }
            }
        }

        // TV cards
        val heroBottom =
            75f * scale +
                    min(
                        270f * scale,
                        height * 0.30f
                    )

        val tvTitleY =
            heroBottom + 45f * scale

        val tvCardTop =
            tvTitleY + 18f * scale

        val tvCardWidth =
            (
                (contentWidth - 5f * 16f * scale) / 6f
                ).coerceIn(
                    125f * scale,
                    230f * scale
                )

        val tvCardHeight =
            tvCardWidth * 0.68f

        val tvGap = 14f * scale

        if (
            y >= tvCardTop &&
            y <= tvCardTop + tvCardHeight
        ) {

            channels.forEachIndexed { index, _ ->

                val cardLeft =
                    contentLeft +
                            index * (tvCardWidth + tvGap) -
                            cardScrollOffset

                val cardRight =
                    cardLeft + tvCardWidth

                if (
                    x >= cardLeft &&
                    x <= cardRight
                ) {

                    selectedSection = 1
                    selectedCard = index

                    invalidate()

                    return
                }
            }
        }

        // Movie cards
        val movieTitleY =
            tvCardTop +
                    tvCardHeight +
                    45f * scale

        val movieCardTop =
            movieTitleY + 18f * scale

        val movieCardWidth =
            (
                (contentWidth - 5f * 18f * scale) / 6f
                ).coerceIn(
                    115f * scale,
                    190f * scale
                )

        val movieCardHeight =
            movieCardWidth * 1.35f

        val movieGap = 16f * scale

        if (
            y >= movieCardTop &&
            y <= movieCardTop + movieCardHeight
        ) {

            movies.forEachIndexed { index, _ ->

                val cardLeft =
                    contentLeft +
                            index * (movieCardWidth + movieGap) -
                            cardScrollOffset

                val cardRight =
                    cardLeft + movieCardWidth

                if (
                    x >= cardLeft &&
                    x <= cardRight
                ) {

                    selectedSection = 2
                    selectedCard = index

                    invalidate()

                    return
                }
            }
        }

        // VER AHORA
        val heroLeft = contentLeft
        val heroTop = 75f * scale

        if (
            x >= heroLeft + 25f * scale &&
            x <= heroLeft + 230f * scale &&
            y >= heroTop + 155f * scale &&
            y <= heroTop + 245f * scale
        ) {

            selectedSection = 1
            selectedCard = 0

            invalidate()
        }
    }

    // =========================================================
    // CONTROL REMOTO
    // =========================================================

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        when (keyCode) {

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

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                if (selectedSection == 1) {

                    if (selectedCard > 0) {
                        selectedCard--
                    }

                } else if (selectedSection == 2) {

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

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {

                handleSelection()

                return true
            }

            KeyEvent.KEYCODE_BACK -> {

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

    // =========================================================
    // OK / ENTER
    // =========================================================

    private fun handleSelection() {

        when (selectedSection) {

            0 -> {
                // Inicio
            }

            1 -> {
                // Futuro: reproductor Live TV
            }

            2 -> {
                // Futuro: detalle de película
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
