package com.tvboxpremium

import android.app.Activity
import android.app.AlertDialog
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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
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

        // ----------------------------------------------------
        // EDITTEXT TECNICO
        // ----------------------------------------------------
        //
        // El Canvas dibuja nuestro login.
        // Este EditText invisible recibe el teclado Android.
        //
        keyboardInput = EditText(this)

        keyboardInput.setBackgroundColor(Color.TRANSPARENT)
        keyboardInput.setTextColor(Color.TRANSPARENT)
        keyboardInput.setCursorVisible(false)
        keyboardInput.alpha = 0.01f

        val keyboardParams =
            FrameLayout.LayoutParams(
                2,
                2
            )

        keyboardParams.gravity =
            Gravity.BOTTOM or Gravity.START

        keyboardParams.leftMargin = 1
        keyboardParams.bottomMargin = 1

        root.addView(
            keyboardInput,
            keyboardParams
        )

        keyboardInput.addTextChangedListener(
            object : TextWatcher {

                private var internalChange = false

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    if (internalChange) return

                    appView.updateLoginText(
                        s?.toString() ?: ""
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )

        setContentView(root)
    }

    fun openKeyboardForField(
        field: Int,
        value: String
    ) {

        keyboardInput.inputType =
            if (field == 2) {

                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD

            } else {

                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_NORMAL or
                        InputType.TYPE_TEXT_VARIATION_URI
            }

        keyboardInput.setText(value)
        keyboardInput.setSelection(
            keyboardInput.text.length
        )

        keyboardInput.requestFocus()

        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        imm.showSoftInput(
            keyboardInput,
            InputMethodManager.SHOW_IMPLICIT
        )
    }

    fun hideKeyboard() {

        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            keyboardInput.windowToken,
            0
        )

        appView.requestFocus()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // No se reinicia la sesión al cambiar orientación o tamaño.
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

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {

        if (appView.handleKey(keyCode)) {
            return true
        }

        return super.onKeyDown(
            keyCode,
            event
        )
    }
}

// ============================================================
// VISTA CANVAS
// ============================================================

class PremiumView(
    private val context: Context,
    private val activity: MainActivity
) : View(context) {

    // ========================================================
    // COLORES
    // ========================================================

    private val backgroundColor =
        Color.rgb(7, 9, 14)

    private val backgroundSecondary =
        Color.rgb(15, 18, 27)

    private val surfaceColor =
        Color.rgb(18, 22, 32)

    private val surfaceLight =
        Color.rgb(29, 34, 47)

    private val surfaceHover =
        Color.rgb(38, 44, 59)

    private val textColor =
        Color.WHITE

    private val secondaryText =
        Color.rgb(165, 172, 188)

    private val mutedText =
        Color.rgb(105, 113, 130)

    private val accentColor =
        Color.rgb(225, 35, 72)

    private val accentDark =
        Color.rgb(155, 20, 48)

    private val successColor =
        Color.rgb(70, 205, 130)

    private val selectedBorder =
        Color.WHITE

    // ========================================================
    // PAINT
    // ========================================================

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    // ========================================================
    // SESION
    // ========================================================

    private var session:
            XtreamSession? = null

    private val prefs =
        context.getSharedPreferences("tvbox_premium_session", Context.MODE_PRIVATE)

    // ========================================================
    // PANTALLA
    // ========================================================

    private var screen =
        Screen.LOGIN

    // ========================================================
    // HOME
    // ========================================================

    private var selectedSection =
        0

    // ========================================================
    // TV
    // ========================================================

    private var selectedCategoryIndex =
        0

    private var selectedChannelIndex =
        0

    private var selectedCategoryId =
        ""

    private var selectedCategoryName =
        ""

    private var categoryChannels:
            List<LiveChannel> =
        emptyList()

    private var liveCategories:
            List<LiveCategory> =
        emptyList()

    private var liveChannels:
            List<LiveChannel> =
        emptyList()

    // ========================================================
    // SCROLL
    // ========================================================

    private var categoryScroll =
        0f

    private var channelScroll =
        0f

    private var downX =
        0f

    private var downY =
        0f

    private var lastTouchX =
        0f

    private var lastTouchY =
        0f

    private var isDragging =
        false

    // ========================================================
    // LOGIN
    // ========================================================

    private var loginServer =
        ""

    private var loginUser =
        ""

    private var loginPassword =
        ""

    private var loginField =
        0

    private var loginLoading =
        false

    private var loginError =
        ""

    // ========================================================
    // IMAGENES
    // ========================================================

    private val bitmapCache =
        Collections.synchronizedMap(
            mutableMapOf<String, Bitmap>()
        )

    private val loadingImages =
        Collections.synchronizedSet(
            mutableSetOf<String>()
        )

    // ========================================================
    // CONSTRUCTOR
    // ========================================================

    init {

        isFocusable = true
        isFocusableInTouchMode = true

        requestFocus()

        restoreSavedSession()

        paint.isAntiAlias = true
        textPaint.isAntiAlias = true
    }

    fun hasActiveSession(): Boolean {
        return session != null
    }

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
        }
    }

    private fun saveSession() {
        prefs.edit()
            .putString("server", loginServer.trim().removeSuffix("/"))
            .putString("user", loginUser.trim())
            .putString("password", loginPassword)
            .apply()
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
                // El cambio de usuario elimina la sesión guardada para que
                // la próxima apertura no vuelva a entrar automáticamente.
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

    // ========================================================
    // DRAW
    // ========================================================

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        canvas.drawColor(
            backgroundColor
        )

        when (screen) {

            Screen.LOGIN ->
                drawLogin(canvas)

            Screen.HOME ->
                drawHome(canvas)

            Screen.TV_CATEGORIES ->
                drawTvCategories(canvas)

            Screen.TV_CHANNELS ->
                drawTvChannels(canvas)
        }
    }

    // ========================================================
    // LOGIN PRO
    // ========================================================

    private fun drawLogin(
        canvas: Canvas
    ) {

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        drawLoginBackground(
            canvas
        )

        // ----------------------------------------------------
        // LOGO
        // ----------------------------------------------------

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
            responsiveText(
                42f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            "TVBOX",
            w / 2f,
            dp(88f),
            textPaint
        )

        textPaint.textSize =
            responsiveText(
                13f
            )

        textPaint.color =
            accentColor

        textPaint.letterSpacing =
            0.18f

        canvas.drawText(
            "PREMIUM",
            w / 2f,
            dp(112f),
            textPaint
        )

        textPaint.letterSpacing =
            0f

        // ----------------------------------------------------
        // CONTENEDOR
        // ----------------------------------------------------

        val mobile =
            width < dp(700f)

        val boxWidth =
            if (mobile) {
                min(
                    dp(500f),
                    w - dp(32f)
                )
            } else {
                min(
                    dp(520f),
                    w - dp(60f)
                )
            }

        val boxHeight =
            if (mobile) {
                dp(380f)
            } else {
                dp(390f)
            }

        val boxLeft =
            (w - boxWidth) / 2f

        val boxTop =
            if (mobile) {
                dp(135f)
            } else {
                dp(145f)
            }

        // sombra
        paint.color =
            Color.argb(
                80,
                0,
                0,
                0
            )

        canvas.drawRoundRect(
            RectF(
                boxLeft + dp(5f),
                boxTop + dp(8f),
                boxLeft + boxWidth + dp(5f),
                boxTop + boxHeight + dp(8f)
            ),
            dp(24f),
            dp(24f),
            paint
        )

        drawRoundedRect(
            canvas,
            boxLeft,
            boxTop,
            boxLeft + boxWidth,
            boxTop + boxHeight,
            dp(24f),
            surfaceColor
        )

        // línea superior
        paint.shader =
            LinearGradient(
                boxLeft,
                0f,
                boxLeft + boxWidth,
                0f,
                accentDark,
                accentColor,
                Shader.TileMode.CLAMP
            )

        canvas.drawRoundRect(
            RectF(
                boxLeft,
                boxTop,
                boxLeft + boxWidth,
                boxTop + dp(4f)
            ),
            dp(2f),
            dp(2f),
            paint
        )

        paint.shader = null

        // ----------------------------------------------------
        // TITULO
        // ----------------------------------------------------

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                23f
            )

        textPaint.color =
            textColor

        canvas.drawText(
            "Bienvenido",
            boxLeft + dp(28f),
            boxTop + dp(48f),
            textPaint
        )

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                12f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            "Inicia sesión para acceder a tu contenido",
            boxLeft + dp(28f),
            boxTop + dp(70f),
            textPaint
        )

        // ----------------------------------------------------
        // CAMPOS
        // ----------------------------------------------------

        val fieldLeft =
            boxLeft + dp(28f)

        val fieldWidth =
            boxWidth - dp(56f)

        val fieldHeight =
            dp(54f)

        drawLoginField(
            canvas,
            fieldLeft,
            boxTop + dp(88f),
            fieldWidth,
            fieldHeight,
            "SERVIDOR",
            loginServer,
            loginField == 0
        )

        drawLoginField(
            canvas,
            fieldLeft,
            boxTop + dp(153f),
            fieldWidth,
            fieldHeight,
            "USUARIO",
            loginUser,
            loginField == 1
        )

        drawLoginField(
            canvas,
            fieldLeft,
            boxTop + dp(218f),
            fieldWidth,
            fieldHeight,
            "CONTRASEÑA",
            if (loginPassword.isEmpty()) {
                ""
            } else {
                "••••••••••"
            },
            loginField == 2
        )

        // ----------------------------------------------------
        // BOTON
        // ----------------------------------------------------

        val buttonTop =
            boxTop + dp(290f)

        val buttonBottom =
            buttonTop + dp(52f)

        val buttonColor =
            if (loginLoading) {
                accentDark
            } else {
                accentColor
            }

        drawRoundedRect(
            canvas,
            fieldLeft,
            buttonTop,
            fieldLeft + fieldWidth,
            buttonBottom,
            dp(14f),
            buttonColor
        )

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                15f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            if (loginLoading) {
                "CONECTANDO..."
            } else {
                "ENTRAR"
            },
            w / 2f,
            buttonTop + dp(33f),
            textPaint
        )

        // ----------------------------------------------------
        // ERROR
        // ----------------------------------------------------

        if (loginError.isNotEmpty()) {

            textPaint.textSize =
                responsiveText(
                    12f
                )

            textPaint.color =
                Color.rgb(
                    255,
                    105,
                    115
                )

            canvas.drawText(
                loginError,
                w / 2f,
                buttonBottom + dp(28f),
                textPaint
            )
        }

        // ----------------------------------------------------
        // AYUDA
        // ----------------------------------------------------

        textPaint.textSize =
            responsiveText(
                10f
            )

        textPaint.color =
            mutedText

        canvas.drawText(
            if (isMobile()) {
                "Toca un campo para escribir"
            } else {
                "▲ ▼ seleccionar   •   OK confirmar"
            },
            w / 2f,
            h - dp(22f),
            textPaint
        )
    }

    private fun drawLoginBackground(
        canvas: Canvas
    ) {

        paint.shader =
            LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                backgroundColor,
                backgroundSecondary,
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

        // manchas decorativas
        paint.color =
            Color.argb(
                28,
                225,
                35,
                72
            )

        canvas.drawCircle(
            width * 0.15f,
            height * 0.20f,
            dp(130f),
            paint
        )

        canvas.drawCircle(
            width * 0.90f,
            height * 0.75f,
            dp(180f),
            paint
        )
    }

    private fun drawLoginField(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        label: String,
        value: String,
        selected: Boolean
    ) {

        val border =
            if (selected) {
                accentColor
            } else {
                Color.rgb(
                    52,
                    58,
                    72
                )
            }

        paint.style =
            Paint.Style.FILL

        paint.color =
            Color.rgb(
                14,
                17,
                25
            )

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                left + width,
                top + height
            ),
            dp(12f),
            dp(12f),
            paint
        )

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            if (selected) {
                dp(2f)
            } else {
                dp(1f)
            }

        paint.color =
            border

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                left + width,
                top + height
            ),
            dp(12f),
            dp(12f),
            paint
        )

        paint.style =
            Paint.Style.FILL

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                9f
            )

        textPaint.color =
            if (selected) {
                accentColor
            } else {
                mutedText
            }

        canvas.drawText(
            label,
            left + dp(15f),
            top + dp(18f),
            textPaint
        )

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                13f
            )

        textPaint.color =
            if (value.isEmpty()) {
                mutedText
            } else {
                Color.WHITE
            }

        canvas.drawText(
            if (value.isEmpty()) {
                when (label) {
                    "SERVIDOR" ->
                        "http://servidor:puerto"

                    "USUARIO" ->
                        "Ingresa tu usuario"

                    else ->
                        "Ingresa tu contraseña"
                }
            } else {
                value
            },
            left + dp(15f),
            top + dp(40f),
            textPaint
        )
    }

    // ========================================================
    // HOME PRO
    // ========================================================

    private fun drawHome(
        canvas: Canvas
    ) {

        drawTopBar(
            canvas,
            "INICIO"
        )

        // fondo
        paint.shader =
            LinearGradient(
                0f,
                dp(55f),
                0f,
                height.toFloat(),
                backgroundSecondary,
                backgroundColor,
                Shader.TileMode.CLAMP
            )

        canvas.drawRect(
            0f,
            dp(55f),
            width.toFloat(),
            height.toFloat(),
            paint
        )

        paint.shader = null

        // ----------------------------------------------------
        // HERO
        // ----------------------------------------------------

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                if (isMobile()) 29f else 38f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            "Todo tu entretenimiento",
            dp(35f),
            dp(112f),
            textPaint
        )

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                14f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            "Televisión en directo y contenido bajo demanda",
            dp(35f),
            dp(140f),
            textPaint
        )

        // línea accent
        paint.color =
            accentColor

        canvas.drawRoundRect(
            RectF(
                dp(35f),
                dp(158f),
                dp(95f),
                dp(162f)
            ),
            dp(2f),
            dp(2f),
            paint
        )

        // ----------------------------------------------------
        // TARJETAS
        // ----------------------------------------------------

        val mobile =
            isMobile()

        if (mobile) {

            drawHomeCard(
                canvas,
                index = 0,
                left = dp(24f),
                top = dp(190f),
                right = width - dp(24f),
                bottom = dp(315f),
                title = "TV EN VIVO",
                subtitle = "Canales y categorías",
                icon = "TV"
            )

            drawHomeCard(
                canvas,
                index = 1,
                left = dp(24f),
                top = dp(335f),
                right = width - dp(24f),
                bottom = dp(460f),
                title = "PELÍCULAS",
                subtitle = "Catálogo VOD",
                icon = "▶"
            )

        } else {

            val gap =
                dp(22f)

            val totalWidth =
                min(
                    width - dp(70f),
                    dp(900f)
                )

            val left =
                (width - totalWidth) / 2f

            val cardWidth =
                (totalWidth - gap) / 2f

            drawHomeCard(
                canvas,
                0,
                left,
                dp(190f),
                left + cardWidth,
                dp(365f),
                "TV EN VIVO",
                "Canales y categorías",
                "TV"
            )

            drawHomeCard(
                canvas,
                1,
                left + cardWidth + gap,
                dp(190f),
                left + totalWidth,
                dp(365f),
                "PELÍCULAS",
                "Catálogo VOD",
                "▶"
            )
        }

        // ----------------------------------------------------
        // ESTADO
        // ----------------------------------------------------

        val count =
            liveChannels.size

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                11f
            )

        textPaint.color =
            mutedText

        canvas.drawText(
            if (count > 0) {
                "$count canales disponibles"
            } else {
                "Preparando catálogo..."
            },
            dp(35f),
            height - dp(28f),
            textPaint
        )
    }

    private fun drawHomeCard(
        canvas: Canvas,
        index: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        title: String,
        subtitle: String,
        icon: String
    ) {

        val selected =
            selectedSection == index

        val fill =
            if (selected) {
                surfaceHover
            } else {
                surfaceColor
            }

        drawRoundedRect(
            canvas,
            left,
            top,
            right,
            bottom,
            dp(20f),
            fill
        )

        // borde
        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            if (selected) {
                dp(3f)
            } else {
                dp(1f)
            }

        paint.color =
            if (selected) {
                Color.WHITE
            } else {
                Color.rgb(
                    42,
                    48,
                    61
                )
            }

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                right,
                bottom
            ),
            dp(20f),
            dp(20f),
            paint
        )

        paint.style =
            Paint.Style.FILL

        // accent vertical
        if (selected) {

            paint.color =
                accentColor

            canvas.drawRoundRect(
                RectF(
                    left,
                    top + dp(25f),
                    left + dp(5f),
                    bottom - dp(25f)
                ),
                dp(2f),
                dp(2f),
                paint
            )
        }

        // icon
        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                26f
            )

        textPaint.color =
            if (selected) {
                accentColor
            } else {
                secondaryText
            }

        canvas.drawText(
            icon,
            (left + right) / 2f,
            top + dp(60f),
            textPaint
        )

        // titulo
        textPaint.textSize =
            responsiveText(
                19f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            title,
            (left + right) / 2f,
            top + dp(100f),
            textPaint
        )

        // subtitulo
        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                12f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            subtitle,
            (left + right) / 2f,
            top + dp(124f),
            textPaint
        )

        // accion
        textPaint.textSize =
            responsiveText(
                10f
            )

        textPaint.color =
            if (selected) {
                Color.WHITE
            } else {
                mutedText
            }

        canvas.drawText(
            if (selected) {
                "ABRIR"
            } else {
                "SELECCIONAR"
            },
            (left + right) / 2f,
            bottom - dp(22f),
            textPaint
        )
    }

    // ========================================================
    // TOP BAR
    // ========================================================

    private fun drawTopBar(
        canvas: Canvas,
        current: String
    ) {

        paint.color =
            Color.rgb(
                9,
                11,
                17
            )

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            dp(58f),
            paint
        )

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                18f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            "TVBOX",
            dp(25f),
            dp(35f),
            textPaint
        )

        textPaint.textSize =
            responsiveText(
                9f
            )

        textPaint.color =
            accentColor

        textPaint.letterSpacing =
            0.12f

        canvas.drawText(
            "PREMIUM",
            dp(86f),
            dp(35f),
            textPaint
        )

        textPaint.letterSpacing =
            0f

        if (session != null) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = responsiveText(11f)
            textPaint.color = secondaryText
            canvas.drawText("CUENTA  ⋮", width - dp(22f), dp(35f), textPaint)
        }

        if (current != "INICIO") {

            textPaint.textSize =
                responsiveText(
                    11f
                )

            textPaint.color =
                secondaryText

            canvas.drawText(
                " / $current",
                dp(155f),
                dp(35f),
                textPaint
            )
        }
    }

    // ========================================================
    // TV CATEGORIAS
    // ========================================================

    private fun drawTvCategories(
        canvas: Canvas
    ) {

        drawTopBar(
            canvas,
            "TV"
        )

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                if (isMobile()) 28f else 32f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            "Televisión",
            dp(30f),
            dp(95f),
            textPaint
        )

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                13f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            if (liveCategories.isEmpty()) {
                "Cargando categorías..."
            } else {
                "${liveCategories.size} categorías"
            },
            dp(30f),
            dp(120f),
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

        // indicador scroll
        if (categoryScroll > 0f) {

            drawScrollHint(
                canvas,
                true
            )
        }

        val columns =
            categoryColumns()

        val cardWidth =
            categoryCardWidth(
                columns
            )

        val cardHeight =
            if (isMobile()) {
                dp(138f)
            } else {
                dp(145f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        val startX =
            if (isMobile()) {
                dp(16f)
            } else {
                dp(30f)
            }

        val startY =
            dp(150f) - categoryScroll

        liveCategories.forEachIndexed {
            index,
            category ->

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

            if (
                top + cardHeight >= dp(140f) &&
                top <= height
            ) {

                drawCategoryCard(
                    canvas,
                    category,
                    left,
                    top,
                    cardWidth,
                    cardHeight,
                    index ==
                            selectedCategoryIndex
                )
            }
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
            dp(15f),
            if (selected) {
                surfaceHover
            } else {
                surfaceColor
            }
        )

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            if (selected) {
                dp(3f)
            } else {
                dp(1f)
            }

        paint.color =
            if (selected) {
                selectedBorder
            } else {
                Color.rgb(
                    42,
                    48,
                    61
                )
            }

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                left + cardWidth,
                top + cardHeight
            ),
            dp(15f),
            dp(15f),
            paint
        )

        paint.style =
            Paint.Style.FILL

        val channels =
            channelsForCategory(
                category.categoryId
            )

        val logos =
            channels
                .map {
                    it.icon
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()
                .take(4)

        drawCategoryMosaic(
            canvas,
            logos,
            left + dp(8f),
            top + dp(8f),
            cardWidth - dp(16f),
            dp(90f),
            category.categoryName
        )

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                13f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            truncateText(
                category.categoryName,
                if (isMobile()) 21 else 26
            ),
            left + dp(12f),
            top + cardHeight - dp(17f),
            textPaint
        )
    }

    // ========================================================
    // MOSAICO
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

        val halfW =
            width / 2f

        val halfH =
            height / 2f

        val rects =
            listOf(
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

        urls.take(4)
            .forEachIndexed {
                index,
                url ->

                requestImage(
                    url
                )

                drawImageOrPlaceholder(
                    canvas,
                    bitmapCache[url],
                    rects[index],
                    categoryName
                )
            }

        if (urls.size < 4) {

            for (
                index
                in urls.size until 4
            ) {

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

        paint.shader =
            LinearGradient(
                left,
                top,
                left + width,
                top + height,
                Color.rgb(
                    40,
                    44,
                    59
                ),
                Color.rgb(
                    17,
                    20,
                    29
                ),
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

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                18f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            initials(title),
            left + width / 2f,
            top + height / 2f + dp(6f),
            textPaint
        )
    }

    // ========================================================
    // TV CHANNELS
    // ========================================================

    private fun drawTvChannels(
        canvas: Canvas
    ) {

        drawTopBar(
            canvas,
            "TV"
        )

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                if (isMobile()) 24f else 30f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            selectedCategoryName,
            dp(30f),
            dp(92f),
            textPaint
        )

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                12f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            "${categoryChannels.size} canales",
            dp(30f),
            dp(117f),
            textPaint
        )

        if (categoryChannels.isEmpty()) {

            drawEmptyState(
                canvas,
                "No hay canales en esta categoría"
            )

            return
        }

        val columns =
            channelColumns()

        val cardWidth =
            channelCardWidth(
                columns
            )

        val cardHeight =
            if (isMobile()) {
                dp(125f)
            } else {
                dp(135f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        val startX =
            if (isMobile()) {
                dp(16f)
            } else {
                dp(30f)
            }

        val startY =
            dp(145f) - channelScroll

        categoryChannels.forEachIndexed {
            index,
            channel ->

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

            if (
                top + cardHeight >= dp(135f) &&
                top <= height
            ) {

                drawChannelCard(
                    canvas,
                    channel,
                    left,
                    top,
                    cardWidth,
                    cardHeight,
                    index ==
                            selectedChannelIndex
                )
            }
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
            if (selected) {
                surfaceHover
            } else {
                surfaceColor
            }
        )

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            if (selected) {
                dp(3f)
            } else {
                dp(1f)
            }

        paint.color =
            if (selected) {
                Color.WHITE
            } else {
                Color.rgb(
                    42,
                    48,
                    61
                )
            }

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

        paint.style =
            Paint.Style.FILL

        val logoSize =
            if (isMobile()) {
                dp(66f)
            } else {
                dp(70f)
            }

        val logoRect =
            RectF(
                left + dp(12f),
                top + dp(12f),
                left + dp(12f) + logoSize,
                top + dp(12f) + logoSize
            )

        if (channel.icon.isNotBlank()) {

            requestImage(
                channel.icon
            )

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

        val textLeft =
            left + logoSize + dp(25f)

        textPaint.textAlign =
            Paint.Align.LEFT

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                13f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            truncateText(
                channel.name,
                if (isMobile()) 18 else 25
            ),
            textLeft,
            top + dp(40f),
            textPaint
        )

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                10f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            if (
                channel.streamType.isNotBlank()
            ) {
                channel.streamType.uppercase()
            } else {
                "TV"
            },
            textLeft,
            top + dp(60f),
            textPaint
        )

        // punto live
        paint.color =
            successColor

        canvas.drawCircle(
            textLeft,
            top + dp(84f),
            dp(4f),
            paint
        )

        textPaint.textSize =
            responsiveText(
                9f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            "EN VIVO",
            textLeft + dp(10f),
            top + dp(87f),
            textPaint
        )
    }

    private fun drawChannelPlaceholder(
        canvas: Canvas,
        rect: RectF,
        name: String
    ) {

        paint.shader =
            LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                accentDark,
                Color.rgb(
                    31,
                    35,
                    48
                ),
                Shader.TileMode.CLAMP
            )

        canvas.drawRoundRect(
            rect,
            dp(10f),
            dp(10f),
            paint
        )

        paint.shader = null

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.DEFAULT_BOLD

        textPaint.textSize =
            responsiveText(
                17f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            initials(name),
            rect.centerX(),
            rect.centerY() + dp(6f),
            textPaint
        )
    }

    // ========================================================
    // TOUCH
    // ========================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                downX =
                    event.x

                downY =
                    event.y

                lastTouchX =
                    event.x

                lastTouchY =
                    event.y

                isDragging =
                    false

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                val dx =
                    event.x - lastTouchX

                val dy =
                    event.y - lastTouchY

                if (
                    abs(event.x - downX) >
                    dp(8f) ||
                    abs(event.y - downY) >
                    dp(8f)
                ) {

                    isDragging =
                        true
                }

                if (
                    screen ==
                    Screen.TV_CATEGORIES
                ) {

                    if (
                        abs(dy) >
                        abs(dx)
                    ) {

                        categoryScroll =
                            clampCategoryScroll(
                                categoryScroll - dy
                            )

                        invalidate()
                    }

                } else if (
                    screen ==
                    Screen.TV_CHANNELS
                ) {

                    if (
                        abs(dy) >
                        abs(dx)
                    ) {

                        channelScroll =
                            clampChannelScroll(
                                channelScroll - dy
                            )

                        invalidate()
                    }
                }

                lastTouchX =
                    event.x

                lastTouchY =
                    event.y

                return true
            }

            MotionEvent.ACTION_UP -> {

                if (!isDragging) {

                    handleTap(
                        event.x,
                        event.y
                    )

                } else {

                    // swipe horizontal
                    val dx =
                        event.x - downX

                    if (
                        abs(dx) >
                        dp(80f)
                    ) {

                        if (
                            screen ==
                            Screen.HOME
                        ) {

                            if (dx < 0) {
                                selectedSection =
                                    min(
                                        1,
                                        selectedSection + 1
                                    )
                            } else {
                                selectedSection =
                                    max(
                                        0,
                                        selectedSection - 1
                                    )
                            }

                            invalidate()
                        }
                    }
                }

                return true
            }
        }

        return true
    }

    // ========================================================
    // TOUCH TAP
    // ========================================================

    private fun handleTap(
        x: Float,
        y: Float
    ) {

        when (screen) {

            // ------------------------------------------------
            // LOGIN
            // ------------------------------------------------

            Screen.LOGIN -> {

                handleLoginTap(
                    x,
                    y
                )
            }

            // ------------------------------------------------
            // HOME
            // ------------------------------------------------

            Screen.HOME -> {

                if (session != null && x > width - dp(145f) && y < dp(65f)) {
                    showAccountMenu()
                    return
                }

                val mobile =
                    isMobile()

                if (mobile) {

                    val tvRect =
                        RectF(
                            dp(24f),
                            dp(190f),
                            width - dp(24f),
                            dp(315f)
                        )

                    val vodRect =
                        RectF(
                            dp(24f),
                            dp(335f),
                            width - dp(24f),
                            dp(460f)
                        )

                    if (
                        tvRect.contains(
                            x,
                            y
                        )
                    ) {

                        selectedSection = 0

                        openTv()

                        return
                    }

                    if (
                        vodRect.contains(
                            x,
                            y
                        )
                    ) {

                        selectedSection = 1

                        Toast.makeText(
                            context,
                            "Películas se habilitará en la siguiente etapa",
                            Toast.LENGTH_SHORT
                        ).show()

                        return
                    }

                } else {

                    val totalWidth =
                        min(
                            width - dp(70f),
                            dp(900f)
                        )

                    val gap =
                        dp(22f)

                    val left =
                        (width - totalWidth) / 2f

                    val cardWidth =
                        (totalWidth - gap) / 2f

                    val tvRect =
                        RectF(
                            left,
                            dp(190f),
                            left + cardWidth,
                            dp(365f)
                        )

                    val vodRect =
                        RectF(
                            left + cardWidth + gap,
                            dp(190f),
                            left + totalWidth,
                            dp(365f)
                        )

                    if (
                        tvRect.contains(
                            x,
                            y
                        )
                    ) {

                        selectedSection = 0
                        openTv()
                        return
                    }

                    if (
                        vodRect.contains(
                            x,
                            y
                        )
                    ) {

                        selectedSection = 1

                        Toast.makeText(
                            context,
                            "Películas se habilitará en la siguiente etapa",
                            Toast.LENGTH_SHORT
                        ).show()

                        return
                    }
                }
            }

            // ------------------------------------------------
            // CATEGORIAS
            // ------------------------------------------------

            Screen.TV_CATEGORIES -> {

                val columns =
                    categoryColumns()

                val cardWidth =
                    categoryCardWidth(
                        columns
                    )

                val cardHeight =
                    if (isMobile()) {
                        dp(138f)
                    } else {
                        dp(145f)
                    }

                val gap =
                    if (isMobile()) {
                        dp(12f)
                    } else {
                        dp(18f)
                    }

                val startX =
                    if (isMobile()) {
                        dp(16f)
                    } else {
                        dp(30f)
                    }

                val startY =
                    dp(150f) -
                            categoryScroll

                liveCategories.forEachIndexed {
                    index,
                    category ->

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

                    if (
                        rect.contains(
                            x,
                            y
                        )
                    ) {

                        selectedCategoryIndex =
                            index

                        openSelectedCategory()

                        return
                    }
                }
            }

            // ------------------------------------------------
            // CANALES
            // ------------------------------------------------

            Screen.TV_CHANNELS -> {

                val columns =
                    channelColumns()

                val cardWidth =
                    channelCardWidth(
                        columns
                    )

                val cardHeight =
                    if (isMobile()) {
                        dp(125f)
                    } else {
                        dp(135f)
                    }

                val gap =
                    if (isMobile()) {
                        dp(12f)
                    } else {
                        dp(18f)
                    }

                val startX =
                    if (isMobile()) {
                        dp(16f)
                    } else {
                        dp(30f)
                    }

                val startY =
                    dp(145f) -
                            channelScroll

                categoryChannels.forEachIndexed {
                    index,
                    channel ->

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

                    if (
                        rect.contains(
                            x,
                            y
                        )
                    ) {

                        selectedChannelIndex =
                            index

                        Toast.makeText(
                            context,
                            channel.name,
                            Toast.LENGTH_SHORT
                        ).show()

                        invalidate()

                        return
                    }
                }
            }
        }
    }

    // ========================================================
    // LOGIN TOUCH
    // ========================================================

    private fun handleLoginTap(
        x: Float,
        y: Float
    ) {

        val w =
            width.toFloat()

        val mobile =
            isMobile()

        val boxWidth =
            if (mobile) {
                min(
                    dp(500f),
                    w - dp(32f)
                )
            } else {
                min(
                    dp(520f),
                    w - dp(60f)
                )
            }

        val boxLeft =
            (w - boxWidth) / 2f

        val boxTop =
            if (mobile) {
                dp(135f)
            } else {
                dp(145f)
            }

        val fieldLeft =
            boxLeft + dp(28f)

        val fieldWidth =
            boxWidth - dp(56f)

        val fields =
            listOf(
                RectF(
                    fieldLeft,
                    boxTop + dp(88f),
                    fieldLeft + fieldWidth,
                    boxTop + dp(142f)
                ),
                RectF(
                    fieldLeft,
                    boxTop + dp(153f),
                    fieldLeft + fieldWidth,
                    boxTop + dp(207f)
                ),
                RectF(
                    fieldLeft,
                    boxTop + dp(218f),
                    fieldLeft + fieldWidth,
                    boxTop + dp(272f)
                )
            )

        fields.forEachIndexed {
            index,
            rect ->

            if (
                rect.contains(
                    x,
                    y
                )
            ) {

                selectLoginField(
                    index
                )

                return
            }
        }

        val button =
            RectF(
                fieldLeft,
                boxTop + dp(290f),
                fieldLeft + fieldWidth,
                boxTop + dp(342f)
            )

        if (
            button.contains(
                x,
                y
            )
        ) {

            performLogin()
        }
    }

    private fun selectLoginField(
        field: Int
    ) {

        loginField =
            field

        val value =
            when (field) {
                0 -> loginServer
                1 -> loginUser
                else -> loginPassword
            }

        activity.openKeyboardForField(
            field,
            value
        )

        invalidate()
    }

    fun updateLoginText(
        value: String
    ) {

        when (loginField) {

            0 ->
                loginServer = value

            1 ->
                loginUser = value

            2 ->
                loginPassword = value
        }

        invalidate()
    }

    // ========================================================
    // LOGIN TECLADO
    // ========================================================

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
                        2,
                        loginField + 1
                    )

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {

                if (
                    loginField == 2
                ) {

                    performLogin()

                } else {

                    selectLoginField(
                        loginField + 1
                    )
                }

                return true
            }
        }

        return false
    }

    // ========================================================
    // TV
    // ========================================================

    private fun openTv() {

        screen =
            Screen.TV_CATEGORIES

        selectedCategoryIndex =
            0

        categoryScroll =
            0f

        if (
            liveCategories.isEmpty()
        ) {

            loadTvData()
        }

        activity.hideKeyboard()

        invalidate()
    }

    private fun openSelectedCategory() {

        if (
            liveCategories.isEmpty()
        ) {
            return
        }

        selectedCategoryIndex =
            selectedCategoryIndex.coerceIn(
                0,
                liveCategories.lastIndex
            )

        val category =
            liveCategories[
                selectedCategoryIndex
            ]

        selectedCategoryId =
            category.categoryId

        selectedCategoryName =
            category.categoryName

        categoryChannels =
            channelsForCategory(
                category.categoryId
            ).sortedBy {
                it.name.lowercase()
            }

        selectedChannelIndex =
            0

        channelScroll =
            0f

        screen =
            Screen.TV_CHANNELS

        invalidate()
    }

    // ========================================================
    // D-PAD HOME
    // ========================================================

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

                if (
                    selectedSection == 0
                ) {

                    openTv()

                } else {

                    Toast.makeText(
                        context,
                        "Películas se habilitará en la siguiente etapa",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                return true
            }
        }

        return false
    }

    // ========================================================
    // D-PAD CATEGORIAS
    // ========================================================

    private fun handleCategoryKey(
        keyCode: Int
    ): Boolean {

        if (
            liveCategories.isEmpty()
        ) {
            return true
        }

        val columns =
            categoryColumns()

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                if (
                    selectedCategoryIndex %
                    columns > 0
                ) {

                    selectedCategoryIndex--

                    ensureCategoryVisible()

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

                    ensureCategoryVisible()

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {

                val newIndex =
                    selectedCategoryIndex -
                            columns

                if (
                    newIndex >= 0
                ) {

                    selectedCategoryIndex =
                        newIndex

                    ensureCategoryVisible()

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                val newIndex =
                    selectedCategoryIndex +
                            columns

                if (
                    newIndex <=
                    liveCategories.lastIndex
                ) {

                    selectedCategoryIndex =
                        newIndex

                    ensureCategoryVisible()

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

    // ========================================================
    // D-PAD CANALES
    // ========================================================

    private fun handleChannelKey(
        keyCode: Int
    ): Boolean {

        if (
            categoryChannels.isEmpty()
        ) {
            return true
        }

        val columns =
            channelColumns()

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                if (
                    selectedChannelIndex %
                    columns > 0
                ) {

                    selectedChannelIndex--

                    ensureChannelVisible()

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

                    ensureChannelVisible()

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {

                val newIndex =
                    selectedChannelIndex -
                            columns

                if (
                    newIndex >= 0
                ) {

                    selectedChannelIndex =
                        newIndex

                    ensureChannelVisible()

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                val newIndex =
                    selectedChannelIndex +
                            columns

                if (
                    newIndex <=
                    categoryChannels.lastIndex
                ) {

                    selectedChannelIndex =
                        newIndex

                    ensureChannelVisible()

                    invalidate()
                }

                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {

                val channel =
                    categoryChannels[
                        selectedChannelIndex
                    ]

                Toast.makeText(
                    context,
                    channel.name,
                    Toast.LENGTH_SHORT
                ).show()

                return true
            }
        }

        return false
    }

    // ========================================================
    // D-PAD GENERAL
    // ========================================================

    fun handleKey(keyCode: Int): Boolean {
        return when (screen) {
            Screen.LOGIN -> handleLoginKey(keyCode)
            Screen.HOME -> handleHomeKey(keyCode)
            Screen.TV_CATEGORIES -> handleCategoryKey(keyCode)
            Screen.TV_CHANNELS -> handleChannelKey(keyCode)
        }
    }

    // ========================================================
    // BACK
    // ========================================================

    fun handleBack(): Boolean {

        when (screen) {

            Screen.TV_CHANNELS -> {

                screen =
                    Screen.TV_CATEGORIES

                channelScroll =
                    0f

                invalidate()

                return true
            }

            Screen.TV_CATEGORIES -> {

                screen =
                    Screen.HOME

                categoryScroll =
                    0f

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
    // NETWORK LOGIN
    // ========================================================

    private fun performLogin() {

        if (
            loginLoading
        ) {
            return
        }

        loginError =
            ""

        val server =
            loginServer
                .trim()
                .removeSuffix("/")

        val user =
            loginUser.trim()

        val password =
            loginPassword

        if (
            server.isEmpty()
        ) {

            loginError =
                "Ingresa el servidor"

            invalidate()

            return
        }

        if (
            user.isEmpty()
        ) {

            loginError =
                "Ingresa el usuario"

            invalidate()

            return
        }

        if (
            password.isEmpty()
        ) {

            loginError =
                "Ingresa la contraseña"

            invalidate()

            return
        }

        activity.hideKeyboard()

        loginLoading =
            true

        invalidate()

        Thread {

            try {

                val base =
                    if (
                        server.startsWith(
                            "http://"
                        ) ||
                        server.startsWith(
                            "https://"
                        )
                    ) {
                        server
                    } else {
                        "http://$server"
                    }

                val encodedUser =
                    URLEncoder.encode(
                        user,
                        "UTF-8"
                    )

                val encodedPassword =
                    URLEncoder.encode(
                        password,
                        "UTF-8"
                    )

                val apiUrl =
                    "$base/player_api.php" +
                            "?username=$encodedUser" +
                            "&password=$encodedPassword"

                val response =
                    httpGet(
                        apiUrl
                    )

                val json =
                    JSONObject(
                        response
                    )

                val userInfo =
                    json.optJSONObject(
                        "user_info"
                    )

                val auth =
                    userInfo?.optInt(
                        "auth",
                        0
                    ) ?: 0

                if (
                    auth != 1
                ) {

                    post {

                        loginLoading =
                            false

                        loginError =
                            "Usuario o contraseña incorrectos"

                        invalidate()
                    }

                    return@Thread
                }

                session =
                    XtreamSession(
                        serverUrl = base,
                        username = user,
                        password = password
                    )

                saveSession()

                post {

                    loginLoading =
                        false

                    loginError =
                        ""

                    screen =
                        Screen.HOME

                    selectedSection =
                        0

                    invalidate()
                }

                loadTvData()

            } catch (
                e: Exception
            ) {

                post {

                    loginLoading =
                        false

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
            session
                ?: return

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
                    httpGet(
                        categoriesUrl
                    )

                val streamsResponse =
                    httpGet(
                        streamsUrl
                    )

                val categories =
                    parseLiveCategories(
                        categoriesResponse
                    )

                val channels =
                    parseLiveChannels(
                        streamsResponse
                    )

                liveCategories =
                    categories
                        .distinctBy {
                            it.categoryId
                        }
                        .sortedBy {
                            it.categoryName.lowercase()
                        }

                liveChannels =
                    channels
                        .distinctBy {
                            it.streamId
                        }
                        .sortedBy {
                            it.name.lowercase()
                        }

                post {
                    invalidate()
                }

            } catch (
                e: Exception
            ) {

                post {

                    if (
                        screen ==
                        Screen.TV_CATEGORIES
                    ) {

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
    // PARSER CATEGORIAS
    // ========================================================

    private fun parseLiveCategories(
        response: String
    ): List<LiveCategory> {

        val result =
            mutableListOf<LiveCategory>()

        val array =
            JSONArray(
                response
            )

        for (
            i in 0 until array.length()
        ) {

            val obj =
                array.optJSONObject(
                    i
                )
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

            if (
                id.isNotEmpty() &&
                name.isNotEmpty()
            ) {

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
    // PARSER CANALES
    // ========================================================

    private fun parseLiveChannels(
        response: String
    ): List<LiveChannel> {

        val result =
            mutableListOf<LiveChannel>()

        val array =
            JSONArray(
                response
            )

        for (
            i in 0 until array.length()
        ) {

            val obj =
                array.optJSONObject(
                    i
                )
                    ?: continue

            val streamId =
                try {

                    obj.optInt(
                        "stream_id",
                        0
                    )

                } catch (
                    e: Exception
                ) {

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

            if (
                streamId > 0
            ) {

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
    // HTTP
    // ========================================================

    private fun httpGet(
        urlString: String
    ): String {

        val connection =
            URL(
                urlString
            )
                .openConnection()
                    as HttpURLConnection

        try {

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                10000

            connection.readTimeout =
                15000

            connection.instanceFollowRedirects =
                true

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

            if (
                code !in 200..299
            ) {

                throw Exception(
                    "HTTP $code"
                )
            }

            return BufferedReader(
                InputStreamReader(
                    connection.inputStream
                )
            ).use { reader ->

                val builder =
                    StringBuilder()

                while (true) {

                    val line =
                        reader.readLine()
                            ?: break

                    builder.append(
                        line
                    )
                }

                builder.toString()
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

        if (
            url.isBlank()
        ) {
            return
        }

        synchronized(
            bitmapCache
        ) {

            if (
                bitmapCache.containsKey(
                    url
                )
            ) {
                return
            }
        }

        synchronized(
            loadingImages
        ) {

            if (
                loadingImages.contains(
                    url
                )
            ) {
                return
            }

            loadingImages.add(
                url
            )
        }

        Thread {

            var connection:
                    HttpURLConnection? =
                null

            try {

                connection =
                    URL(
                        url
                    )
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    8000

                connection.readTimeout =
                    10000

                connection.instanceFollowRedirects =
                    true

                connection.setRequestProperty(
                    "User-Agent",
                    "TVBoxPremium/1.0"
                )

                val bitmap =
                    connection.inputStream.use {
                        BitmapFactory.decodeStream(
                            it
                        )
                    }

                if (
                    bitmap != null
                ) {

                    synchronized(
                        bitmapCache
                    ) {

                        bitmapCache[url] =
                            bitmap
                    }
                }

            } catch (
                e: Exception
            ) {

                // Placeholder.

            } finally {

                connection?.disconnect()

                synchronized(
                    loadingImages
                ) {

                    loadingImages.remove(
                        url
                    )
                }

                post {
                    invalidate()
                }
            }

        }.start()
    }

    // ========================================================
    // IMAGEN
    // ========================================================

    private fun drawImageOrPlaceholder(
        canvas: Canvas,
        bitmap: Bitmap?,
        rect: RectF,
        title: String
    ) {

        if (
            bitmap == null
        ) {

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

        canvas.clipPath(
            path
        )

        canvas.drawBitmap(
            bitmap,
            null,
            rect,
            paint
        )

        canvas.restore()
    }

    // ========================================================
    // CATEGORIAS
    // ========================================================

    private fun channelsForCategory(
        categoryId: String
    ): List<LiveChannel> {

        return liveChannels.filter {
            it.categoryId ==
                    categoryId
        }
    }

    // ========================================================
    // SCROLL
    // ========================================================

    private fun clampCategoryScroll(
        value: Float
    ): Float {

        val columns =
            categoryColumns()

        val cardHeight =
            if (isMobile()) {
                dp(138f)
            } else {
                dp(145f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        val rows =
            (
                liveCategories.size +
                        columns - 1
                ) / columns

        val contentHeight =
            rows *
                    (cardHeight + gap)

        val viewport =
            height -
                    dp(150f) -
                    dp(15f)

        val maxScroll =
            max(
                0f,
                contentHeight -
                        viewport
            )

        return value.coerceIn(
            0f,
            maxScroll
        )
    }

    private fun clampChannelScroll(
        value: Float
    ): Float {

        val columns =
            channelColumns()

        val cardHeight =
            if (isMobile()) {
                dp(125f)
            } else {
                dp(135f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        val rows =
            (
                categoryChannels.size +
                        columns - 1
                ) / columns

        val contentHeight =
            rows *
                    (cardHeight + gap)

        val viewport =
            height -
                    dp(145f) -
                    dp(15f)

        val maxScroll =
            max(
                0f,
                contentHeight -
                        viewport
            )

        return value.coerceIn(
            0f,
            maxScroll
        )
    }

    private fun ensureCategoryVisible() {

        val columns =
            categoryColumns()

        val row =
            selectedCategoryIndex /
                    columns

        val cardHeight =
            if (isMobile()) {
                dp(138f)
            } else {
                dp(145f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        val itemTop =
            dp(150f) +
                    row *
                    (cardHeight + gap)

        val itemBottom =
            itemTop +
                    cardHeight

        val topLimit =
            dp(150f)

        val bottomLimit =
            height -
                    dp(10f)

        if (
            itemBottom -
                    categoryScroll >
            bottomLimit
        ) {

            categoryScroll =
                itemBottom -
                        bottomLimit
        }

        if (
            itemTop -
                    categoryScroll <
            topLimit
        ) {

            categoryScroll =
                itemTop -
                        topLimit
        }

        categoryScroll =
            clampCategoryScroll(
                categoryScroll
            )
    }

    private fun ensureChannelVisible() {

        val columns =
            channelColumns()

        val row =
            selectedChannelIndex /
                    columns

        val cardHeight =
            if (isMobile()) {
                dp(125f)
            } else {
                dp(135f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        val itemTop =
            dp(145f) +
                    row *
                    (cardHeight + gap)

        val itemBottom =
            itemTop +
                    cardHeight

        val topLimit =
            dp(145f)

        val bottomLimit =
            height -
                    dp(10f)

        if (
            itemBottom -
                    channelScroll >
            bottomLimit
        ) {

            channelScroll =
                itemBottom -
                        bottomLimit
        }

        if (
            itemTop -
                    channelScroll <
            topLimit
        ) {

            channelScroll =
                itemTop -
                        topLimit
        }

        channelScroll =
            clampChannelScroll(
                channelScroll
            )
    }

    // ========================================================
    // COLUMNAS RESPONSIVAS
    // ========================================================

    private fun categoryColumns(): Int {

        return when {

            isMobile() ->
                2

            width >= dp(1300f) ->
                5

            width >= dp(1000f) ->
                4

            else ->
                3
        }
    }

    private fun channelColumns(): Int {

        return when {

            isMobile() ->
                1

            width >= dp(1300f) ->
                4

            width >= dp(1000f) ->
                3

            else ->
                2
        }
    }

    private fun categoryCardWidth(
        columns: Int
    ): Float {

        val margin =
            if (isMobile()) {
                dp(16f)
            } else {
                dp(30f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        return (
            width -
                    margin * 2f -
                    gap *
                    (columns - 1)
            ) / columns
    }

    private fun channelCardWidth(
        columns: Int
    ): Float {

        val margin =
            if (isMobile()) {
                dp(16f)
            } else {
                dp(30f)
            }

        val gap =
            if (isMobile()) {
                dp(12f)
            } else {
                dp(18f)
            }

        return (
            width -
                    margin * 2f -
                    gap *
                    (columns - 1)
            ) / columns
    }

    // ========================================================
    // UI HELPERS
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

        paint.shader =
            null

        paint.style =
            Paint.Style.FILL

        paint.color =
            color

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

    private fun drawLoading(
        canvas: Canvas,
        centerX: Float,
        centerY: Float
    ) {

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.typeface =
            Typeface.DEFAULT

        textPaint.textSize =
            responsiveText(
                15f
            )

        textPaint.color =
            secondaryText

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

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.textSize =
            responsiveText(
                16f
            )

        textPaint.color =
            secondaryText

        canvas.drawText(
            message,
            width / 2f,
            height / 2f,
            textPaint
        )
    }

    private fun drawScrollHint(
        canvas: Canvas,
        up: Boolean
    ) {

        textPaint.textAlign =
            Paint.Align.CENTER

        textPaint.textSize =
            responsiveText(
                18f
            )

        textPaint.color =
            Color.WHITE

        canvas.drawText(
            if (up) "▲" else "▼",
            width / 2f,
            dp(137f),
            textPaint
        )
    }

    private fun truncateText(
        value: String,
        maxLength: Int
    ): String {

        if (
            value.length <= maxLength
        ) {
            return value
        }

        return value
            .take(
                maxLength - 1
            )
            .trimEnd() +
                "…"
    }

    private fun initials(
        value: String
    ): String {

        val words =
            value
                .trim()
                .split(
                    Regex(
                        "\\s+"
                    )
                )
                .filter {
                    it.isNotBlank()
                }

        if (
            words.isEmpty()
        ) {
            return "TV"
        }

        return if (
            words.size == 1
        ) {

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

    private fun isMobile(): Boolean {

        return width < dp(700f)
    }

    private fun responsiveText(
        base: Float
    ): Float {

        return if (
            isMobile()
        ) {
            dp(base * 0.92f)
        } else {
            dp(base)
        }
    }

    private fun dp(
        value: Float
    ): Float {

        return value *
                resources
                    .displayMetrics
                    .density
    }
}
