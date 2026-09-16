package com.tvboxpremium

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import kotlin.math.abs
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

// --- MODELOS VOD ---
data class VodCategory(
    val categoryId: String,
    val categoryName: String
)

data class VodMovie(
    val streamId: Int,
    val name: String,
    val icon: String,
    val rating: String,
    val categoryId: String,
    val containerExtension: String
)

data class VodMovieDetail(
    val streamId: Int,
    val title: String,
    val posterUrl: String,
    val year: String,
    val duration: String,
    val rating: String,
    val plot: String,
    val cast: String,
    val director: String,
    val containerExtension: String
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
    TV_CHANNELS,
    VOD_CATEGORIES,
    VOD_MOVIES,
    VOD_MOVIE_DETAIL
}

// ============================================================
// ACTIVITY
// ============================================================

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var appView: PremiumView
    private lateinit var keyboardInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(android.R.color.black)

        root = FrameLayout(this)

        appView = PremiumView(
            context = this,
            activity = this
        )

        root.addView(
            appView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        keyboardInput = EditText(this)
        keyboardInput.setBackgroundColor(Color.TRANSPARENT)
        keyboardInput.setTextColor(Color.TRANSPARENT)
        keyboardInput.setCursorVisible(false)
        keyboardInput.alpha = 0.01f

        val keyboardParams = FrameLayout.LayoutParams(2, 2)
        keyboardParams.gravity = Gravity.BOTTOM or Gravity.START
        keyboardParams.leftMargin = 1
        keyboardParams.bottomMargin = 1

        root.addView(keyboardInput, keyboardParams)

        keyboardInput.addTextChangedListener(object : TextWatcher {
            private var internalChange = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (internalChange) return
                appView.updateLoginText(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        setContentView(root)
    }

    fun openKeyboardForField(field: Int, value: String) {
        keyboardInput.inputType = if (field == 2) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL or InputType.TYPE_TEXT_VARIATION_URI
        }

        keyboardInput.setText(value)
        keyboardInput.setSelection(keyboardInput.text.length)
        keyboardInput.requestFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(keyboardInput.windowToken, 0)
        appView.requestFocus()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        appView.invalidate()
        appView.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("session_active", appView.hasActiveSession())
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
// VISTA CANVAS
// ============================================================

class PremiumView(
    private val context: Context,
    private val activity: MainActivity
) : View(context) {

    private val backgroundColor = Color.rgb(7, 9, 14)
    private val backgroundSecondary = Color.rgb(15, 18, 27)
    private val surfaceColor = Color.rgb(18, 22, 32)
    private val surfaceHover = Color.rgb(38, 44, 59)
    private val textColor = Color.WHITE
    private val secondaryText = Color.rgb(165, 172, 188)
    private val mutedText = Color.rgb(105, 113, 130)
    private val accentColor = Color.rgb(225, 35, 72)
    private val accentDark = Color.rgb(155, 20, 48)
    private val successColor = Color.rgb(70, 205, 130)
    private val selectedBorder = Color.WHITE

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var session: XtreamSession? = null
    private val prefs = context.getSharedPreferences("tvbox_premium_session", Context.MODE_PRIVATE)

    private var screen = Screen.LOGIN
    private var selectedSection = 0

    // TV State
    private var selectedCategoryIndex = 0
    private var selectedChannelIndex = 0
    private var selectedCategoryId = ""
    private var selectedCategoryName = ""
    private var categoryChannels: List<LiveChannel> = emptyList()
    private var liveCategories: List<LiveCategory> = emptyList()
    private var liveChannels: List<LiveChannel> = emptyList()

    // VOD State
    private var vodCategories: List<VodCategory> = emptyList()
    private var categoryVodMovies: List<VodMovie> = emptyList()
    private var selectedVodCategoryIndex = 0
    private var selectedVodMovieIndex = 0
    private var currentMovieDetail: VodMovieDetail? = null
    private var detailFocusedElement = 0 // 0 = Reproducir, 1 = Fila Sugerencias
    private var selectedRelatedMovieIndex = 0
    private var isVodLoading = false

    // Scroll
    private var categoryScroll = 0f
    private var channelScroll = 0f
    private var vodCategoryScroll = 0f
    private var vodMovieScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // Login
    private var loginServer = ""
    private var loginUser = ""
    private var loginPassword = ""
    private var loginField = 0
    private var loginLoading = false
    private var loginError = ""

    // Cache
    private val bitmapCache = Collections.synchronizedMap(mutableMapOf<String, Bitmap>())
    private val loadingImages = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        restoreSavedSession()
        paint.isAntiAlias = true
        textPaint.isAntiAlias = true
    }

    fun hasActiveSession(): Boolean = session != null

    private fun restoreSavedSession() {
        val savedServer = prefs.getString("server", "") ?: ""
        val savedUser = prefs.getString("user", "") ?: ""
        val savedPassword = prefs.getString("password", "") ?: ""
        if (savedServer.isNotBlank() && savedUser.isNotBlank()) {
            loginServer = savedServer
            loginUser = savedUser
            loginPassword = savedPassword
            session = XtreamSession(savedServer, savedUser, savedPassword)
            screen = Screen.HOME
            loadTvData()
            loadVodCategories()
        }
    }

    private fun saveSession() {
        prefs.edit()
            .putString("server", loginServer.trim().removeSuffix("/"))
            .putString("user", loginUser.trim())
            .putString("password", loginPassword)
            .apply()
    }

    private fun playLiveChannel(channel: LiveChannel) {
        val currentSession = session ?: return
        val streamUrl = "${currentSession.serverUrl}/live/${currentSession.username}/${currentSession.password}/${channel.streamId}.${channel.extension}"

        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("STREAM_TITLE", channel.name)
        }
        context.startActivity(intent)
    }

    private fun playVodMovie(detail: VodMovieDetail) {
        val currentSession = session ?: return
        val streamUrl = "${currentSession.serverUrl}/movie/${currentSession.username}/${currentSession.password}/${detail.streamId}.${detail.containerExtension}"

        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("STREAM_TITLE", detail.title)
        }
        context.startActivity(intent)
    }

    private fun showAccountMenu() {
        val current = session ?: return
        AlertDialog.Builder(context)
            .setTitle("Mi cuenta")
            .setMessage("Usuario: ${current.username}\nServidor: ${current.serverUrl}\n\nPuedes cerrar sesión o cambiar de usuario.")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                prefs.edit().clear().apply()
                session = null
                loginServer = ""
                loginUser = ""
                loginPassword = ""
                loginError = ""
                screen = Screen.LOGIN
                invalidate()
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Cambiar usuario") { _, _ ->
                prefs.edit().clear().apply()
                session = null
                loginServer = ""
                loginUser = ""
                loginPassword = ""
                loginField = 0
                loginError = ""
                screen = Screen.LOGIN
                activity.hideKeyboard()
                invalidate()
            }
            .show()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColor)

        when (screen) {
            Screen.LOGIN -> drawLogin(canvas)
            Screen.HOME -> drawHome(canvas)
            Screen.TV_CATEGORIES -> drawTvCategories(canvas)
            Screen.TV_CHANNELS -> drawTvChannels(canvas)
            Screen.VOD_CATEGORIES -> drawVodCategories(canvas)
            Screen.VOD_MOVIES -> drawVodMovies(canvas)
            Screen.VOD_MOVIE_DETAIL -> drawVodMovieDetail(canvas)
        }
    }

    // ============================================================
    // LOGIN
    // ============================================================

    private fun drawLogin(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawLoginBackground(canvas)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = responsiveText(42f)
        textPaint.color = Color.WHITE
        canvas.drawText("TVBOX", w / 2f, dp(88f), textPaint)

        textPaint.textSize = responsiveText(13f)
        textPaint.color = accentColor
        textPaint.letterSpacing = 0.18f
        canvas.drawText("PREMIUM", w / 2f, dp(112f), textPaint)
        textPaint.letterSpacing = 0f

        val mobile = width < dp(700f)
        val boxWidth = if (mobile) min(dp(500f), w - dp(32f)) else min(dp(520f), w - dp(60f))
        val boxHeight = if (mobile) dp(380f) else dp(390f)
        val boxLeft = (w - boxWidth) / 2f
        val boxTop = if (mobile) dp(135f) else dp(145f)

        paint.color = Color.argb(80, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(boxLeft + dp(5f), boxTop + dp(8f), boxLeft + boxWidth + dp(5f), boxTop + boxHeight + dp(8f)),
            dp(24f), dp(24f), paint
        )

        drawRoundedRect(canvas, boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, dp(24f), surfaceColor)

        paint.shader = LinearGradient(boxLeft, 0f, boxLeft + boxWidth, 0f, accentDark, accentColor, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + dp(4f)), dp(2f), dp(2f), paint)
        paint.shader = null

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(23f)
        textPaint.color = textColor
        canvas.drawText("Bienvenido", boxLeft + dp(28f), boxTop + dp(48f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(12f)
        textPaint.color = secondaryText
        canvas.drawText("Inicia sesión para acceder a tu contenido", boxLeft + dp(28f), boxTop + dp(70f), textPaint)

        val fieldLeft = boxLeft + dp(28f)
        val fieldWidth = boxWidth - dp(56f)
        val fieldHeight = dp(54f)

        drawLoginField(canvas, fieldLeft, boxTop + dp(88f), fieldWidth, fieldHeight, "SERVIDOR", loginServer, loginField == 0)
        drawLoginField(canvas, fieldLeft, boxTop + dp(153f), fieldWidth, fieldHeight, "USUARIO", loginUser, loginField == 1)
        drawLoginField(canvas, fieldLeft, boxTop + dp(218f), fieldWidth, fieldHeight, "CONTRASEÑA", if (loginPassword.isEmpty()) "" else "••••••••••", loginField == 2)

        val buttonTop = boxTop + dp(290f)
        val buttonBottom = buttonTop + dp(52f)
        val buttonColor = if (loginLoading) accentDark else accentColor

        drawRoundedRect(canvas, fieldLeft, buttonTop, fieldLeft + fieldWidth, buttonBottom, dp(14f), buttonColor)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(15f)
        textPaint.color = Color.WHITE
        canvas.drawText(if (loginLoading) "CONECTANDO..." else "ENTRAR", w / 2f, buttonTop + dp(33f), textPaint)

        if (loginError.isNotEmpty()) {
            textPaint.textSize = responsiveText(12f)
            textPaint.color = Color.rgb(255, 105, 115)
            canvas.drawText(loginError, w / 2f, buttonBottom + dp(28f), textPaint)
        }

        textPaint.textSize = responsiveText(10f)
        textPaint.color = mutedText
        canvas.drawText(
            if (isMobile()) "Toca un campo para escribir" else "▲ ▼ seleccionar   •   OK confirmar",
            w / 2f, h - dp(22f), textPaint
        )
    }

    private fun drawLoginBackground(canvas: Canvas) {
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), backgroundColor, backgroundSecondary, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        paint.color = Color.argb(28, 225, 35, 72)
        canvas.drawCircle(width * 0.15f, height * 0.20f, dp(130f), paint)
        canvas.drawCircle(width * 0.90f, height * 0.75f, dp(180f), paint)
    }

    private fun drawLoginField(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, label: String, value: String, selected: Boolean) {
        val border = if (selected) accentColor else Color.rgb(52, 58, 72)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(14, 17, 25)
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), dp(12f), dp(12f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) dp(2f) else dp(1f)
        paint.color = border
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), dp(12f), dp(12f), paint)

        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(9f)
        textPaint.color = if (selected) accentColor else mutedText
        canvas.drawText(label, left + dp(15f), top + dp(18f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(13f)
        textPaint.color = if (value.isEmpty()) mutedText else Color.WHITE
        canvas.drawText(
            value.ifEmpty {
                when (label) {
                    "SERVIDOR" -> "http://servidor:puerto"
                    "USUARIO" -> "Ingresa tu usuario"
                    else -> "Ingresa tu contraseña"
                }
            },
            left + dp(15f), top + dp(40f), textPaint
        )
    }

    // ============================================================
    // HOME
    // ============================================================

    private fun drawHome(canvas: Canvas) {
        drawTopBar(canvas, "INICIO")

        paint.shader = LinearGradient(0f, dp(55f), 0f, height.toFloat(), backgroundSecondary, backgroundColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, dp(55f), width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(if (isMobile()) 29f else 38f)
        textPaint.color = Color.WHITE
        canvas.drawText("Todo tu entretenimiento", dp(35f), dp(112f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(14f)
        textPaint.color = secondaryText
        canvas.drawText("Televisión en directo y contenido bajo demanda", dp(35f), dp(140f), textPaint)

        paint.color = accentColor
        canvas.drawRoundRect(RectF(dp(35f), dp(158f), dp(95f), dp(162f)), dp(2f), dp(2f), paint)

        val mobile = isMobile()
        if (mobile) {
            drawHomeCard(canvas, 0, dp(24f), dp(190f), width - dp(24f), dp(315f), "TV EN VIVO", "Canales y categorías", "TV")
            drawHomeCard(canvas, 1, dp(24f), dp(335f), width - dp(24f), dp(460f), "PELÍCULAS", "Catálogo VOD", "▶")
        } else {
            val gap = dp(22f)
            val totalWidth = min(width - dp(70f), dp(900f))
            val left = (width - totalWidth) / 2f
            val cardWidth = (totalWidth - gap) / 2f

            drawHomeCard(canvas, 0, left, dp(190f), left + cardWidth, dp(365f), "TV EN VIVO", "Canales y categorías", "TV")
            drawHomeCard(canvas, 1, left + cardWidth + gap, dp(190f), left + totalWidth, dp(365f), "PELÍCULAS", "Catálogo VOD", "▶")
        }

        val count = liveChannels.size
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(11f)
        textPaint.color = mutedText
        canvas.drawText(
            if (count > 0) "$count canales disponibles" else "Preparando catálogo...",
            dp(35f), height - dp(28f), textPaint
        )
    }

    private fun drawHomeCard(canvas: Canvas, index: Int, left: Float, top: Float, right: Float, bottom: Float, title: String, subtitle: String, icon: String) {
        val selected = selectedSection == index
        val fill = if (selected) surfaceHover else surfaceColor

        drawRoundedRect(canvas, left, top, right, bottom, dp(20f), fill)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) dp(3f) else dp(1f)
        paint.color = if (selected) Color.WHITE else Color.rgb(42, 48, 61)
        canvas.drawRoundRect(RectF(left, top, right, bottom), dp(20f), dp(20f), paint)
        paint.style = Paint.Style.FILL

        if (selected) {
            paint.color = accentColor
            canvas.drawRoundRect(RectF(left, top + dp(25f), left + dp(5f), bottom - dp(25f)), dp(2f), dp(2f), paint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(26f)
        textPaint.color = if (selected) accentColor else secondaryText
        canvas.drawText(icon, (left + right) / 2f, top + dp(60f), textPaint)

        textPaint.textSize = responsiveText(19f)
        textPaint.color = Color.WHITE
        canvas.drawText(title, (left + right) / 2f, top + dp(100f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(12f)
        textPaint.color = secondaryText
        canvas.drawText(subtitle, (left + right) / 2f, top + dp(124f), textPaint)

        textPaint.textSize = responsiveText(10f)
        textPaint.color = if (selected) Color.WHITE else mutedText
        canvas.drawText(if (selected) "ABRIR" else "SELECCIONAR", (left + right) / 2f, bottom - dp(22f), textPaint)
    }

    // ============================================================
    // TOP BAR
    // ============================================================

    private fun drawTopBar(canvas: Canvas, current: String) {
        paint.color = Color.rgb(9, 11, 17)
        canvas.drawRect(0f, 0f, width.toFloat(), dp(58f), paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(18f)
        textPaint.color = Color.WHITE
        canvas.drawText("TVBOX", dp(25f), dp(35f), textPaint)

        textPaint.textSize = responsiveText(9f)
        textPaint.color = accentColor
        textPaint.letterSpacing = 0.12f
        canvas.drawText("PREMIUM", dp(86f), dp(35f), textPaint)
        textPaint.letterSpacing = 0f

        if (session != null) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = responsiveText(11f)
            textPaint.color = secondaryText
            canvas.drawText("CUENTA  ⋮", width - dp(22f), dp(35f), textPaint)
        }

        if (current != "INICIO") {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = responsiveText(11f)
            textPaint.color = secondaryText
            canvas.drawText(" / $current", dp(155f), dp(35f), textPaint)
        }
    }

    // ============================================================
    // TV CATEGORIAS
    // ============================================================

    private fun drawTvCategories(canvas: Canvas) {
        drawTopBar(canvas, "TV")

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(if (isMobile()) 28f else 32f)
        textPaint.color = Color.WHITE
        canvas.drawText("Televisión", dp(30f), dp(95f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(13f)
        textPaint.color = secondaryText
        canvas.drawText(
            if (liveCategories.isEmpty()) "Cargando categorías..." else "${liveCategories.size} categorías",
            dp(30f), dp(120f), textPaint
        )

        if (liveCategories.isEmpty()) {
            drawLoading(canvas, width / 2f, height / 2f)
            return
        }

        if (categoryScroll > 0f) {
            drawScrollHint(canvas, true)
        }

        val columns = categoryColumns()
        val cardWidth = categoryCardWidth(columns)
        val cardHeight = if (isMobile()) dp(138f) else dp(145f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val startX = if (isMobile()) dp(16f) else dp(30f)
        val startY = dp(150f) - categoryScroll

        liveCategories.forEachIndexed { index, category ->
            val row = index / columns
            val col = index % columns
            val left = startX + col * (cardWidth + gap)
            val top = startY + row * (cardHeight + gap)

            if (top + cardHeight >= dp(140f) && top <= height) {
                drawCategoryCard(canvas, category, left, top, cardWidth, cardHeight, index == selectedCategoryIndex)
            }
        }
    }

    private fun drawCategoryCard(canvas: Canvas, category: LiveCategory, left: Float, top: Float, cardWidth: Float, cardHeight: Float, selected: Boolean) {
        drawRoundedRect(canvas, left, top, left + cardWidth, top + cardHeight, dp(15f), if (selected) surfaceHover else surfaceColor)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) dp(3f) else dp(1f)
        paint.color = if (selected) selectedBorder else Color.rgb(42, 48, 61)
        canvas.drawRoundRect(RectF(left, top, left + cardWidth, top + cardHeight), dp(15f), dp(15f), paint)
        paint.style = Paint.Style.FILL

        val channels = channelsForCategory(category.categoryId)
        val logos = channels.map { it.icon }.filter { it.isNotBlank() }.distinct().take(4)

        drawCategoryMosaic(canvas, logos, left + dp(8f), top + dp(8f), cardWidth - dp(16f), dp(90f), category.categoryName)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(13f)
        textPaint.color = Color.WHITE
        canvas.drawText(
            truncateText(category.categoryName, if (isMobile()) 21 else 26),
            left + dp(12f), top + cardHeight - dp(17f), textPaint
        )
    }

    private fun drawCategoryMosaic(canvas: Canvas, urls: List<String>, left: Float, top: Float, width: Float, height: Float, categoryName: String) {
        if (urls.isEmpty()) {
            drawCategoryPlaceholder(canvas, left, top, width, height, categoryName)
            return
        }

        val halfW = width / 2f
        val halfH = height / 2f

        val rects = listOf(
            RectF(left, top, left + halfW - dp(2f), top + halfH - dp(2f)),
            RectF(left + halfW + dp(2f), top, left + width, top + halfH - dp(2f)),
            RectF(left, top + halfH + dp(2f), left + halfW - dp(2f), top + height),
            RectF(left + halfW + dp(2f), top + halfH + dp(2f), left + width, top + height)
        )

        urls.take(4).forEachIndexed { index, url ->
            requestImage(url)
            drawImageOrPlaceholder(canvas, bitmapCache[url], rects[index], categoryName)
        }

        if (urls.size < 4) {
            for (index in urls.size until 4) {
                drawCategoryPlaceholder(canvas, rects[index].left, rects[index].top, rects[index].width(), rects[index].height(), categoryName)
            }
        }
    }

    private fun drawCategoryPlaceholder(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, title: String) {
        paint.shader = LinearGradient(left, top, left + width, top + height, Color.rgb(40, 44, 59), Color.rgb(17, 20, 29), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), dp(8f), dp(8f), paint)
        paint.shader = null

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(18f)
        textPaint.color = Color.WHITE
        canvas.drawText(initials(title), left + width / 2f, top + height / 2f + dp(6f), textPaint)
    }

    // ============================================================
    // TV CHANNELS
    // ============================================================

    private fun drawTvChannels(canvas: Canvas) {
        drawTopBar(canvas, "TV")

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(if (isMobile()) 24f else 30f)
        textPaint.color = Color.WHITE
        canvas.drawText(selectedCategoryName, dp(30f), dp(92f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(12f)
        textPaint.color = secondaryText
        canvas.drawText("${categoryChannels.size} canales", dp(30f), dp(117f), textPaint)

        if (categoryChannels.isEmpty()) {
            drawEmptyState(canvas, "No hay canales en esta categoría")
            return
        }

        val columns = channelColumns()
        val cardWidth = channelCardWidth(columns)
        val cardHeight = if (isMobile()) dp(125f) else dp(135f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val startX = if (isMobile()) dp(16f) else dp(30f)
        val startY = dp(145f) - channelScroll

        categoryChannels.forEachIndexed { index, channel ->
            val row = index / columns
            val col = index % columns
            val left = startX + col * (cardWidth + gap)
            val top = startY + row * (cardHeight + gap)

            if (top + cardHeight >= dp(135f) && top <= height) {
                drawChannelCard(canvas, channel, left, top, cardWidth, cardHeight, index == selectedChannelIndex)
            }
        }
    }

    private fun drawChannelCard(canvas: Canvas, channel: LiveChannel, left: Float, top: Float, width: Float, height: Float, selected: Boolean) {
        drawRoundedRect(canvas, left, top, left + width, top + height, dp(14f), if (selected) surfaceHover else surfaceColor)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) dp(3f) else dp(1f)
        paint.color = if (selected) Color.WHITE else Color.rgb(42, 48, 61)
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), dp(14f), dp(14f), paint)
        paint.style = Paint.Style.FILL

        val logoSize = if (isMobile()) dp(66f) else dp(70f)
        val logoRect = RectF(left + dp(12f), top + dp(12f), left + dp(12f) + logoSize, top + dp(12f) + logoSize)

        if (channel.icon.isNotBlank()) {
            requestImage(channel.icon)
            drawImageOrPlaceholder(canvas, bitmapCache[channel.icon], logoRect, channel.name)
        } else {
            drawChannelPlaceholder(canvas, logoRect, channel.name)
        }

        val textLeft = left + logoSize + dp(25f)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(13f)
        textPaint.color = Color.WHITE
        canvas.drawText(truncateText(channel.name, if (isMobile()) 18 else 25), textLeft, top + dp(40f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(10f)
        textPaint.color = secondaryText
        canvas.drawText(if (channel.streamType.isNotBlank()) channel.streamType.uppercase() else "TV", textLeft, top + dp(60f), textPaint)

        paint.color = successColor
        canvas.drawCircle(textLeft, top + dp(84f), dp(4f), paint)

        textPaint.textSize = responsiveText(9f)
        textPaint.color = secondaryText
        canvas.drawText("EN VIVO", textLeft + dp(10f), top + dp(87f), textPaint)
    }

    private fun drawChannelPlaceholder(canvas: Canvas, rect: RectF, name: String) {
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, accentDark, Color.rgb(31, 35, 48), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paint)
        paint.shader = null

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(17f)
        textPaint.color = Color.WHITE
        canvas.drawText(initials(name), rect.centerX(), rect.centerY() + dp(6f), textPaint)
    }

    // ============================================================
    // VOD CATEGORIAS, PELICULAS Y DETALLE
    // ============================================================

    private fun openVod() {
        screen = Screen.VOD_CATEGORIES
        selectedVodCategoryIndex = 0
        vodCategoryScroll = 0f
        if (vodCategories.isEmpty()) {
            loadVodCategories()
        }
        activity.hideKeyboard()
        invalidate()
    }

    private fun openSelectedVodCategory() {
        if (vodCategories.isEmpty()) return
        val cat = vodCategories[selectedVodCategoryIndex]
        selectedCategoryId = cat.categoryId
        selectedCategoryName = cat.categoryName
        screen = Screen.VOD_MOVIES
        selectedVodMovieIndex = 0
        vodMovieScroll = 0f
        loadVodMovies(cat.categoryId)
        invalidate()
    }

    private fun openVodMovieDetail(movie: VodMovie) {
        screen = Screen.VOD_MOVIE_DETAIL
        isVodLoading = true
        currentMovieDetail = null
        detailFocusedElement = 0
        selectedRelatedMovieIndex = 0
        invalidate()

        val sess = session ?: return
        Thread {
            try {
                val url = buildApiUrl(sess, "get_vod_info") + "&vod_id=${movie.streamId}"
                val resp = httpGet(url)
                val json = JSONObject(resp)
                val info = json.optJSONObject("info")
                val movieData = json.optJSONObject("movie_data")

                val detail = VodMovieDetail(
                    streamId = movie.streamId,
                    title = info?.optString("name", movie.name) ?: movie.name,
                    posterUrl = info?.optString("movie_image", movie.icon) ?: movie.icon,
                    year = info?.optString("releasedate", "")?.take(4) ?: "",
                    duration = info?.optString("duration", "") ?: "",
                    rating = info?.optString("rating", movie.rating) ?: movie.rating,
                    plot = info?.optString("plot", "Sin descripción disponible.") ?: "Sin descripción.",
                    cast = info?.optString("cast", "N/A") ?: "N/A",
                    director = info?.optString("director", "N/A") ?: "N/A",
                    containerExtension = movieData?.optString("container_extension", "mp4") ?: "mp4"
                )

                post {
                    currentMovieDetail = detail
                    isVodLoading = false
                    invalidate()
                }
            } catch (e: Exception) {
                post {
                    isVodLoading = false
                    Toast.makeText(context, "Error al cargar los detalles", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun drawVodCategories(canvas: Canvas) {
        drawTopBar(canvas, "PELÍCULAS")

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(if (isMobile()) 28f else 32f)
        textPaint.color = Color.WHITE
        canvas.drawText("Categorías VOD", dp(30f), dp(95f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(13f)
        textPaint.color = secondaryText
        canvas.drawText(
            if (vodCategories.isEmpty()) "Cargando catálogo..." else "${vodCategories.size} categorías",
            dp(30f), dp(120f), textPaint
        )

        if (vodCategories.isEmpty()) {
            drawLoading(canvas, width / 2f, height / 2f)
            return
        }

        val columns = categoryColumns()
        val cardWidth = categoryCardWidth(columns)
        val cardHeight = if (isMobile()) dp(138f) else dp(145f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val startX = if (isMobile()) dp(16f) else dp(30f)
        val startY = dp(150f) - vodCategoryScroll

        vodCategories.forEachIndexed { index, cat ->
            val row = index / columns
            val col = index % columns
            val left = startX + col * (cardWidth + gap)
            val top = startY + row * (cardHeight + gap)

            if (top + cardHeight >= dp(140f) && top <= height) {
                val selected = index == selectedVodCategoryIndex
                drawRoundedRect(canvas, left, top, left + cardWidth, top + cardHeight, dp(15f), if (selected) surfaceHover else surfaceColor)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (selected) dp(3f) else dp(1f)
                paint.color = if (selected) selectedBorder else Color.rgb(42, 48, 61)
                canvas.drawRoundRect(RectF(left, top, left + cardWidth, top + cardHeight), dp(15f), dp(15f), paint)
                paint.style = Paint.Style.FILL

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = responsiveText(15f)
                textPaint.color = Color.WHITE
                canvas.drawText(truncateText(cat.categoryName, 22), left + cardWidth / 2f, top + cardHeight / 2f + dp(5f), textPaint)
            }
        }
    }

    private fun drawVodMovies(canvas: Canvas) {
        drawTopBar(canvas, "PELÍCULAS / $selectedCategoryName")

        if (isVodLoading) {
            drawLoading(canvas, width / 2f, height / 2f)
            return
        }

        val columns = categoryColumns() + 1
        val cardWidth = categoryCardWidth(columns)
        val cardHeight = cardWidth * 1.45f
        val gap = dp(14f)
        val startX = if (isMobile()) dp(16f) else dp(30f)
        val startY = dp(90f) - vodMovieScroll

        categoryVodMovies.forEachIndexed { index, movie ->
            val row = index / columns
            val col = index % columns
            val left = startX + col * (cardWidth + gap)
            val top = startY + row * (cardHeight + gap)

            if (top + cardHeight >= dp(80f) && top <= height) {
                val selected = index == selectedVodMovieIndex
                val rect = RectF(left, top, left + cardWidth, top + cardHeight)

                if (movie.icon.isNotBlank()) {
                    requestImage(movie.icon)
                    drawImageOrPlaceholder(canvas, bitmapCache[movie.icon], rect, movie.name)
                } else {
                    drawCategoryPlaceholder(canvas, left, top, cardWidth, cardHeight, movie.name)
                }

                if (selected) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(4f)
                    paint.color = Color.WHITE
                    canvas.drawRoundRect(rect, dp(8f), dp(8f), paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
    }

    private fun drawVodMovieDetail(canvas: Canvas) {
        drawTopBar(canvas, "DETALLE DE PELÍCULA")

        val detail = currentMovieDetail
        if (isVodLoading || detail == null) {
            drawLoading(canvas, width / 2f, height / 2f)
            return
        }

        val posterWidth = dp(180f)
        val posterHeight = dp(260f)
        val posterRect = RectF(dp(35f), dp(85f), dp(35f) + posterWidth, dp(85f) + posterHeight)

        if (detail.posterUrl.isNotBlank()) {
            requestImage(detail.posterUrl)
            drawImageOrPlaceholder(canvas, bitmapCache[detail.posterUrl], posterRect, detail.title)
        } else {
            drawCategoryPlaceholder(canvas, posterRect.left, posterRect.top, posterWidth, posterHeight, detail.title)
        }

        val infoLeft = dp(235f)
        textPaint.textAlign = Paint.Align.LEFT

        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(24f)
        textPaint.color = Color.WHITE
        canvas.drawText(truncateText(detail.title, 32), infoLeft, dp(110f), textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(13f)
        textPaint.color = secondaryText
        val infoSub = "${detail.year}  •  ${detail.duration}  •  ⭐ ${detail.rating}"
        canvas.drawText(infoSub, infoLeft, dp(135f), textPaint)

        textPaint.textSize = responsiveText(12f)
        textPaint.color = Color.LTGRAY
        canvas.drawText("Sinopsis:", infoLeft, dp(165f), textPaint)
        canvas.drawText(truncateText(detail.plot, 120), infoLeft, dp(185f), textPaint)

        textPaint.color = mutedText
        canvas.drawText("Actores: ${truncateText(detail.cast, 50)}", infoLeft, dp(220f), textPaint)
        canvas.drawText("Director: ${truncateText(detail.director, 40)}", infoLeft, dp(240f), textPaint)

        val btnPlayLeft = infoLeft
        val btnPlayTop = dp(270f)
        val btnPlayRight = btnPlayLeft + dp(180f)
        val btnPlayBottom = btnPlayTop + dp(48f)
        val isBtnFocused = detailFocusedElement == 0

        drawRoundedRect(canvas, btnPlayLeft, btnPlayTop, btnPlayRight, btnPlayBottom, dp(10f), if (isBtnFocused) accentColor else surfaceHover)

        if (isBtnFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3f)
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(btnPlayLeft, btnPlayTop, btnPlayRight, btnPlayBottom), dp(10f), dp(10f), paint)
            paint.style = Paint.Style.FILL
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = responsiveText(14f)
        textPaint.color = Color.WHITE
        canvas.drawText("▶ REPRODUCIR", (btnPlayLeft + btnPlayRight) / 2f, btnPlayTop + dp(30f), textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = responsiveText(14f)
        textPaint.color = Color.WHITE
        canvas.drawText("También te puede interesar", dp(35f), dp(370f), textPaint)

        val relCardW = dp(90f)
        val relCardH = dp(125f)
        val relGap = dp(15f)

        categoryVodMovies.take(8).forEachIndexed { idx, movie ->
            val rLeft = dp(35f) + idx * (relCardW + relGap)
            val rTop = dp(385f)
            val rect = RectF(rLeft, rTop, rLeft + relCardW, rTop + relCardH)

            if (movie.icon.isNotBlank()) {
                requestImage(movie.icon)
                drawImageOrPlaceholder(canvas, bitmapCache[movie.icon], rect, movie.name)
            } else {
                drawCategoryPlaceholder(canvas, rLeft, rTop, relCardW, relCardH, movie.name)
            }

            if (detailFocusedElement == 1 && idx == selectedRelatedMovieIndex) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(3f)
                paint.color = Color.WHITE
                canvas.drawRoundRect(rect, dp(6f), dp(6f), paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    // ============================================================
    // GESTOS TOUCH (SOPORTE COMPLETO TV Y CELULAR)
    // ============================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                if (abs(event.x - downX) > dp(8f) || abs(event.y - downY) > dp(8f)) {
                    isDragging = true
                }

                when (screen) {
                    Screen.TV_CATEGORIES -> {
                        if (abs(dy) > abs(dx)) {
                            categoryScroll = clampCategoryScroll(categoryScroll - dy)
                            invalidate()
                        }
                    }
                    Screen.TV_CHANNELS -> {
                        if (abs(dy) > abs(dx)) {
                            channelScroll = clampChannelScroll(channelScroll - dy)
                            invalidate()
                        }
                    }
                    Screen.VOD_CATEGORIES -> {
                        if (abs(dy) > abs(dx)) {
                            vodCategoryScroll = clampVodCategoryScroll(vodCategoryScroll - dy)
                            invalidate()
                        }
                    }
                    Screen.VOD_MOVIES -> {
                        if (abs(dy) > abs(dx)) {
                            vodMovieScroll = clampVodMovieScroll(vodMovieScroll - dy)
                            invalidate()
                        }
                    }
                    else -> {}
                }

                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    handleTap(event.x, event.y)
                } else {
                    val dx = event.x - downX
                    if (abs(dx) > dp(80f) && screen == Screen.HOME) {
                        selectedSection = if (dx < 0) min(1, selectedSection + 1) else max(0, selectedSection - 1)
                        invalidate()
                    }
                }
                return true
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        when (screen) {
            Screen.LOGIN -> handleLoginTap(x, y)
            Screen.HOME -> {
                if (session != null && x > width - dp(145f) && y < dp(65f)) {
                    showAccountMenu()
                    return
                }

                val mobile = isMobile()
                val tvRect = if (mobile) RectF(dp(24f), dp(190f), width - dp(24f), dp(315f)) else RectF((width - min(width - dp(70f), dp(900f))) / 2f, dp(190f), (width - min(width - dp(70f), dp(900f))) / 2f + (min(width - dp(70f), dp(900f)) - dp(22f)) / 2f, dp(365f))
                val vodRect = if (mobile) RectF(dp(24f), dp(335f), width - dp(24f), dp(460f)) else RectF((width - min(width - dp(70f), dp(900f))) / 2f + (min(width - dp(70f), dp(900f)) - dp(22f)) / 2f + dp(22f), dp(190f), (width - min(width - dp(70f), dp(900f))) / 2f + min(width - dp(70f), dp(900f)), dp(365f))

                if (tvRect.contains(x, y)) {
                    selectedSection = 0
                    openTv()
                } else if (vodRect.contains(x, y)) {
                    selectedSection = 1
                    openVod()
                }
            }
            Screen.TV_CATEGORIES -> {
                val columns = categoryColumns()
                val cardWidth = categoryCardWidth(columns)
                val cardHeight = if (isMobile()) dp(138f) else dp(145f)
                val gap = if (isMobile()) dp(12f) else dp(18f)
                val startX = if (isMobile()) dp(16f) else dp(30f)
                val startY = dp(150f) - categoryScroll

                liveCategories.forEachIndexed { index, _ ->
                    val row = index / columns
                    val col = index % columns
                    val left = startX + col * (cardWidth + gap)
                    val top = startY + row * (cardHeight + gap)

                    if (RectF(left, top, left + cardWidth, top + cardHeight).contains(x, y)) {
                        selectedCategoryIndex = index
                        openSelectedCategory()
                        return
                    }
                }
            }
            Screen.TV_CHANNELS -> {
                val columns = channelColumns()
                val cardWidth = channelCardWidth(columns)
                val cardHeight = if (isMobile()) dp(125f) else dp(135f)
                val gap = if (isMobile()) dp(12f) else dp(18f)
                val startX = if (isMobile()) dp(16f) else dp(30f)
                val startY = dp(145f) - channelScroll

                categoryChannels.forEachIndexed { index, channel ->
                    val row = index / columns
                    val col = index % columns
                    val left = startX + col * (cardWidth + gap)
                    val top = startY + row * (cardHeight + gap)

                    if (RectF(left, top, left + cardWidth, top + cardHeight).contains(x, y)) {
                        selectedChannelIndex = index
                        playLiveChannel(channel)
                        return
                    }
                }
            }
            Screen.VOD_CATEGORIES -> {
                val columns = categoryColumns()
                val cardWidth = categoryCardWidth(columns)
                val cardHeight = if (isMobile()) dp(138f) else dp(145f)
                val gap = if (isMobile()) dp(12f) else dp(18f)
                val startX = if (isMobile()) dp(16f) else dp(30f)
                val startY = dp(150f) - vodCategoryScroll

                vodCategories.forEachIndexed { index, _ ->
                    val row = index / columns
                    val col = index % columns
                    val left = startX + col * (cardWidth + gap)
                    val top = startY + row * (cardHeight + gap)

                    if (RectF(left, top, left + cardWidth, top + cardHeight).contains(x, y)) {
                        selectedVodCategoryIndex = index
                        openSelectedVodCategory()
                        return
                    }
                }
            }
            Screen.VOD_MOVIES -> {
                val columns = categoryColumns() + 1
                val cardWidth = categoryCardWidth(columns)
                val cardHeight = cardWidth * 1.45f
                val gap = dp(14f)
                val startX = if (isMobile()) dp(16f) else dp(30f)
                val startY = dp(90f) - vodMovieScroll

                categoryVodMovies.forEachIndexed { index, movie ->
                    val row = index / columns
                    val col = index % columns
                    val left = startX + col * (cardWidth + gap)
                    val top = startY + row * (cardHeight + gap)

                    if (RectF(left, top, left + cardWidth, top + cardHeight).contains(x, y)) {
                        selectedVodMovieIndex = index
                        openVodMovieDetail(movie)
                        return
                    }
                }
            }
            Screen.VOD_MOVIE_DETAIL -> {
                val btnPlayLeft = dp(235f)
                val btnPlayTop = dp(270f)
                val btnPlayRight = btnPlayLeft + dp(180f)
                val btnPlayBottom = btnPlayTop + dp(48f)

                if (RectF(btnPlayLeft, btnPlayTop, btnPlayRight, btnPlayBottom).contains(x, y)) {
                    detailFocusedElement = 0
                    currentMovieDetail?.let { playVodMovie(it) }
                    return
                }

                val relCardW = dp(90f)
                val relCardH = dp(125f)
                val relGap = dp(15f)

                categoryVodMovies.take(8).forEachIndexed { idx, movie ->
                    val rLeft = dp(35f) + idx * (relCardW + relGap)
                    val rTop = dp(385f)
                    val rect = RectF(rLeft, rTop, rLeft + relCardW, rTop + relCardH)

                    if (rect.contains(x, y)) {
                        detailFocusedElement = 1
                        selectedRelatedMovieIndex = idx
                        openVodMovieDetail(movie)
                        return
                    }
                }
            }
        }
    }

    private fun handleLoginTap(x: Float, y: Float) {
        val w = width.toFloat()
        val mobile = isMobile()
        val boxWidth = if (mobile) min(dp(500f), w - dp(32f)) else min(dp(520f), w - dp(60f))
        val boxLeft = (w - boxWidth) / 2f
        val boxTop = if (mobile) dp(135f) else dp(145f)
        val fieldLeft = boxLeft + dp(28f)
        val fieldWidth = boxWidth - dp(56f)

        val fields = listOf(
            RectF(fieldLeft, boxTop + dp(88f), fieldLeft + fieldWidth, boxTop + dp(142f)),
            RectF(fieldLeft, boxTop + dp(153f), fieldLeft + fieldWidth, boxTop + dp(207f)),
            RectF(fieldLeft, boxTop + dp(218f), fieldLeft + fieldWidth, boxTop + dp(272f))
        )

        fields.forEachIndexed { index, rect ->
            if (rect.contains(x, y)) {
                selectLoginField(index)
                return
            }
        }

        if (RectF(fieldLeft, boxTop + dp(290f), fieldLeft + fieldWidth, boxTop + dp(342f)).contains(x, y)) {
            performLogin()
        }
    }

    private fun selectLoginField(field: Int) {
        loginField = field
        val value = when (field) {
            0 -> loginServer
            1 -> loginUser
            else -> loginPassword
        }
        activity.openKeyboardForField(field, value)
        invalidate()
    }

    fun updateLoginText(value: String) {
        when (loginField) {
            0 -> loginServer = value
            1 -> loginUser = value
            2 -> loginPassword = value
        }
        invalidate()
    }

    // ============================================================
    // TECLADO & D-PAD METODOS
    // ============================================================

    private fun handleLoginKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { loginField = max(0, loginField - 1); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { loginField = min(2, loginField + 1); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (loginField == 2) performLogin() else selectLoginField(loginField + 1)
                return true
            }
        }
        return false
    }

    private fun openTv() {
        screen = Screen.TV_CATEGORIES
        selectedCategoryIndex = 0
        categoryScroll = 0f
        if (liveCategories.isEmpty()) loadTvData()
        activity.hideKeyboard()
        invalidate()
    }

    private fun openSelectedCategory() {
        if (liveCategories.isEmpty()) return
        selectedCategoryIndex = selectedCategoryIndex.coerceIn(0, liveCategories.lastIndex)
        val category = liveCategories[selectedCategoryIndex]
        selectedCategoryId = category.categoryId
        selectedCategoryName = category.categoryName
        categoryChannels = channelsForCategory(category.categoryId).sortedBy { it.name.lowercase() }
        selectedChannelIndex = 0
        channelScroll = 0f
        screen = Screen.TV_CHANNELS
        invalidate()
    }

    private fun handleHomeKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { selectedSection = max(0, selectedSection - 1); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { selectedSection = min(1, selectedSection + 1); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (selectedSection == 0) openTv() else openVod()
                return true
            }
        }
        return false
    }

    private fun handleCategoryKey(keyCode: Int): Boolean {
        if (liveCategories.isEmpty()) return true
        val columns = categoryColumns()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (selectedCategoryIndex % columns > 0) { selectedCategoryIndex--; ensureCategoryVisible(); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (selectedCategoryIndex < liveCategories.lastIndex) { selectedCategoryIndex++; ensureCategoryVisible(); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { val n = selectedCategoryIndex - columns; if (n >= 0) { selectedCategoryIndex = n; ensureCategoryVisible(); invalidate(); return true } }
            KeyEvent.KEYCODE_DPAD_DOWN -> { val n = selectedCategoryIndex + columns; if (n <= liveCategories.lastIndex) { selectedCategoryIndex = n; ensureCategoryVisible(); invalidate(); return true } }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { openSelectedCategory(); return true }
        }
        return false
    }

    private fun handleChannelKey(keyCode: Int): Boolean {
        if (categoryChannels.isEmpty()) return true
        val columns = channelColumns()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (selectedChannelIndex % columns > 0) { selectedChannelIndex--; ensureChannelVisible(); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (selectedChannelIndex < categoryChannels.lastIndex) { selectedChannelIndex++; ensureChannelVisible(); invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { val n = selectedChannelIndex - columns; if (n >= 0) { selectedChannelIndex = n; ensureChannelVisible(); invalidate(); return true } }
            KeyEvent.KEYCODE_DPAD_DOWN -> { val n = selectedChannelIndex + columns; if (n <= categoryChannels.lastIndex) { selectedChannelIndex = n; ensureChannelVisible(); invalidate(); return true } }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                playLiveChannel(categoryChannels[selectedChannelIndex])
                return true
            }
        }
        return false
    }

    private fun handleVodCategoryKey(keyCode: Int): Boolean {
        val cols = categoryColumns()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (selectedVodCategoryIndex % cols > 0) { selectedVodCategoryIndex--; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (selectedVodCategoryIndex < vodCategories.lastIndex) { selectedVodCategoryIndex++; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (selectedVodCategoryIndex - cols >= 0) { selectedVodCategoryIndex -= cols; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (selectedVodCategoryIndex + cols <= vodCategories.lastIndex) { selectedVodCategoryIndex += cols; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { openSelectedVodCategory(); return true }
        }
        return false
    }

    private fun handleVodMovieKey(keyCode: Int): Boolean {
        val cols = categoryColumns() + 1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (selectedVodMovieIndex % cols > 0) { selectedVodMovieIndex--; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (selectedVodMovieIndex < categoryVodMovies.lastIndex) { selectedVodMovieIndex++; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (selectedVodMovieIndex - cols >= 0) { selectedVodMovieIndex -= cols; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (selectedVodMovieIndex + cols <= categoryVodMovies.lastIndex) { selectedVodMovieIndex += cols; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (categoryVodMovies.isNotEmpty()) openVodMovieDetail(categoryVodMovies[selectedVodMovieIndex])
                return true
            }
        }
        return false
    }

    private fun handleVodMovieDetailKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> if (detailFocusedElement == 0) { detailFocusedElement = 1; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (detailFocusedElement == 1) { detailFocusedElement = 0; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (detailFocusedElement == 1 && selectedRelatedMovieIndex > 0) { selectedRelatedMovieIndex--; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (detailFocusedElement == 1 && selectedRelatedMovieIndex < min(7, categoryVodMovies.lastIndex)) { selectedRelatedMovieIndex++; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (detailFocusedElement == 0) {
                    currentMovieDetail?.let { playVodMovie(it) }
                } else if (detailFocusedElement == 1 && categoryVodMovies.isNotEmpty()) {
                    openVodMovieDetail(categoryVodMovies[selectedRelatedMovieIndex])
                }
                return true
            }
        }
        return false
    }

    fun handleKey(keyCode: Int): Boolean {
        return when (screen) {
            Screen.LOGIN -> handleLoginKey(keyCode)
            Screen.HOME -> handleHomeKey(keyCode)
            Screen.TV_CATEGORIES -> handleCategoryKey(keyCode)
            Screen.TV_CHANNELS -> handleChannelKey(keyCode)
            Screen.VOD_CATEGORIES -> handleVodCategoryKey(keyCode)
            Screen.VOD_MOVIES -> handleVodMovieKey(keyCode)
            Screen.VOD_MOVIE_DETAIL -> handleVodMovieDetailKey(keyCode)
        }
    }

    fun handleBack(): Boolean {
        return when (screen) {
            Screen.VOD_MOVIE_DETAIL -> { screen = Screen.VOD_MOVIES; invalidate(); true }
            Screen.VOD_MOVIES -> { screen = Screen.VOD_CATEGORIES; invalidate(); true }
            Screen.VOD_CATEGORIES -> { screen = Screen.HOME; invalidate(); true }
            Screen.TV_CHANNELS -> { screen = Screen.TV_CATEGORIES; channelScroll = 0f; invalidate(); true }
            Screen.TV_CATEGORIES -> { screen = Screen.HOME; categoryScroll = 0f; invalidate(); true }
            else -> false
        }
    }

    // ============================================================
    // NETWORK LOGIN & LOADERS
    // ============================================================

    private fun performLogin() {
        if (loginLoading) return
        loginError = ""

        val server = loginServer.trim().removeSuffix("/")
        val user = loginUser.trim()
        val password = loginPassword

        if (server.isEmpty()) { loginError = "Ingresa el servidor"; invalidate(); return }
        if (user.isEmpty()) { loginError = "Ingresa el usuario"; invalidate(); return }
        if (password.isEmpty()) { loginError = "Ingresa la contraseña"; invalidate(); return }

        activity.hideKeyboard()
        loginLoading = true
        invalidate()

        Thread {
            try {
                val base = if (server.startsWith("http://") || server.startsWith("https://")) server else "http://$server"
                val encodedUser = URLEncoder.encode(user, "UTF-8")
                val encodedPassword = URLEncoder.encode(password, "UTF-8")

                val apiUrl = "$base/player_api.php?username=$encodedUser&password=$encodedPassword"
                val response = httpGet(apiUrl)
                val json = JSONObject(response)
                val userInfo = json.optJSONObject("user_info")
                val auth = userInfo?.optInt("auth", 0) ?: 0

                if (auth != 1) {
                    post {
                        loginLoading = false
                        loginError = "Usuario o contraseña incorrectos"
                        invalidate()
                    }
                    return@Thread
                }

                session = XtreamSession(serverUrl = base, username = user, password = password)
                saveSession()

                post {
                    loginLoading = false
                    loginError = ""
                    screen = Screen.HOME
                    selectedSection = 0
                    invalidate()
                }

                loadTvData()
                loadVodCategories()

            } catch (e: Exception) {
                post {
                    loginLoading = false
                    loginError = "No se pudo conectar al servidor"
                    invalidate()
                }
            }
        }.start()
    }

    private fun loadTvData() {
        val currentSession = session ?: return
        Thread {
            try {
                val categoriesUrl = buildApiUrl(currentSession, "get_live_categories")
                val streamsUrl = buildApiUrl(currentSession, "get_live_streams")

                val categoriesResponse = httpGet(categoriesUrl)
                val streamsResponse = httpGet(streamsUrl)

                liveCategories = parseLiveCategories(categoriesResponse).distinctBy { it.categoryId }.sortedBy { it.categoryName.lowercase() }
                liveChannels = parseLiveChannels(streamsResponse).distinctBy { it.streamId }.sortedBy { it.name.lowercase() }

                post { invalidate() }

            } catch (e: Exception) {
                post {
                    if (screen == Screen.TV_CATEGORIES) {
                        Toast.makeText(context, "No se pudieron cargar las categorías", Toast.LENGTH_SHORT).show()
                    }
                    invalidate()
                }
            }
        }.start()
    }

    private fun loadVodCategories() {
        val currentSession = session ?: return
        Thread {
            try {
                val url = buildApiUrl(currentSession, "get_vod_categories")
                val resp = httpGet(url)
                val array = JSONArray(resp)
                val list = mutableListOf<VodCategory>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("category_id", "").trim()
                    val name = obj.optString("category_name", "").trim()
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        list.add(VodCategory(id, name))
                    }
                }
                vodCategories = list.sortedBy { it.categoryName.lowercase() }
                post { invalidate() }
            } catch (_: Exception) {}
        }.start()
    }

    private fun loadVodMovies(categoryId: String) {
        val currentSession = session ?: return
        isVodLoading = true
        Thread {
            try {
                val url = buildApiUrl(currentSession, "get_vod_streams") + "&category_id=$categoryId"
                val resp = httpGet(url)
                val array = JSONArray(resp)
                val list = mutableListOf<VodMovie>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    list.add(
                        VodMovie(
                            streamId = obj.optInt("stream_id", 0),
                            name = obj.optString("name", "Película").trim(),
                            icon = obj.optString("stream_icon", "").trim(),
                            rating = obj.optString("rating", "N/A").trim(),
                            categoryId = obj.optString("category_id", "").trim(),
                            containerExtension = obj.optString("container_extension", "mp4").trim()
                        )
                    )
                }
                categoryVodMovies = list.sortedBy { it.name.lowercase() }
                isVodLoading = false
                post { invalidate() }
            } catch (e: Exception) {
                isVodLoading = false
                post { invalidate() }
            }
        }.start()
    }

    private fun buildApiUrl(currentSession: XtreamSession, action: String): String {
        val user = URLEncoder.encode(currentSession.username, "UTF-8")
        val password = URLEncoder.encode(currentSession.password, "UTF-8")
        return "${currentSession.serverUrl}/player_api.php?username=$user&password=$password&action=$action"
    }

    private fun parseLiveCategories(response: String): List<LiveCategory> {
        val result = mutableListOf<LiveCategory>()
        val array = JSONArray(response)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("category_id", "").trim()
            val name = obj.optString("category_name", "").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) {
                result.add(LiveCategory(categoryId = id, categoryName = name))
            }
        }
        return result
    }

    private fun parseLiveChannels(response: String): List<LiveChannel> {
        val result = mutableListOf<LiveChannel>()
        val array = JSONArray(response)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val streamId = obj.optInt("stream_id", 0)
            val name = obj.optString("name", "Canal").trim()
            val icon = obj.optString("stream_icon", "").trim()
            val categoryId = obj.optString("category_id", "").trim()
            val streamType = obj.optString("stream_type", "").trim()
            val extension = obj.optString("container_extension", "ts").trim()

            if (streamId > 0) {
                result.add(LiveChannel(streamId, name, icon, categoryId, streamType, extension))
            }
        }
        return result
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "TVBoxPremium/1.0")

            val code = connection.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")

            return BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val builder = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    builder.append(line)
                }
                builder.toString()
            }
        } finally {
            connection.disconnect()
        }
    }

    // ============================================================
    // IMAGENES CACHE & PLACEHOLDERS
    // ============================================================

    private fun requestImage(url: String) {
        if (url.isBlank()) return
        synchronized(bitmapCache) { if (bitmapCache.containsKey(url)) return }
        synchronized(loadingImages) { if (loadingImages.contains(url)) return; loadingImages.add(url) }

        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "TVBoxPremium/1.0")

                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    synchronized(bitmapCache) { bitmapCache[url] = bitmap }
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
                synchronized(loadingImages) { loadingImages.remove(url) }
                post { invalidate() }
            }
        }.start()
    }

    private fun drawImageOrPlaceholder(canvas: Canvas, bitmap: Bitmap?, rect: RectF, title: String) {
        if (bitmap == null) {
            drawCategoryPlaceholder(canvas, rect.left, rect.top, rect.width(), rect.height(), title)
            return
        }
        canvas.save()
        val path = Path()
        path.addRoundRect(rect, dp(8f), dp(8f), Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restore()
    }

    private fun channelsForCategory(categoryId: String): List<LiveChannel> {
        return liveChannels.filter { it.categoryId == categoryId }
    }

    // ============================================================
    // CÁLCULOS SCROLL & RESPONSIVE
    // ============================================================

    private fun clampCategoryScroll(value: Float): Float {
        val columns = categoryColumns()
        val cardHeight = if (isMobile()) dp(138f) else dp(145f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val rows = (liveCategories.size + columns - 1) / columns
        val contentHeight = rows * (cardHeight + gap)
        val viewport = height - dp(150f) - dp(15f)
        val maxScroll = max(0f, contentHeight - viewport)
        return value.coerceIn(0f, maxScroll)
    }

    private fun clampChannelScroll(value: Float): Float {
        val columns = channelColumns()
        val cardHeight = if (isMobile()) dp(125f) else dp(135f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val rows = (categoryChannels.size + columns - 1) / columns
        val contentHeight = rows * (cardHeight + gap)
        val viewport = height - dp(145f) - dp(15f)
        val maxScroll = max(0f, contentHeight - viewport)
        return value.coerceIn(0f, maxScroll)
    }

    private fun clampVodCategoryScroll(value: Float): Float {
        val columns = categoryColumns()
        val cardHeight = if (isMobile()) dp(138f) else dp(145f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val rows = (vodCategories.size + columns - 1) / columns
        val contentHeight = rows * (cardHeight + gap)
        val viewport = height - dp(150f) - dp(15f)
        val maxScroll = max(0f, contentHeight - viewport)
        return value.coerceIn(0f, maxScroll)
    }

    private fun clampVodMovieScroll(value: Float): Float {
        val columns = categoryColumns() + 1
        val cardWidth = categoryCardWidth(columns)
        val cardHeight = cardWidth * 1.45f
        val gap = dp(14f)
        val rows = (categoryVodMovies.size + columns - 1) / columns
        val contentHeight = rows * (cardHeight + gap)
        val viewport = height - dp(90f) - dp(15f)
        val maxScroll = max(0f, contentHeight - viewport)
        return value.coerceIn(0f, maxScroll)
    }

    private fun ensureCategoryVisible() {
        val columns = categoryColumns()
        val row = selectedCategoryIndex / columns
        val cardHeight = if (isMobile()) dp(138f) else dp(145f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val itemTop = dp(150f) + row * (cardHeight + gap)
        val itemBottom = itemTop + cardHeight
        val topLimit = dp(150f)
        val bottomLimit = height - dp(10f)

        if (itemBottom - categoryScroll > bottomLimit) categoryScroll = itemBottom - bottomLimit
        if (itemTop - categoryScroll < topLimit) categoryScroll = itemTop - topLimit
        categoryScroll = clampCategoryScroll(categoryScroll)
    }

    private fun ensureChannelVisible() {
        val columns = channelColumns()
        val row = selectedChannelIndex / columns
        val cardHeight = if (isMobile()) dp(125f) else dp(135f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        val itemTop = dp(145f) + row * (cardHeight + gap)
        val itemBottom = itemTop + cardHeight
        val topLimit = dp(145f)
        val bottomLimit = height - dp(10f)

        if (itemBottom - channelScroll > bottomLimit) channelScroll = itemBottom - bottomLimit
        if (itemTop - channelScroll < topLimit) channelScroll = itemTop - topLimit
        channelScroll = clampChannelScroll(channelScroll)
    }

    private fun categoryColumns(): Int = when {
        isMobile() -> 2
        width >= dp(1300f) -> 5
        width >= dp(1000f) -> 4
        else -> 3
    }

    private fun channelColumns(): Int = when {
        isMobile() -> 1
        width >= dp(1300f) -> 4
        width >= dp(1000f) -> 3
        else -> 2
    }

    private fun categoryCardWidth(columns: Int): Float {
        val margin = if (isMobile()) dp(16f) else dp(30f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        return (width - margin * 2f - gap * (columns - 1)) / columns
    }

    private fun channelCardWidth(columns: Int): Float {
        val margin = if (isMobile()) dp(16f) else dp(30f)
        val gap = if (isMobile()) dp(12f) else dp(18f)
        return (width - margin * 2f - gap * (columns - 1)) / columns
    }

    private fun drawRoundedRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
    }

    private fun drawLoading(canvas: Canvas, centerX: Float, centerY: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = responsiveText(15f)
        textPaint.color = secondaryText
        canvas.drawText("Cargando...", centerX, centerY, textPaint)
    }

    private fun drawEmptyState(canvas: Canvas, message: String) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = responsiveText(16f)
        textPaint.color = secondaryText
        canvas.drawText(message, width / 2f, height / 2f, textPaint)
    }

    private fun drawScrollHint(canvas: Canvas, up: Boolean) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = responsiveText(18f)
        textPaint.color = Color.WHITE
        canvas.drawText(if (up) "▲" else "▼", width / 2f, dp(137f), textPaint)
    }

    private fun truncateText(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        return value.take(maxLength - 1).trimEnd() + "…"
    }

    private fun initials(value: String): String {
        val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return "TV"
        return if (words.size == 1) {
            words[0].take(2).uppercase()
        } else {
            words.take(2).joinToString("") { it.first().uppercase() }
        }
    }

    private fun isMobile(): Boolean = width < dp(700f)

    private fun responsiveText(base: Float): Float = if (isMobile()) dp(base * 0.92f) else dp(base)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
