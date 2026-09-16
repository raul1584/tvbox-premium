package com.tvboxpremium

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

// ============================================================
// MODELOS
// ============================================================

data class LiveCategory(
    val categoryId: String,
    val categoryName: String
)

data class LiveChannel(
    val streamId: Int,
    val name: String,
    val icon: String,
    val categoryId: String,
    val streamType: String,
    val extension: String
)

// ============================================================
// SESION XTREAM
// ============================================================

data class XtreamSession(
    val serverUrl: String,
    val username: String,
    val password: String
)

// ============================================================
// PANTALLAS
// ============================================================

enum class Screen {
    LOGIN,
    HOME,
    TV_CATEGORIES,
    TV_CHANNELS
}

// ============================================================
// ACTIVITY
// ============================================================

class MainActivity : Activity() {

    private lateinit var appView: PremiumView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(android.R.color.black)

        appView = PremiumView(this)
        setContentView(appView)
    }

    override fun onBackPressed() {

        if (!appView.handleBack()) {
            super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        if (appView.handleKey(keyCode)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }
}

// ============================================================
// VISTA PRINCIPAL CANVAS
// ============================================================

class PremiumView(
    private val context: Context
) : View(context) {

    // --------------------------------------------------------
    // COLORES
    // --------------------------------------------------------

    private val backgroundColor = Color.rgb(8, 10, 15)
    private val surfaceColor = Color.rgb(17, 20, 28)
    private val surfaceLight = Color.rgb(27, 31, 42)
    private val textColor = Color.WHITE
    private val secondaryText = Color.rgb(170, 176, 190)
    private val accentColor = Color.rgb(220, 30, 70)
    private val accentSoft = Color.rgb(150, 25, 55)
    private val selectedBorder = Color.WHITE

    // --------------------------------------------------------
    // PAINTS
    // --------------------------------------------------------

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // --------------------------------------------------------
    // SESION
    // --------------------------------------------------------

    private var session: XtreamSession? = null

    // --------------------------------------------------------
    // ESTADO
    // --------------------------------------------------------

    private var screen = Screen.LOGIN

    private var selectedSection = 0

    private var selectedCategoryIndex = 0
    private var selectedChannelIndex = 0

    private var selectedCategoryId = ""
    private var selectedCategoryName = ""

    private var categoryChannels: List<LiveChannel> = emptyList()

    // --------------------------------------------------------
    // DATOS REALES TV
    // --------------------------------------------------------

    private var liveCategories: List<LiveCategory> = emptyList()
    private var liveChannels: List<LiveChannel> = emptyList()

    // --------------------------------------------------------
    // LOGIN
    // --------------------------------------------------------

    private var loginServer = ""
    private var loginUser = ""
    private var loginPassword = ""

    private var loginField = 0

    private var loginLoading = false
    private var loginError = ""

    // --------------------------------------------------------
    // TOUCH
    // --------------------------------------------------------

    private var downX = 0f
    private var downY = 0f

    private var lastTouchTime = 0L

    // --------------------------------------------------------
    // CACHE IMAGENES
    // --------------------------------------------------------

    private val bitmapCache =
        Collections.synchronizedMap(mutableMapOf<String, Bitmap>())

    private val loadingImages =
        Collections.synchronizedSet(mutableSetOf<String>())

    // --------------------------------------------------------
    // CONSTRUCTOR
    // --------------------------------------------------------

    init {

        isFocusable = true
        isFocusableInTouchMode = true

        requestFocus()

        paint.isAntiAlias = true
        textPaint.isAntiAlias = true
    }

    // ========================================================
    // DRAW
    // ========================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(backgroundColor)

        when (screen) {

            Screen.LOGIN -> {
                drawLogin(canvas)
            }

            Screen.HOME -> {
                drawHome(canvas)
            }

            Screen.TV_CATEGORIES -> {
                drawTvCategories(canvas)
            }

            Screen.TV_CHANNELS -> {
                drawTvChannels(canvas)
            }
        }
    }

    // ========================================================
    // LOGIN
    // ========================================================

    private fun drawLogin(canvas: Canvas) {

        val w = width.toFloat()
        val h = height.toFloat()

        drawBackgroundGradient(canvas)

        // Logo
        textPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        textPaint.textSize = dp(42f)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER

        canvas.drawText(
            "TVBOX",
            w / 2f,
            dp(110f),
            textPaint
        )

        textPaint.textSize = dp(16f)
        textPaint.color = secondaryText

        canvas.drawText(
            "PREMIUM",
            w / 2f,
            dp(138f),
            textPaint
        )

        val boxWidth = min(dp(520f), w - dp(60f))
        val boxLeft = (w - boxWidth) / 2f
        val boxTop = dp(180f)

        drawRoundedRect(
            canvas,
            boxLeft,
            boxTop,
            boxLeft + boxWidth,
            boxTop + dp(350f),
            dp(20f),
            surfaceColor
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(22f)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.DEFAULT_BOLD

        canvas.drawText(
            "Iniciar sesión",
            boxLeft + dp(30f),
            boxTop + dp(45f),
            textPaint
        )

        drawLoginField(
            canvas,
            boxLeft + dp(30f),
            boxTop + dp(75f),
            boxWidth - dp(60f),
            "Servidor",
            loginServer,
            loginField == 0
        )

        drawLoginField(
            canvas,
            boxLeft + dp(30f),
            boxTop + dp(140f),
            boxWidth - dp(60f),
            "Usuario",
            loginUser,
            loginField == 1
        )

        drawLoginField(
            canvas,
            boxLeft + dp(30f),
            boxTop + dp(205f),
            boxWidth - dp(60f),
            "Contraseña",
            if (loginPassword.isEmpty()) "" else "••••••••",
            loginField == 2
        )

        val buttonTop = boxTop + dp(285f)

        drawRoundedRect(
            canvas,
            boxLeft + dp(30f),
            buttonTop,
            boxLeft + boxWidth - dp(30f),
            buttonTop + dp(48f),
            dp(12f),
            accentColor
        )

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(16f)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.DEFAULT_BOLD

        canvas.drawText(
            if (loginLoading) "Conectando..." else "ENTRAR",
            w / 2f,
            buttonTop + dp(31f),
            textPaint
        )

        if (loginError.isNotEmpty()) {

            textPaint.textSize = dp(13f)
            textPaint.color = Color.rgb(255, 100, 110)

            canvas.drawText(
                loginError,
                w / 2f,
                buttonTop + dp(75f),
                textPaint
            )
        }
    }

    private fun drawLoginField(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        label: String,
        value: String,
        selected: Boolean
    ) {

        val borderColor =
            if (selected) accentColor else Color.rgb(50, 55, 68)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) dp(2f) else dp(1f)
        paint.color = borderColor

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                left + width,
                top + dp(52f)
            ),
            dp(10f),
            dp(10f),
            paint
        )

        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(11f)
        textPaint.color = secondaryText

        canvas.drawText(
            label,
            left + dp(15f),
            top + dp(18f),
            textPaint
        )

        textPaint.textSize = dp(14f)
        textPaint.color = Color.WHITE

        canvas.drawText(
            value,
            left + dp(15f),
            top + dp(40f),
            textPaint
        )
    }

    // ========================================================
    // HOME
    // ========================================================

    private fun drawHome(canvas: Canvas) {

        drawTopBar(canvas)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = dp(34f)
        textPaint.color = Color.WHITE

        canvas.drawText(
            "Bienvenido",
            dp(55f),
            dp(110f),
            textPaint
        )

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = dp(17f)
        textPaint.color = secondaryText

        canvas.drawText(
            "Disfruta de tu contenido",
            dp(55f),
            dp(140f),
            textPaint
        )

        // TV
        drawHomeSection(
            canvas,
            0,
            dp(55f),
            dp(195f),
            dp(275f),
            dp(170f),
            "TV",
            "Televisión en directo",
            "Explorar canales"
        )

        // VOD placeholder
        drawHomeSection(
            canvas,
            1,
            dp(245f),
            dp(195f),
            dp(465f),
            dp(170f),
            "PELÍCULAS",
            "Tu catálogo VOD",
            "Próximamente"
        )

        // Indicador inferior
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(13f)
        textPaint.color = Color.rgb(125, 130, 145)

        canvas.drawText(
            "Usa ◀ ▶ ▲ ▼ y OK para navegar",
            dp(55f),
            height - dp(35f),
            textPaint
        )
    }

    private fun drawHomeSection(
        canvas: Canvas,
        index: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        title: String,
        subtitle: String,
        action: String
    ) {

        val selected = selectedSection == index

        drawRoundedRect(
            canvas,
            left,
            top,
            right,
            bottom,
            dp(18f),
            if (selected) surfaceLight else surfaceColor
        )

        if (selected) {

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3f)
            paint.color = selectedBorder

            canvas.drawRoundRect(
                RectF(left, top, right, bottom),
                dp(18f),
                dp(18f),
                paint
            )

            paint.style = Paint.Style.FILL
        }

        // icon
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(30f)
        textPaint.color = if (selected) Color.WHITE else secondaryText
        textPaint.typeface = Typeface.DEFAULT_BOLD

        canvas.drawText(
            if (index == 0) "TV" else "▶",
            (left + right) / 2f,
            top + dp(55f),
            textPaint
        )

        textPaint.textSize = dp(20f)
        textPaint.color = Color.WHITE

        canvas.drawText(
            title,
            (left + right) / 2f,
            top + dp(95f),
            textPaint
        )

        textPaint.textSize = dp(12f)
        textPaint.color = secondaryText

        canvas.drawText(
            subtitle,
            (left + right) / 2f,
            top + dp(120f),
            textPaint
        )

        textPaint.textSize = dp(11f)
        textPaint.color =
            if (selected) Color.WHITE else Color.rgb(110, 115, 130)

        canvas.drawText(
            action,
            (left + right) / 2f,
            top + dp(150f),
            textPaint
        )
    }

    // ========================================================
    // TV CATEGORIES
    // ========================================================

    private fun drawTvCategories(canvas: Canvas) {

        drawTopBar(canvas)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = dp(32f)
        textPaint.color = Color.WHITE

        canvas.drawText(
            "TV",
            dp(50f),
            dp(95f),
            textPaint
        )

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = dp(15f)
        textPaint.color = secondaryText

        val subtitle =
            if (liveCategories.isEmpty()) {
                "Cargando categorías..."
            } else {
                "${liveCategories.size} categorías disponibles"
            }

        canvas.drawText(
            subtitle,
            dp(50f),
            dp(122f),
            textPaint
        )

        if (liveCategories.isEmpty()) {

            drawLoading(
                canvas,
                width / 2f,
                height / 2f
            )

            return
        }

        val columns = categoryColumns()
        val cardWidth = dp(185f)
        val cardHeight = dp(145f)
        val gap = dp(18f)

        val startX = dp(50f)
        val startY = dp(155f)

        liveCategories.forEachIndexed { index, category ->

            val row = index / columns
            val col = index % columns

            val left =
                startX + col * (cardWidth + gap)

            val top =
                startY + row * (cardHeight + gap)

            if (top > height - dp(20f)) {
                return@forEachIndexed
            }

            val selected =
                index == selectedCategoryIndex

            drawCategoryCard(
                canvas,
                category,
                left,
                top,
                cardWidth,
                cardHeight,
                selected
            )
        }
    }

    private fun drawCategoryCard(
        canvas: Canvas,
        category: LiveCategory,
        left: Float,
        top: Float,
        cardWidth: Float,
        cardHeight: Float,
        selected: Boolean
    ) {

        drawRoundedRect(
            canvas,
            left,
            top,
            left + cardWidth,
            top + cardHeight,
            dp(14f),
            if (selected) surfaceLight else surfaceColor
        )

        if (selected) {

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3f)
            paint.color = selectedBorder

            canvas.drawRoundRect(
                RectF(
                    left,
                    top,
                    left + cardWidth,
                    top + cardHeight
                ),
                dp(14f),
                dp(14f),
                paint
            )

            paint.style = Paint.Style.FILL
        }

        val channels =
            channelsForCategory(category.categoryId)

        val logos =
            channels
                .map { it.icon }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)

        drawCategoryMosaic(
            canvas,
            logos,
            left + dp(8f),
            top + dp(8f),
            cardWidth - dp(16f),
            dp(92f),
            category.categoryName
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = dp(14f)
        textPaint.color = Color.WHITE

        val name =
            truncateText(
                category.categoryName,
                24
            )

        canvas.drawText(
            name,
            left + dp(12f),
            top + cardHeight - dp(18f),
            textPaint
        )
    }

    // ========================================================
    // MOSAICO DE LOGOS DE CATEGORIA
    // ========================================================

    private fun drawCategoryMosaic(
        canvas: Canvas,
        urls: List<String>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        categoryName: String
    ) {

        if (urls.isEmpty()) {

            drawCategoryPlaceholder(
                canvas,
                left,
                top,
                width,
                height,
                categoryName
            )

            return
        }

        val halfW = width / 2f
        val halfH = height / 2f

        val rects = listOf(
            RectF(
                left,
                top,
                left + halfW - dp(2f),
                top + halfH - dp(2f)
            ),
            RectF(
                left + halfW + dp(2f),
                top,
                left + width,
                top + halfH - dp(2f)
            ),
            RectF(
                left,
                top + halfH + dp(2f),
                left + halfW - dp(2f),
                top + height
            ),
            RectF(
                left + halfW + dp(2f),
                top + halfH + dp(2f),
                left + width,
                top + height
            )
        )

        urls.take(4).forEachIndexed { index, url ->

            requestImage(url)

            val bitmap =
                bitmapCache[url]

            drawImageOrPlaceholder(
                canvas,
                bitmap,
                rects[index],
                categoryName
            )
        }

        if (urls.size < 4) {

            for (index in urls.size until 4) {

                drawCategoryPlaceholder(
                    canvas,
                    rects[index].left,
                    rects[index].top,
                    rects[index].width(),
                    rects[index].height(),
                    categoryName
                )
            }
        }
    }

    private fun drawCategoryPlaceholder(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        title: String
    ) {

        paint.shader = LinearGradient(
            left,
            top,
            left + width,
            top + height,
            Color.rgb(35, 38, 52),
            Color.rgb(17, 19, 27),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                left + width,
                top + height
            ),
            dp(8f),
            dp(8f),
            paint
        )

        paint.shader = null

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(20f)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.DEFAULT_BOLD

        canvas.drawText(
            initials(title),
            left + width / 2f,
            top + height / 2f + dp(7f),
            textPaint
        )
    }

    // ========================================================
    // TV CHANNELS
    // ========================================================

    private fun drawTvChannels(canvas: Canvas) {

        drawTopBar(canvas)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = dp(30f)
        textPaint.color = Color.WHITE

        canvas.drawText(
            selectedCategoryName,
            dp(50f),
            dp(92f),
            textPaint
        )

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = dp(14f)
        textPaint.color = secondaryText

        canvas.drawText(
            "${categoryChannels.size} canales",
            dp(50f),
            dp(118f),
            textPaint
        )

        if (categoryChannels.isEmpty()) {

            drawEmptyState(
                canvas,
                "No hay canales en esta categoría"
            )

            return
        }

        val columns = channelColumns()

        val cardWidth = dp(205f)
        val cardHeight = dp(135f)
        val gap = dp(18f)

        val startX = dp(50f)
        val startY = dp(150f)

        categoryChannels.forEachIndexed { index, channel ->

            val row = index / columns
            val col = index % columns

            val left =
                startX + col * (cardWidth + gap)

            val top =
                startY + row * (cardHeight + gap)

            if (top > height - dp(15f)) {
                return@forEachIndexed
            }

            val selected =
                index == selectedChannelIndex

            drawChannelCard(
                canvas,
                channel,
                left,
                top,
                cardWidth,
                cardHeight,
                selected
            )
        }
    }

    private fun drawChannelCard(
        canvas: Canvas,
        channel: LiveChannel,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        selected: Boolean
    ) {

        drawRoundedRect(
            canvas,
            left,
            top,
            left + width,
            top + height,
            dp(14f),
            if (selected) surfaceLight else surfaceColor
        )

        if (selected) {

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3f)
            paint.color = selectedBorder

            canvas.drawRoundRect(
                RectF(
                    left,
                    top,
                    left + width,
                    top + height
                ),
                dp(14f),
                dp(14f),
                paint
            )

            paint.style = Paint.Style.FILL
        }

        val logoRect = RectF(
            left + dp(12f),
            top + dp(12f),
            left + dp(82f),
            top + dp(82f)
        )

        if (channel.icon.isNotBlank()) {

            requestImage(channel.icon)

            drawImageOrPlaceholder(
                canvas,
                bitmapCache[channel.icon],
                logoRect,
                channel.name
            )

        } else {

            drawChannelPlaceholder(
                canvas,
                logoRect,
                channel.name
            )
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = dp(14f)
        textPaint.color = Color.WHITE

        canvas.drawText(
            truncateText(channel.name, 25),
            left + dp(95f),
            top + dp(40f),
            textPaint
        )

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = dp(11f)
        textPaint.color = secondaryText

        canvas.drawText(
            if (channel.streamType.isNotBlank()) {
                channel.streamType.uppercase()
            } else {
                "TV"
            },
            left + dp(95f),
            top + dp(61f),
            textPaint
        )
    }

    private fun drawChannelPlaceholder(
        canvas: Canvas,
        rect: RectF,
        name: String
    ) {

        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            accentSoft,
            Color.rgb(30, 33, 45),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            rect,
            dp(10f),
            dp(10f),
            paint
        )

        paint.shader = null

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(18f)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.DEFAULT_BOLD

        canvas.drawText(
            initials(name),
            rect.centerX(),
            rect.centerY() + dp(6f),
            textPaint
        )
    }

    // ========================================================
    // TOP BAR
    // ========================================================

    private fun drawTopBar(canvas: Canvas) {

        paint.color = Color.rgb(10, 12, 18)

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            dp(55f),
            paint
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(18f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE

        canvas.drawText(
            "TVBOX",
            dp(28f),
            dp(35f),
            textPaint
        )

        textPaint.textSize = dp(11f)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = accentColor

        canvas.drawText(
            "PREMIUM",
            dp(92f),
            dp(35f),
            textPaint
        )
    }

    // ========================================================
    // NETWORK - LOGIN
    // ========================================================

    private fun performLogin() {

        if (loginLoading) return

        loginError = ""

        val server =
            loginServer.trim().removeSuffix("/")

        val user =
            loginUser.trim()

        val password =
            loginPassword

        if (server.isEmpty()) {
            loginError = "Ingresa el servidor"
            invalidate()
            return
        }

        if (user.isEmpty()) {
            loginError = "Ingresa el usuario"
            invalidate()
            return
        }

        if (password.isEmpty()) {
            loginError = "Ingresa la contraseña"
            invalidate()
            return
        }

        loginLoading = true
        invalidate()

        Thread {

            try {

                val base =
                    if (server.startsWith("http://") ||
                    server.startsWith("https://")) {
                        server
                    } else {
                        "http://$server"
                    }

                val encodedUser =
                    URLEncoder.encode(user, "UTF-8")

                val encodedPassword =
                    URLEncoder.encode(password, "UTF-8")

                val apiUrl =
                    "$base/player_api.php?username=$encodedUser&password=$encodedPassword"

                val response =
                    httpGet(apiUrl)

                val json =
                    JSONObject(response)

                val userInfo =
                    json.optJSONObject("user_info")

                val auth =
                    userInfo?.optInt("auth", 0) ?: 0

                if (auth != 1) {

                    post {

                        loginLoading = false
                        loginError = "Usuario o contraseña incorrectos"
                        invalidate()
                    }

                    return@Thread
                }

                val newSession =
                    XtreamSession(
                        serverUrl = base,
                        username = user,
                        password = password
                    )

                session = newSession

                post {

                    loginLoading = false
                    loginError = ""
                    screen = Screen.HOME
                    selectedSection = 0
                    invalidate()
                }

                loadTvData()

            } catch (e: Exception) {

                post {

                    loginLoading = false
                    loginError =
                        "No se pudo conectar al servidor"
                    invalidate()
                }
            }

        }.start()
    }

    // ========================================================
    // CARGAR TV
    // ========================================================

    private fun loadTvData() {

        val currentSession =
            session ?: return

        Thread {

            try {

                val categoriesUrl =
                    buildApiUrl(
                        currentSession,
                        "get_live_categories"
                    )

                val streamsUrl =
                    buildApiUrl(
                        currentSession,
                        "get_live_streams"
                    )

                val categoriesResponse =
                    httpGet(categoriesUrl)

                val streamsResponse =
                    httpGet(streamsUrl)

                val categories =
                    parseLiveCategories(
                        categoriesResponse
                    )

                val channels =
                    parseLiveChannels(
                        streamsResponse
                    )

                synchronized(this) {

                    liveCategories =
                        categories
                            .distinctBy { it.categoryId }
                            .sortedBy {
                                it.categoryName.lowercase()
                            }

                    liveChannels =
                        channels
                            .distinctBy { it.streamId }
                            .sortedBy {
                                it.name.lowercase()
                            }
                }

                post {
                    invalidate()
                }

            } catch (e: Exception) {

                post {

                    if (screen == Screen.TV_CATEGORIES) {

                        Toast.makeText(
                            context,
                            "No se pudieron cargar las categorías",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    invalidate()
                }
            }

        }.start()
    }

    private fun buildApiUrl(
        currentSession: XtreamSession,
        action: String
    ): String {

        val user =
            URLEncoder.encode(
                currentSession.username,
                "UTF-8"
            )

        val password =
            URLEncoder.encode(
                currentSession.password,
                "UTF-8"
            )

        return "${currentSession.serverUrl}/player_api.php" +
                "?username=$user" +
                "&password=$password" +
                "&action=$action"
    }

    // ========================================================
    // PARSE CATEGORIAS
    // ========================================================

    private fun parseLiveCategories(
        response: String
    ): List<LiveCategory> {

        val result = mutableListOf<LiveCategory>()

        val array =
            JSONArray(response)

        for (i in 0 until array.length()) {

            val obj =
                array.optJSONObject(i)
                    ?: continue

            val id =
                obj.optString(
                    "category_id",
                    ""
                ).trim()

            val name =
                obj.optString(
                    "category_name",
                    ""
                ).trim()

            if (id.isNotEmpty() && name.isNotEmpty()) {

                result.add(
                    LiveCategory(
                        categoryId = id,
                        categoryName = name
                    )
                )
            }
        }

        return result
    }

    // ========================================================
    // PARSE CANALES
    // ========================================================

    private fun parseLiveChannels(
        response: String
    ): List<LiveChannel> {

        val result = mutableListOf<LiveChannel>()

        val array =
            JSONArray(response)

        for (i in 0 until array.length()) {

            val obj =
                array.optJSONObject(i)
                    ?: continue

            val streamId =
                try {
                    obj.optInt(
                        "stream_id",
                        0
                    )
                } catch (_: Exception) {
                    0
                }

            val name =
                obj.optString(
                    "name",
                    "Canal"
                ).trim()

            val icon =
                obj.optString(
                    "stream_icon",
                    ""
                ).trim()

            val categoryId =
                obj.optString(
                    "category_id",
                    ""
                ).trim()

            val streamType =
                obj.optString(
                    "stream_type",
                    ""
                ).trim()

            val extension =
                obj.optString(
                    "container_extension",
                    "ts"
                ).trim()

            if (streamId > 0) {

                result.add(
                    LiveChannel(
                        streamId = streamId,
                        name = name,
                        icon = icon,
                        categoryId = categoryId,
                        streamType = streamType,
                        extension = extension
                    )
                )
            }
        }

        return result
    }

    // ========================================================
    // NETWORK HTTP
    // ========================================================

    private fun httpGet(
        urlString: String
    ): String {

        val connection =
            URL(urlString)
                .openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )
            connection.setRequestProperty(
                "User-Agent",
                "TVBoxPremium/1.0"
            )

            val code =
                connection.responseCode

            if (code !in 200..299) {
                throw Exception(
                    "HTTP $code"
                )
            }

            val input =
                connection.inputStream

            BufferedReader(
                InputStreamReader(input)
            ).use { reader ->

                val builder =
                    StringBuilder()

                var line: String?

                while (true) {

                    line =
                        reader.readLine()
                            ?: break

                    builder.append(line)
                }

                return builder.toString()
            }

        } finally {

            connection.disconnect()
        }
    }

    // ========================================================
    // IMAGENES
    // ========================================================

    private fun requestImage(
        url: String
    ) {

        if (url.isBlank()) return

        synchronized(bitmapCache) {

            if (bitmapCache.containsKey(url)) {
                return
            }
        }

        synchronized(loadingImages) {

            if (loadingImages.contains(url)) {
                return
            }

            loadingImages.add(url)
        }

        Thread {

            var connection:
                    HttpURLConnection? = null

            try {

                connection =
                    URL(url)
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.instanceFollowRedirects = true

                connection.setRequestProperty(
                    "User-Agent",
                    "TVBoxPremium/1.0"
                )

                val bitmap =
                    connection.inputStream.use {
                        BitmapFactory.decodeStream(it)
                    }

                if (bitmap != null) {

                    synchronized(bitmapCache) {

                        bitmapCache[url] = bitmap
                    }
                }

            } catch (_: Exception) {

                // Imagen no disponible.
                // Se utiliza placeholder.

            } finally {

                connection?.disconnect()

                synchronized(loadingImages) {
                    loadingImages.remove(url)
                }

                post {
                    invalidate()
                }
            }

        }.start()
    }

    // ========================================================
    // DIBUJAR IMAGEN
    // ========================================================

    private fun drawImageOrPlaceholder(
        canvas: Canvas,
        bitmap: Bitmap?,
        rect: RectF,
        title: String
    ) {

        if (bitmap == null) {

            drawCategoryPlaceholder(
                canvas,
                rect.left,
                rect.top,
                rect.width(),
                rect.height(),
                title
            )

            return
        }

        canvas.save()

        val path =
            Path()

        path.addRoundRect(
            rect,
            dp(8f),
            dp(8f),
            Path.Direction.CW
        )

        canvas.clipPath(path)

        canvas.drawBitmap(
            bitmap,
            null,
            rect,
            paint
        )

        canvas.restore()
    }

    // ========================================================
    // CATEGORIA / CANALES
    // ========================================================

    private fun channelsForCategory(
        categoryId: String
    ): List<LiveChannel> {

        return liveChannels.filter {
            it.categoryId == categoryId
        }
    }

    private fun openSelectedCategory() {

        if (liveCategories.isEmpty()) return

        selectedCategoryIndex =
            selectedCategoryIndex.coerceIn(
                0,
                liveCategories.lastIndex
            )

        val category =
            liveCategories[selectedCategoryIndex]

        selectedCategoryId =
            category.categoryId

        selectedCategoryName =
            category.categoryName

        categoryChannels =
            channelsForCategory(
                category.categoryId
            )
                .sortedBy {
                    it.name.lowercase()
                }

        selectedChannelIndex = 0

        screen = Screen.TV_CHANNELS

        invalidate()
    }

    // ========================================================
    // NAVEGACION TECLADO / D-PAD
    // ========================================================

    fun handleKey(
        keyCode: Int
    ): Boolean {

        when (screen) {

            Screen.LOGIN -> {

                return handleLoginKey(
                    keyCode
                )
            }

            Screen.HOME -> {

                return handleHomeKey(
                    keyCode
                )
            }

            Screen.TV_CATEGORIES -> {

                return handleCategoryKey(
                    keyCode
                )
            }

            Screen.TV_CHANNELS -> {

                return handleChannelKey(
                    keyCode
                )
            }
        }
    }

    private fun handleLoginKey(
        keyCode: Int
    ): Boolean {

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_UP -> {

                loginField =
                    max(
                        0,
                        loginField - 1
                    )

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                loginField =
                    min(
                        3,
                        loginField + 1
                    )

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {

                if (loginField == 3) {
                    performLogin()
                } else {
                    loginField++
                }

                invalidate()
                return true
            }
        }

        return false
    }

    private fun handleHomeKey(
        keyCode: Int
    ): Boolean {

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                selectedSection =
                    max(
                        0,
                        selectedSection - 1
                    )

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {

                selectedSection =
                    min(
                        1,
                        selectedSection + 1
                    )

                invalidate()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {

                if (selectedSection == 0) {

                    screen =
                        Screen.TV_CATEGORIES

                    selectedCategoryIndex = 0

                    if (liveCategories.isEmpty()) {
                        loadTvData()
                    }
                }

                invalidate()
                return true
            }
        }

        return false
    }

    private fun handleCategoryKey(
        keyCode: Int
    ): Boolean {

        if (liveCategories.isEmpty()) {
            return true
        }

        val columns =
            categoryColumns()

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                if (selectedCategoryIndex % columns > 0) {

                    selectedCategoryIndex--

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {

                if (
                    selectedCategoryIndex <
                    liveCategories.lastIndex
                ) {

                    selectedCategoryIndex++

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {

                val newIndex =
                    selectedCategoryIndex - columns

                if (newIndex >= 0) {

                    selectedCategoryIndex =
                        newIndex

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                val newIndex =
                    selectedCategoryIndex + columns

                if (
                    newIndex <=
                    liveCategories.lastIndex
                ) {

                    selectedCategoryIndex =
                        newIndex

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {

                openSelectedCategory()

                return true
            }
        }

        return false
    }

    private fun handleChannelKey(
        keyCode: Int
    ): Boolean {

        if (categoryChannels.isEmpty()) {
            return true
        }

        val columns =
            channelColumns()

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                if (selectedChannelIndex % columns > 0) {

                    selectedChannelIndex--

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {

                if (
                    selectedChannelIndex <
                    categoryChannels.lastIndex
                ) {

                    selectedChannelIndex++

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {

                val newIndex =
                    selectedChannelIndex - columns

                if (newIndex >= 0) {

                    selectedChannelIndex =
                        newIndex

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                val newIndex =
                    selectedChannelIndex + columns

                if (
                    newIndex <=
                    categoryChannels.lastIndex
                ) {

                    selectedChannelIndex =
                        newIndex

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {

                // PLAYBACK SE IMPLEMENTARÁ EN LA SIGUIENTE ETAPA.
                // No abrimos PlayerActivity todavía.

                Toast.makeText(
                    context,
                    "Canal seleccionado",
                    Toast.LENGTH_SHORT
                ).show()

                return true
            }
        }

        return false
    }

    // ========================================================
    // BACK
    // ========================================================

    fun handleBack(): Boolean {

        when (screen) {

            Screen.TV_CHANNELS -> {

                screen =
                    Screen.TV_CATEGORIES

                invalidate()

                return true
            }

            Screen.TV_CATEGORIES -> {

                screen =
                    Screen.HOME

                invalidate()

                return true
            }

            Screen.HOME -> {

                return false
            }

            Screen.LOGIN -> {

                return false
            }
        }
    }

    // ========================================================
    // TOUCH
    // ========================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                downX = event.x
                downY = event.y

                return true
            }

            MotionEvent.ACTION_UP -> {

                val upX = event.x
                val upY = event.y

                val dx =
                    upX - downX

                val dy =
                    upY - downY

                val absX =
                    kotlin.math.abs(dx)

                val absY =
                    kotlin.math.abs(dy)

                if (
                    absX > dp(40f) ||
                    absY > dp(40f)
                ) {

                    if (screen == Screen.TV_CATEGORIES) {

                        if (absX > absY) {

                            if (dx < 0) {
                                moveCategoryRight()
                            } else {
                                moveCategoryLeft()
                            }

                        } else {

                            if (dy < 0) {
                                moveCategoryDown()
                            } else {
                                moveCategoryUp()
                            }
                        }

                    } else if (
                        screen == Screen.TV_CHANNELS
                    ) {

                        if (absX > absY) {

                            if (dx < 0) {
                                moveChannelRight()
                            } else {
                                moveChannelLeft()
                            }

                        } else {

                            if (dy < 0) {
                                moveChannelDown()
                            } else {
                                moveChannelUp()
                            }
                        }
                    }

                    return true
                }

                handleTap(
                    upX,
                    upY
                )

                return true
            }
        }

        return true
    }

    private fun handleTap(
        x: Float,
        y: Float
    ) {

        when (screen) {

            Screen.HOME -> {

                val tvRect =
                    RectF(
                        dp(55f),
                        dp(195f),
                        dp(225f),
                        dp(365f)
                    )

                if (tvRect.contains(x, y)) {

                    selectedSection = 0

                    screen =
                        Screen.TV_CATEGORIES

                    if (liveCategories.isEmpty()) {
                        loadTvData()
                    }

                    invalidate()
                }
            }

            Screen.TV_CATEGORIES -> {

                val columns =
                    categoryColumns()

                val cardWidth =
                    dp(185f)

                val cardHeight =
                    dp(145f)

                val gap =
                    dp(18f)

                val startX =
                    dp(50f)

                val startY =
                    dp(155f)

                liveCategories.forEachIndexed { index, _ ->

                    val row =
                        index / columns

                    val col =
                        index % columns

                    val left =
                        startX +
                                col *
                                (cardWidth + gap)

                    val top =
                        startY +
                                row *
                                (cardHeight + gap)

                    val rect =
                        RectF(
                            left,
                            top,
                            left + cardWidth,
                            top + cardHeight
                        )

                    if (rect.contains(x, y)) {

                        selectedCategoryIndex =
                            index

                        openSelectedCategory()

                        return
                    }
                }
            }

            Screen.TV_CHANNELS -> {

                val columns =
                    channelColumns()

                val cardWidth =
                    dp(205f)

                val cardHeight =
                    dp(135f)

                val gap =
                    dp(18f)

                val startX =
                    dp(50f)

                val startY =
                    dp(150f)

                categoryChannels.forEachIndexed { index, channel ->

                    val row =
                        index / columns

                    val col =
                        index % columns

                    val left =
                        startX +
                                col *
                                (cardWidth + gap)

                    val top =
                        startY +
                                row *
                                (cardHeight + gap)

                    val rect =
                        RectF(
                            left,
                            top,
                            left + cardWidth,
                            top + cardHeight
                        )

                    if (rect.contains(x, y)) {

                        selectedChannelIndex =
                            index

                        Toast.makeText(
                            context,
                            "Canal seleccionado",
                            Toast.LENGTH_SHORT
                        ).show()

                        invalidate()

                        return
                    }
                }
            }

            Screen.LOGIN -> {

                // Login por teclado/mando.
            }
        }
    }

    // ========================================================
    // MOVIMIENTO TOUCH
    // ========================================================

    private fun moveCategoryLeft() {

        if (selectedCategoryIndex % categoryColumns() > 0) {
            selectedCategoryIndex--
            invalidate()
        }
    }

    private fun moveCategoryRight() {

        if (selectedCategoryIndex < liveCategories.lastIndex) {
            selectedCategoryIndex++
            invalidate()
        }
    }

    private fun moveCategoryUp() {

        val columns =
            categoryColumns()

        val newIndex =
            selectedCategoryIndex - columns

        if (newIndex >= 0) {
            selectedCategoryIndex = newIndex
            invalidate()
        }
    }

    private fun moveCategoryDown() {

        val columns =
            categoryColumns()

        val newIndex =
            selectedCategoryIndex + columns

        if (newIndex <= liveCategories.lastIndex) {
            selectedCategoryIndex = newIndex
            invalidate()
        }
    }

    private fun moveChannelLeft() {

        if (selectedChannelIndex % channelColumns() > 0) {
            selectedChannelIndex--
            invalidate()
        }
    }

    private fun moveChannelRight() {

        if (selectedChannelIndex < categoryChannels.lastIndex) {
            selectedChannelIndex++
            invalidate()
        }
    }

    private fun moveChannelUp() {

        val columns =
            channelColumns()

        val newIndex =
            selectedChannelIndex - columns

        if (newIndex >= 0) {
            selectedChannelIndex = newIndex
            invalidate()
        }
    }

    private fun moveChannelDown() {

        val columns =
            channelColumns()

        val newIndex =
            selectedChannelIndex + columns

        if (newIndex <= categoryChannels.lastIndex) {
            selectedChannelIndex = newIndex
            invalidate()
        }
    }

    // ========================================================
    // HELPERS UI
    // ========================================================

    private fun drawRoundedRect(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        color: Int
    ) {

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                right,
                bottom
            ),
            radius,
            radius,
            paint
        )
    }

    private fun drawBackgroundGradient(
        canvas: Canvas
    ) {

        paint.shader =
            LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                Color.rgb(8, 10, 15),
                Color.rgb(20, 12, 20),
                Shader.TileMode.CLAMP
            )

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            paint
        )

        paint.shader = null
    }

    private fun drawLoading(
        canvas: Canvas,
        centerX: Float,
        centerY: Float
    ) {

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(16f)
        textPaint.color = secondaryText

        canvas.drawText(
            "Cargando TV...",
            centerX,
            centerY,
            textPaint
        )
    }

    private fun drawEmptyState(
        canvas: Canvas,
        message: String
    ) {

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(17f)
        textPaint.color = secondaryText

        canvas.drawText(
            message,
            width / 2f,
            height / 2f,
            textPaint
        )
    }

    private fun categoryColumns(): Int {

        return when {

            width >= dp(1200f) -> 5

            width >= dp(900f) -> 4

            else -> 3
        }
    }

    private fun channelColumns(): Int {

        return when {

            width >= dp(1200f) -> 5

            width >= dp(900f) -> 4

            else -> 3
        }
    }

    private fun truncateText(
        value: String,
        maxLength: Int
    ): String {

        if (value.length <= maxLength) {
            return value
        }

        return value
            .take(maxLength - 1)
            .trimEnd() + "…"
    }

    private fun initials(
        value: String
    ): String {

        val words =
            value.trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        if (words.isEmpty()) {
            return "TV"
        }

        return if (words.size == 1) {

            words[0]
                .take(2)
                .uppercase()

        } else {

            words
                .take(2)
                .joinToString("") {
                    it.first()
                        .uppercase()
                }
        }
    }

    private fun dp(
        value: Float
    ): Float {

        return value *
                resources.displayMetrics.density
    }
}
