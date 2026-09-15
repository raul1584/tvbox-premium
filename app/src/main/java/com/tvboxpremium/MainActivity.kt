package com.tvboxpremium

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(2, 8, 16)
        window.navigationBarColor = Color.rgb(2, 8, 16)

        setContentView(HomeView(this))
    }
}

class HomeView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // =========================================================
    // NAVEGACIÓN
    // =========================================================

    private var selectedSection = 0

    /*
        0 = Hero
        1 = TV
        2 = Películas
        3 = menú lateral
    */
    private var focusZone = 0

    private var selectedCard = 0

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var tvScroll = 0f
    private var movieScroll = 0f

    // =========================================================
    // DATOS DEMO
    // =========================================================

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
    // MÉTRICAS RESPONSIVE
    // =========================================================

    private val sidebarWidth: Float
        get() = min(
            width * 0.19f,
            310f
        )

    private val horizontalMargin: Float
        get() = max(
            22f,
            width * 0.025f
        )

    private val contentLeft: Float
        get() = sidebarWidth + horizontalMargin

    private val contentRight: Float
        get() = width - horizontalMargin

    private val contentWidth: Float
        get() = contentRight - contentLeft

    private val scale: Float
        get() = min(
            width / 1920f,
            height / 1080f
        ).coerceAtLeast(0.70f)

    // =========================================================
    // DIBUJO
    // =========================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(
            Color.rgb(
                2,
                9,
                18
            )
        )

        drawBackground(canvas)
        drawSidebar(canvas)
        drawHeader(canvas)
        drawHero(canvas)
        drawLiveSection(canvas)
        drawMoviesSection(canvas)
    }

    // =========================================================
    // FONDO
    // =========================================================

    private fun drawBackground(canvas: Canvas) {

        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(
            2,
            9,
            18
        )

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            paint
        )

        // Luz superior derecha
        paint.color = Color.rgb(
            4,
            25,
            45
        )

        canvas.drawCircle(
            width * 0.88f,
            height * 0.10f,
            width * 0.25f,
            paint
        )

        // Luz inferior
        paint.color = Color.rgb(
            3,
            20,
            36
        )

        canvas.drawCircle(
            width * 0.78f,
            height * 0.95f,
            width * 0.30f,
            paint
        )
    }

    // =========================================================
    // SIDEBAR
    // =========================================================

    private fun drawSidebar(canvas: Canvas) {

        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(
            4,
            16,
            29
        )

        canvas.drawRect(
            0f,
            0f,
            sidebarWidth,
            height.toFloat(),
            paint
        )

        // Línea derecha
        paint.color = Color.rgb(
            16,
            47,
            72
        )

        canvas.drawRect(
            sidebarWidth - 1f,
            0f,
            sidebarWidth,
            height.toFloat(),
            paint
        )

        // LOGO
        paint.color = Color.WHITE
        paint.textSize = 27f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "▶ TVBOX",
            sidebarWidth * 0.12f,
            55f * scale,
            paint
        )

        paint.color = Color.rgb(
            55,
            165,
            255
        )

        paint.textSize = 12f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "PREMIUM",
            sidebarWidth * 0.31f,
            77f * scale,
            paint
        )

        // Línea
        paint.color = Color.rgb(
            20,
            60,
            90
        )

        canvas.drawRect(
            sidebarWidth * 0.10f,
            102f * scale,
            sidebarWidth * 0.90f,
            103f * scale,
            paint
        )

        val startY = 155f * scale
        val spacing = 64f * scale

        sections.forEachIndexed { index, title ->

            val y =
                startY +
                        index * spacing

            val focused =
                focusZone == 3 &&
                        selectedSection == index

            if (focused) {

                paint.color = Color.rgb(
                    18,
                    112,
                    218
                )

                canvas.drawRoundRect(
                    RectF(
                        sidebarWidth * 0.07f,
                        y - 35f * scale,
                        sidebarWidth * 0.93f,
                        y + 17f * scale
                    ),
                    13f * scale,
                    13f * scale,
                    paint
                )

                // Barra lateral de foco
                paint.color = Color.rgb(
                    80,
                    190,
                    255
                )

                canvas.drawRoundRect(
                    RectF(
                        sidebarWidth * 0.07f,
                        y - 27f * scale,
                        sidebarWidth * 0.09f,
                        y + 9f * scale
                    ),
                    3f * scale,
                    3f * scale,
                    paint
                )
            }

            paint.color =
                if (focused)
                    Color.WHITE
                else
                    Color.rgb(
                        190,
                        205,
                        220
                    )

            paint.textSize =
                if (focused)
                    19f * scale
                else
                    18f * scale

            paint.isFakeBoldText = focused

            canvas.drawText(
                title,
                sidebarWidth * 0.20f,
                y,
                paint
            )
        }
    }

    // =========================================================
    // HEADER
    // =========================================================

    private fun drawHeader(canvas: Canvas) {

        paint.color = Color.rgb(
            190,
            205,
            220
        )

        paint.textSize = 14f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "TVBOX PREMIUM",
            contentLeft,
            42f * scale,
            paint
        )

        // Buscar
        paint.color = Color.rgb(
            130,
            150,
            170
        )

        paint.textSize = 15f * scale

        canvas.drawText(
            "⌕  Buscar",
            contentRight - 150f * scale,
            42f * scale,
            paint
        )

        // Perfil
        paint.color = Color.rgb(
            170,
            190,
            210
        )

        canvas.drawCircle(
            contentRight - 18f * scale,
            36f * scale,
            13f * scale,
            paint
        )
    }

    // =========================================================
    // HERO
    // =========================================================

    private fun drawHero(canvas: Canvas) {

        val left = contentLeft
        val top = 65f * scale
        val right = contentRight

        val heroHeight = min(
            285f * scale,
            height * 0.31f
        )

        val bottom = top + heroHeight

        // Fondo
        paint.color = Color.rgb(
            6,
            22,
            40
        )

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                right,
                bottom
            ),
            22f * scale,
            22f * scale,
            paint
        )

        // Zona luminosa derecha
        paint.color = Color.rgb(
            7,
            50,
            84
        )

        val path = Path()

        path.moveTo(
            right - 520f * scale,
            top
        )

        path.lineTo(
            right,
            top
        )

        path.lineTo(
            right,
            bottom
        )

        path.lineTo(
            right - 260f * scale,
            bottom
        )

        path.close()

        canvas.drawPath(
            path,
            paint
        )

        // Círculos decorativos
        paint.color = Color.rgb(
            10,
            76,
            125
        )

        canvas.drawCircle(
            right - 210f * scale,
            top + heroHeight * 0.48f,
            120f * scale,
            paint
        )

        paint.color = Color.rgb(
            16,
            103,
            165
        )

        canvas.drawCircle(
            right - 210f * scale,
            top + heroHeight * 0.48f,
            68f * scale,
            paint
        )

        // Play
        paint.color = Color.argb(
            55,
            255,
            255,
            255
        )

        canvas.drawCircle(
            right - 210f * scale,
            top + heroHeight * 0.48f,
            88f * scale,
            paint
        )

        paint.color = Color.WHITE

        val play = Path()

        val px =
            right - 210f * scale

        val py =
            top + heroHeight * 0.48f

        play.moveTo(
            px - 22f * scale,
            py - 35f * scale
        )

        play.lineTo(
            px + 40f * scale,
            py
        )

        play.lineTo(
            px - 22f * scale,
            py + 35f * scale
        )

        play.close()

        canvas.drawPath(
            play,
            paint
        )

        // Texto pequeño
        paint.color = Color.rgb(
            75,
            180,
            255
        )

        paint.textSize = 14f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "CONTENIDO DESTACADO",
            left + 36f * scale,
            top + 42f * scale,
            paint
        )

        // Título
        paint.color = Color.WHITE

        paint.textSize = 40f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "TVBOX PREMIUM",
            left + 36f * scale,
            top + 100f * scale,
            paint
        )

        // Subtítulo
        paint.color = Color.rgb(
            205,
            220,
            235
        )

        paint.textSize = 18f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "Tu entretenimiento en un solo lugar.",
            left + 36f * scale,
            top + 140f * scale,
            paint
        )

        // Botón
        val buttonLeft =
            left + 36f * scale

        val buttonTop =
            top + 175f * scale

        val buttonRight =
            buttonLeft + 168f * scale

        val buttonBottom =
            buttonTop + 52f * scale

        val focused =
            focusZone == 0

        paint.color =
            if (focused)
                Color.rgb(
                    35,
                    145,
                    255
                )
            else
                Color.rgb(
                    15,
                    115,
                    225
                )

        canvas.drawRoundRect(
            RectF(
                buttonLeft,
                buttonTop,
                buttonRight,
                buttonBottom
            ),
            12f * scale,
            12f * scale,
            paint
        )

        if (focused) {

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * scale
            paint.color = Color.WHITE

            canvas.drawRoundRect(
                RectF(
                    buttonLeft - 2f * scale,
                    buttonTop - 2f * scale,
                    buttonRight + 2f * scale,
                    buttonBottom + 2f * scale
                ),
                13f * scale,
                13f * scale,
                paint
            )

            paint.style = Paint.Style.FILL
        }

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

    private fun drawLiveSection(canvas: Canvas) {

        val heroBottom =
            65f * scale +
                    min(
                        285f * scale,
                        height * 0.31f
                    )

        val titleY =
            heroBottom +
                    43f * scale

        paint.color = Color.WHITE
        paint.textSize = 23f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "TV EN VIVO",
            contentLeft,
            titleY,
            paint
        )

        paint.color = Color.rgb(
            110,
            140,
            165
        )

        paint.textSize = 12f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "Canales disponibles",
            contentLeft + 135f * scale,
            titleY,
            paint
        )

        val top =
            titleY +
                    17f * scale

        val gap =
            14f * scale

        val cardWidth =
            (
                contentWidth -
                        5f * gap
                ) / 6f

        val finalWidth =
            cardWidth.coerceIn(
                135f * scale,
                235f * scale
            )

        val cardHeight =
            finalWidth * 0.66f

        channels.forEachIndexed { index, channel ->

            val x =
                contentLeft +
                        index *
                        (finalWidth + gap) -
                        tvScroll

            if (
                x + finalWidth < contentLeft ||
                x > contentRight
            ) {
                return@forEachIndexed
            }

            val focused =
                focusZone == 1 &&
                        selectedCard == index

            // Card
            paint.color =
                if (focused)
                    Color.rgb(
                        18,
                        104,
                        190
                    )
                else
                    Color.rgb(
                        8,
                        30,
                        51
                    )

            canvas.drawRoundRect(
                RectF(
                    x,
                    top,
                    x + finalWidth,
                    top + cardHeight
                ),
                15f * scale,
                15f * scale,
                paint
            )

            // Foco
            if (focused) {

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * scale
                paint.color = Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f * scale,
                        top - 2f * scale,
                        x + finalWidth + 2f * scale,
                        top + cardHeight + 2f * scale
                    ),
                    16f * scale,
                    16f * scale,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

            // Área de logo
            paint.color =
                if (focused)
                    Color.rgb(
                        26,
                        125,
                        215
                    )
                else
                    Color.rgb(
                        14,
                        50,
                        78
                    )

            canvas.drawCircle(
                x + finalWidth / 2f,
                top + cardHeight * 0.37f,
                min(
                    finalWidth,
                    cardHeight
                ) * 0.22f,
                paint
            )

            // Símbolo play
            paint.color = Color.WHITE

            val miniPlay =
                Path()

            val cx =
                x + finalWidth / 2f

            val cy =
                top + cardHeight * 0.37f

            miniPlay.moveTo(
                cx - 8f * scale,
                cy - 12f * scale
            )

            miniPlay.lineTo(
                cx + 12f * scale,
                cy
            )

            miniPlay.lineTo(
                cx - 8f * scale,
                cy + 12f * scale
            )

            miniPlay.close()

            canvas.drawPath(
                miniPlay,
                paint
            )

            // Nombre
            paint.color = Color.WHITE
            paint.textSize = 14f * scale
            paint.isFakeBoldText = true

            val textWidth =
                paint.measureText(channel)

            canvas.drawText(
                channel,
                x + (finalWidth - textWidth) / 2f,
                top + cardHeight * 0.73f,
                paint
            )

            // Estado
            paint.color = Color.rgb(
                125,
                195,
                255
            )

            paint.textSize = 10f * scale
            paint.isFakeBoldText = false

            val live =
                "● EN VIVO"

            val liveWidth =
                paint.measureText(live)

            canvas.drawText(
                live,
                x + (finalWidth - liveWidth) / 2f,
                top + cardHeight * 0.89f,
                paint
            )
        }

        // Indicadores
        drawPageIndicators(
            canvas,
            contentRight - 70f * scale,
            titleY - 5f * scale,
            8,
            selectedCard
        )
    }

    // =========================================================
    // PELÍCULAS
    // =========================================================

    private fun drawMoviesSection(canvas: Canvas) {

        val heroBottom =
            65f * scale +
                    min(
                        285f * scale,
                        height * 0.31f
                    )

        val liveTitle =
            heroBottom +
                    43f * scale

        val liveTop =
            liveTitle +
                    17f * scale

        val liveWidth =
            (
                (contentWidth - 5f * 14f * scale) / 6f
                ).coerceIn(
                    135f * scale,
                    235f * scale
                )

        val liveHeight =
            liveWidth * 0.66f

        val titleY =
            liveTop +
                    liveHeight +
                    48f * scale

        paint.color = Color.WHITE
        paint.textSize = 23f * scale
        paint.isFakeBoldText = true

        canvas.drawText(
            "PELÍCULAS POPULARES",
            contentLeft,
            titleY,
            paint
        )

        paint.color = Color.rgb(
            110,
            140,
            165
        )

        paint.textSize = 12f * scale
        paint.isFakeBoldText = false

        canvas.drawText(
            "Recomendadas para ti",
            contentLeft + 245f * scale,
            titleY,
            paint
        )

        val top =
            titleY +
                    17f * scale

        val gap =
            16f * scale

        val cardWidth =
            (
                contentWidth -
                        5f * gap
                ) / 6f

        val finalWidth =
            cardWidth.coerceIn(
                120f * scale,
                195f * scale
            )

        val cardHeight =
            finalWidth * 1.34f

        movies.forEachIndexed { index, movie ->

            val x =
                contentLeft +
                        index *
                        (finalWidth + gap) -
                        movieScroll

            if (
                x + finalWidth < contentLeft ||
                x > contentRight
            ) {
                return@forEachIndexed
            }

            val focused =
                focusZone == 2 &&
                        selectedCard == index

            // Poster
            paint.color =
                Color.rgb(
                    10 + index * 4,
                    26 + index * 4,
                    45 + index * 5
                )

            if (focused) {
                paint.color =
                    Color.rgb(
                        20,
                        80,
                        135
                    )
            }

            canvas.drawRoundRect(
                RectF(
                    x,
                    top,
                    x + finalWidth,
                    top + cardHeight
                ),
                12f * scale,
                12f * scale,
                paint
            )

            // Imagen placeholder
            paint.color =
                Color.rgb(
                    18 + index * 4,
                    43 + index * 3,
                    68 + index * 4
                )

            canvas.drawRoundRect(
                RectF(
                    x + 7f * scale,
                    top + 7f * scale,
                    x + finalWidth - 7f * scale,
                    top + cardHeight * 0.77f
                ),
                9f * scale,
                9f * scale,
                paint
            )

            // Icono
            paint.color = Color.argb(
                55,
                255,
                255,
                255
            )

            canvas.drawCircle(
                x + finalWidth / 2f,
                top + cardHeight * 0.34f,
                28f * scale,
                paint
            )

            // Foco
            if (focused) {

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * scale
                paint.color = Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f * scale,
                        top - 2f * scale,
                        x + finalWidth + 2f * scale,
                        top + cardHeight + 2f * scale
                    ),
                    13f * scale,
                    13f * scale,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

            // Título
            paint.color = Color.WHITE
            paint.textSize = 13f * scale
            paint.isFakeBoldText = true

            canvas.drawText(
                movie,
                x + 11f * scale,
                top + cardHeight * 0.87f,
                paint
            )
        }

        drawPageIndicators(
            canvas,
            contentRight - 70f * scale,
            titleY - 5f * scale,
            8,
            selectedCard
        )
    }

    // =========================================================
    // INDICADORES
    // =========================================================

    private fun drawPageIndicators(
        canvas: Canvas,
        x: Float,
        y: Float,
        total: Int,
        selected: Int
    ) {

        val size = 5f * scale
        val gap = 10f * scale

        for (i in 0 until min(total, 6)) {

            paint.color =
                if (i == selected)
                    Color.rgb(
                        40,
                        150,
                        255
                    )
                else
                    Color.rgb(
                        80,
                        105,
                        125
                    )

            canvas.drawCircle(
                x + i * gap,
                y,
                if (i == selected)
                    size * 1.25f
                else
                    size * 0.75f,
                paint
            )
        }
    }

    // =========================================================
    // TOUCH
    // =========================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                touchStartX = event.x
                touchStartY = event.y

                return true
            }

            MotionEvent.ACTION_UP -> {

                val x = event.x
                val y = event.y

                val dx =
                    x - touchStartX

                val dy =
                    y - touchStartY

                // Swipe horizontal
                if (
                    abs(dx) > 70f &&
                    abs(dx) > abs(dy)
                ) {

                    if (focusZone == 1) {

                        tvScroll +=
                            if (dx < 0)
                                220f * scale
                            else
                                -220f * scale

                        tvScroll =
                            tvScroll.coerceIn(
                                0f,
                                900f * scale
                            )

                    } else if (focusZone == 2) {

                        movieScroll +=
                            if (dx < 0)
                                220f * scale
                            else
                                -220f * scale

                        movieScroll =
                            movieScroll.coerceIn(
                                0f,
                                900f * scale
                            )
                    }

                    invalidate()

                    return true
                }

                handleTouch(x, y)

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
    // TOUCH HIT TEST
    // =========================================================

    private fun handleTouch(
        x: Float,
        y: Float
    ) {

        // Sidebar
        if (x <= sidebarWidth) {

            val startY =
                120f * scale

            val spacing =
                64f * scale

            sections.forEachIndexed { index, _ ->

                val top =
                    startY +
                            index * spacing

                val bottom =
                    top +
                            60f * scale

                if (
                    y >= top &&
                    y <= bottom
                ) {

                    selectedSection = index
                    selectedCard = 0
                    focusZone = 3

                    tvScroll = 0f
                    movieScroll = 0f

                    invalidate()

                    return
                }
            }
        }

        // Hero
        val heroTop =
            65f * scale

        val heroHeight =
            min(
                285f * scale,
                height * 0.31f
            )

        val buttonTop =
            heroTop +
                    165f * scale

        if (
            x >= contentLeft &&
            x <= contentLeft + 240f * scale &&
            y >= buttonTop &&
            y <= buttonTop + 70f * scale
        ) {

            selectedSection = 1
            selectedCard = 0
            focusZone = 1

            invalidate()

            return
        }

        // TV
        val liveTitle =
            heroTop +
                    heroHeight +
                    43f * scale

        val liveTop =
            liveTitle +
                    17f * scale

        val gap =
            14f * scale

        val liveWidth =
            (
                (contentWidth - 5f * gap) / 6f
                ).coerceIn(
                    135f * scale,
                    235f * scale
                )

        val liveHeight =
            liveWidth * 0.66f

        if (
            y >= liveTop &&
            y <= liveTop + liveHeight
        ) {

            channels.forEachIndexed { index, _ ->

                val cardLeft =
                    contentLeft +
                            index *
                            (liveWidth + gap) -
                            tvScroll

                if (
                    x >= cardLeft &&
                    x <= cardLeft + liveWidth
                ) {

                    selectedSection = 1
                    selectedCard = index
                    focusZone = 1

                    invalidate()

                    return
                }
            }
        }

        // Películas
        val movieTitle =
            liveTop +
                    liveHeight +
                    48f * scale

        val movieTop =
            movieTitle +
                    17f * scale

        val movieGap =
            16f * scale

        val movieWidth =
            (
                (contentWidth - 5f * movieGap) / 6f
                ).coerceIn(
                    120f * scale,
                    195f * scale
                )

        val movieHeight =
            movieWidth * 1.34f

        if (
            y >= movieTop &&
            y <= movieTop + movieHeight
        ) {

            movies.forEachIndexed { index, _ ->

                val cardLeft =
                    contentLeft +
                            index *
                            (movieWidth + movieGap) -
                            movieScroll

                if (
                    x >= cardLeft &&
                    x <= cardLeft + movieWidth
                ) {

                    selectedSection = 2
                    selectedCard = index
                    focusZone = 2

                    invalidate()

                    return
                }
            }
        }
    }

    // =========================================================
    // D-PAD
    // =========================================================

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_RIGHT -> {

                when (focusZone) {

                    0 -> {
                        focusZone = 1
                        selectedCard = 0
                    }

                    1 -> {

                        if (
                            selectedCard <
                            channels.lastIndex
                        ) {

                            selectedCard++

                            keepTvCardVisible()
                        }
                    }

                    2 -> {

                        if (
                            selectedCard <
                            movies.lastIndex
                        ) {

                            selectedCard++

                            keepMovieCardVisible()
                        }
                    }

                    3 -> {
                        focusZone = 0
                    }
                }

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                when (focusZone) {

                    1 -> {

                        if (selectedCard > 0) {

                            selectedCard--

                            keepTvCardVisible()

                        } else {

                            focusZone = 3
                        }
                    }

                    2 -> {

                        if (selectedCard > 0) {

                            selectedCard--

                            keepMovieCardVisible()

                        } else {

                            focusZone = 3
                        }
                    }

                    3 -> {
                        // Ya estamos en menú
                    }

                    else -> {
                        focusZone = 3
                    }
                }

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                when (focusZone) {

                    0 -> {
                        focusZone = 1
                        selectedCard = 0
                    }

                    1 -> {
                        focusZone = 2
                        selectedCard = 0
                    }

                    2 -> {
                        focusZone = 3
                        selectedSection = 0
                    }

                    3 -> {

                        selectedSection++

                        if (
                            selectedSection >
                            sections.lastIndex
                        ) {
                            selectedSection = 0
                        }
                    }
                }

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {

                when (focusZone) {

                    0 -> {
                        focusZone = 3
                    }

                    1 -> {
                        focusZone = 0
                    }

                    2 -> {
                        focusZone = 1
                        selectedCard = 0
                    }

                    3 -> {

                        selectedSection--

                        if (
                            selectedSection < 0
                        ) {
                            selectedSection =
                                sections.lastIndex
                        }
                    }
                }

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {

                handleEnter()

                return true
            }

            KeyEvent.KEYCODE_BACK -> {

                // Comportamiento estilo TV:
                // si estamos en contenido,
                // volvemos al menú.

                if (focusZone != 3) {

                    focusZone = 3

                    invalidate()

                    return true
                }

                // En Inicio no cerramos
                // accidentalmente la interfaz.

                selectedSection = 0
                selectedCard = 0
                focusZone = 0

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
    // VISIBILIDAD TV
    // =========================================================

    private fun keepTvCardVisible() {

        val gap =
            14f * scale

        val cardWidth =
            (
                (contentWidth - 5f * gap) / 6f
                ).coerceIn(
                    135f * scale,
                    235f * scale
                )

        val position =
            selectedCard *
                    (cardWidth + gap)

        val visibleRight =
            contentWidth -
                    cardWidth

        if (
            position - tvScroll >
            visibleRight
        ) {

            tvScroll =
                position -
                        visibleRight
        }

        if (
            position - tvScroll < 0f
        ) {

            tvScroll = position
        }

        tvScroll =
            tvScroll.coerceAtLeast(0f)
    }

    // =========================================================
    // VISIBILIDAD PELÍCULAS
    // =========================================================

    private fun keepMovieCardVisible() {

        val gap =
            16f * scale

        val cardWidth =
            (
                (contentWidth - 5f * gap) / 6f
                ).coerceIn(
                    120f * scale,
                    195f * scale
                )

        val position =
            selectedCard *
                    (cardWidth + gap)

        val visibleRight =
            contentWidth -
                    cardWidth

        if (
            position - movieScroll >
            visibleRight
        ) {

            movieScroll =
                position -
                        visibleRight
        }

        if (
            position - movieScroll < 0f
        ) {

            movieScroll = position
        }

        movieScroll =
            movieScroll.coerceAtLeast(0f)
    }

    // =========================================================
    // ENTER
    // =========================================================

    private fun handleEnter() {

        when (focusZone) {

            0 -> {
                // VER AHORA
                focusZone = 1
                selectedCard = 0
            }

            1 -> {
                // Futuro:
                // abrir reproductor Live TV
            }

            2 -> {
                // Futuro:
                // abrir detalle VOD
            }

            3 -> {

                when (selectedSection) {

                    0 -> {
                        focusZone = 0
                    }

                    1 -> {
                        focusZone = 1
                        selectedCard = 0
                    }

                    2 -> {
                        focusZone = 2
                        selectedCard = 0
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
            }
        }

        invalidate()
    }
}
