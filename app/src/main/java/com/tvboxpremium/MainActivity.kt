package com.tvboxpremium

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    companion object {
        private const val SERVER_URL =
            "http://38.242.252.80:80"
    }

    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor =
            Color.rgb(2, 8, 16)

        window.navigationBarColor =
            Color.rgb(2, 8, 16)

        root = FrameLayout(this)

        setContentView(root)

        showLogin()
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private fun showLogin() {

        root.removeAllViews()

        root.addView(
            LoginView(
                context = this,
                serverUrl = SERVER_URL
            ) { username, password ->

                authenticate(
                    username,
                    password
                )
            }
        )
    }

    // =========================================================
    // AUTENTICACIÓN XTREAM
    // =========================================================

    private fun authenticate(
        username: String,
        password: String
    ) {

        Thread {

            var connection:
                    HttpURLConnection? = null

            try {

                val encodedUser =
                    URLEncoder.encode(
                        username,
                        "UTF-8"
                    )

                val encodedPassword =
                    URLEncoder.encode(
                        password,
                        "UTF-8"
                    )

                val apiUrl =
                    "$SERVER_URL/player_api.php" +
                            "?username=$encodedUser" +
                            "&password=$encodedPassword"

                val url =
                    URL(apiUrl)

                connection =
                    url.openConnection()
                            as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    10000

                connection.instanceFollowRedirects =
                    true

                val responseCode =
                    connection.responseCode

                if (
                    responseCode !in
                    200..299
                ) {

                    runOnUiThread {

                        showLoginError(
                            "No se pudo conectar al servidor."
                        )
                    }

                    return@Thread
                }

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                val json =
                    JSONObject(response)

                val userInfo =
                    json.optJSONObject(
                        "user_info"
                    )

                if (userInfo == null) {

                    runOnUiThread {

                        showLoginError(
                            "Respuesta inválida del servidor."
                        )
                    }

                    return@Thread
                }

                val auth =
                    userInfo.optString(
                        "auth",
                        "0"
                    )

                if (
                    auth == "1" ||
                    auth.equals(
                        "true",
                        ignoreCase = true
                    )
                ) {

                    val server =
                        XtreamSession(
                            serverUrl = SERVER_URL,
                            username = username,
                            password = password
                        )

                    runOnUiThread {

                        hideKeyboard()

                        showHome(
                            server
                        )
                    }

                } else {

                    val status =
                        userInfo.optString(
                            "status",
                            ""
                        )

                    runOnUiThread {

                        if (
                            status.isNotEmpty()
                        ) {

                            showLoginError(
                                "Acceso rechazado: $status"
                            )

                        } else {

                            showLoginError(
                                "Usuario o contraseña incorrectos."
                            )
                        }
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    showLoginError(
                        "Error de conexión: ${e.message ?: "servidor no disponible"}"
                    )
                }

            } finally {

                connection?.disconnect()
            }

        }.start()
    }

    // =========================================================
    // ERROR LOGIN
    // =========================================================

    private fun showLoginError(
        message: String
    ) {

        val view =
            root.getChildAt(0)

        if (
            view is LoginView
        ) {

            view.showError(
                message
            )
        }
    }

    // =========================================================
    // HOME
    // =========================================================

    private fun showHome(
        session: XtreamSession
    ) {

        root.removeAllViews()

        root.addView(
            HomeView(
                context = this,
                session = session
            )
        )
    }

    private fun hideKeyboard() {

        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            root.windowToken,
            0
        )
    }

    override fun onBackPressed() {

        if (
            root.childCount > 0 &&
            root.getChildAt(0) is HomeView
        ) {

            showLogin()

        } else {

            super.onBackPressed()
        }
    }
}


// =============================================================
// SESIÓN XTREAM Y MODELOS TV
// =============================================================

data class XtreamSession(
    val serverUrl: String,
    val username: String,
    val password: String
)

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


// =============================================================
// LOGIN VIEW
// =============================================================

class LoginView(
    context: Context,
    private val serverUrl: String,
    private val onLogin:
        (String, String) -> Unit
) : FrameLayout(context) {

    private val background =
        LoginBackground(context)

    private val username =
        EditText(context)

    private val password =
        EditText(context)

    private val loginButton =
        TextView(context)

    private val errorText =
        TextView(context)

    init {

        setWillNotDraw(false)

        addView(
            background,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        createLogin()
    }

    private fun createLogin() {

        // =====================================================
        // SERVIDOR
        // =====================================================

        val serverText =
            TextView(context)

        serverText.text =
            serverUrl

        serverText.gravity =
            Gravity.CENTER

        serverText.setTextColor(
            Color.rgb(
                90,
                150,
                205
            )
        )

        serverText.textSize =
            12f

        val serverParams =
            LayoutParams(
                dp(450),
                dp(35)
            )

        serverParams.gravity =
            Gravity.CENTER

        serverParams.topMargin =
            dp(-125)

        addView(
            serverText,
            serverParams
        )

        // =====================================================
        // USUARIO
        // =====================================================

        username.setSingleLine(true)

        username.hint =
            "Usuario"

        username.setTextColor(
            Color.WHITE
        )

        username.setHintTextColor(
            Color.rgb(
                130,
                150,
                170
            )
        )

        username.textSize =
            17f

        username.setPadding(
            dp(22),
            0,
            dp(22),
            0
        )

        username.background =
            roundedBackground(
                Color.rgb(
                    10,
                    28,
                    47
                ),
                Color.rgb(
                    30,
                    75,
                    110
                )
            )

        val userParams =
            LayoutParams(
                dp(390),
                dp(58)
            )

        userParams.gravity =
            Gravity.CENTER

        userParams.topMargin =
            dp(-55)

        addView(
            username,
            userParams
        )

        // =====================================================
        // CONTRASEÑA
        // =====================================================

        password.setSingleLine(true)

        password.hint =
            "Contraseña"

        password.setTextColor(
            Color.WHITE
        )

        password.setHintTextColor(
            Color.rgb(
                130,
                150,
                170
            )
        )

        password.textSize =
            17f

        password.setPadding(
            dp(22),
            0,
            dp(22),
            0
        )

        password.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD

        password.background =
            roundedBackground(
                Color.rgb(
                    10,
                    28,
                    47
                ),
                Color.rgb(
                    30,
                    75,
                    110
                )
            )

        val passParams =
            LayoutParams(
                dp(390),
                dp(58)
            )

        passParams.gravity =
            Gravity.CENTER

        passParams.topMargin =
            dp(15)

        addView(
            password,
            passParams
        )

        // =====================================================
        // BOTÓN
        // =====================================================

        loginButton.text =
            "INICIAR SESIÓN"

        loginButton.gravity =
            Gravity.CENTER

        loginButton.setTextColor(
            Color.WHITE
        )

        loginButton.textSize =
            16f

        loginButton.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        loginButton.isFocusable =
            true

        loginButton.isClickable =
            true

        loginButton.background =
            roundedBackground(
                Color.rgb(
                    15,
                    115,
                    225
                ),
                Color.rgb(
                    70,
                    175,
                    255
                )
            )

        val loginParams =
            LayoutParams(
                dp(390),
                dp(58)
            )

        loginParams.gravity =
            Gravity.CENTER

        loginParams.topMargin =
            dp(100)

        addView(
            loginButton,
            loginParams
        )

        loginButton.setOnClickListener {

            val user =
                username.text
                    .toString()
                    .trim()

            val pass =
                password.text
                    .toString()

            if (
                user.isEmpty() ||
                pass.isEmpty()
            ) {

                showError(
                    "Ingresa usuario y contraseña."
                )

                return@setOnClickListener
            }

            loginButton.text =
                "CONECTANDO..."

            loginButton.isEnabled =
                false

            errorText.text = ""

            onLogin(
                user,
                pass
            )
        }

        // =====================================================
        // ERROR
        // =====================================================

        errorText.gravity =
            Gravity.CENTER

        errorText.setTextColor(
            Color.rgb(
                255,
                105,
                105
            )
        )

        errorText.textSize =
            13f

        val errorParams =
            LayoutParams(
                dp(500),
                dp(45)
            )

        errorParams.gravity =
            Gravity.CENTER

        errorParams.topMargin =
            dp(165)

        addView(
            errorText,
            errorParams
        )
    }

    fun showError(
        message: String
    ) {

        loginButton.text =
            "INICIAR SESIÓN"

        loginButton.isEnabled =
            true

        errorText.text =
            message

        username.requestFocus()
    }

    override fun onAttachedToWindow() {

        super.onAttachedToWindow()

        username.requestFocus()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        when (keyCode) {

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                if (
                    username.hasFocus()
                ) {

                    password.requestFocus()

                    return true
                }

                if (
                    password.hasFocus()
                ) {

                    loginButton.requestFocus()

                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {

                if (
                    loginButton.hasFocus()
                ) {

                    password.requestFocus()

                    return true
                }

                if (
                    password.hasFocus()
                ) {

                    username.requestFocus()

                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {

                if (
                    loginButton.hasFocus()
                ) {

                    loginButton.performClick()

                    return true
                }
            }
        }

        return super.onKeyDown(
            keyCode,
            event
        )
    }

    private fun roundedBackground(
        fill: Int,
        stroke: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(fill)

            cornerRadius =
                dp(12).toFloat()

            setStroke(
                dp(1),
                stroke
            )
        }
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                    resources.displayMetrics.density
            ).toInt()
    }
}


// =============================================================
// LOGIN BACKGROUND
// =============================================================

class LoginBackground(
    context: Context
) : View(context) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        canvas.drawColor(
            Color.rgb(
                2,
                9,
                18
            )
        )

        paint.color =
            Color.rgb(
                4,
                29,
                52
            )

        canvas.drawCircle(
            w * 0.82f,
            h * 0.20f,
            w * 0.35f,
            paint
        )

        paint.color =
            Color.rgb(
                3,
                20,
                38
            )

        canvas.drawCircle(
            w * 0.18f,
            h * 0.90f,
            w * 0.40f,
            paint
        )

        paint.color =
            Color.WHITE

        paint.textAlign =
            Paint.Align.CENTER

        paint.textSize =
            min(
                w * 0.055f,
                58f
            )

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        canvas.drawText(
            "▶ TVBOX",
            w / 2f,
            h * 0.29f,
            paint
        )

        paint.color =
            Color.rgb(
                55,
                165,
                255
            )

        paint.textSize =
            min(
                w * 0.022f,
                22f
            )

        paint.typeface =
            Typeface.DEFAULT

        canvas.drawText(
            "PREMIUM",
            w / 2f,
            h * 0.34f,
            paint
        )

        paint.color =
            Color.rgb(
                190,
                210,
                230
            )

        paint.textSize =
            min(
                w * 0.018f,
                18f
            )

        canvas.drawText(
            "Inicia sesión para continuar",
            w / 2f,
            h * 0.405f,
            paint
        )

        paint.textAlign =
            Paint.Align.LEFT
    }
}


// =============================================================
// HOME
// =============================================================

class HomeView(
    context: Context,
    private val session: XtreamSession
) : View(context) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        loadLiveData()
    }

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
    // DATOS TV REALES Y DEMO PELÍCULAS
    // =========================================================

    private val sections =
        arrayOf(
            "Inicio",
            "TV",
            "Películas",
            "Buscar",
            "Favoritos",
            "Configuración"
        )

    private val channels =
        mutableListOf<String>()

    private val liveChannels =
        mutableListOf<LiveChannel>()

    private val liveCategories =
        mutableListOf<LiveCategory>()

    private var loadingLive = true
    private var liveError = ""

    private val movies =
        arrayOf(
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
    // CARGA TV REAL DESDE XTREAM
    // =========================================================

    private fun loadLiveData() {

        loadingLive = true
        liveError = ""

        invalidate()

        Thread {

            try {

                val categoriesJson =
                    httpGet(
                        session.serverUrl +
                                "/player_api.php" +
                                "?username=" +
                                URLEncoder.encode(
                                    session.username,
                                    "UTF-8"
                                ) +
                                "&password=" +
                                URLEncoder.encode(
                                    session.password,
                                    "UTF-8"
                                ) +
                                "&action=get_live_categories"
                    )

                val streamsJson =
                    httpGet(
                        session.serverUrl +
                                "/player_api.php" +
                                "?username=" +
                                URLEncoder.encode(
                                    session.username,
                                    "UTF-8"
                                ) +
                                "&password=" +
                                URLEncoder.encode(
                                    session.password,
                                    "UTF-8"
                                ) +
                                "&action=get_live_streams"
                    )

                val categoriesArray =
                    org.json.JSONArray(
                        categoriesJson
                    )

                val streamsArray =
                    org.json.JSONArray(
                        streamsJson
                    )

                val categoriesResult =
                    mutableListOf<LiveCategory>()

                for (
                    i in 0 until categoriesArray.length()
                ) {

                    val item =
                        categoriesArray.optJSONObject(i)
                            ?: continue

                    categoriesResult.add(
                        LiveCategory(
                            categoryId =
                                item.optString(
                                    "category_id"
                                ),
                            categoryName =
                                item.optString(
                                    "category_name",
                                    "Sin categoría"
                                )
                        )
                    )
                }

                val streamsResult =
                    mutableListOf<LiveChannel>()

                for (
                    i in 0 until streamsArray.length()
                ) {

                    val item =
                        streamsArray.optJSONObject(i)
                            ?: continue

                    val streamId =
                        item.optInt(
                            "stream_id",
                            -1
                        )

                    if (streamId <= 0) {
                        continue
                    }

                    val name =
                        item.optString(
                            "name",
                            "Canal"
                        )

                    val icon =
                        item.optString(
                            "stream_icon",
                            ""
                        )

                    val categoryId =
                        item.optString(
                            "category_id",
                            ""
                        )

                    val streamType =
                        item.optString(
                            "stream_type",
                            "live"
                        )

                    val extension =
                        item.optString(
                            "container_extension",
                            "ts"
                        )

                    streamsResult.add(
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

                post {

                    liveCategories.clear()
                    liveCategories.addAll(
                        categoriesResult
                    )

                    liveChannels.clear()
                    liveChannels.addAll(
                        streamsResult
                    )

                    channels.clear()

                    channels.addAll(
                        liveChannels.map {
                            it.name
                        }
                    )

                    loadingLive = false
                    liveError = ""

                    selectedCard = 0
                    tvScroll = 0f

                    invalidate()
                }

            } catch (e: Exception) {

                post {

                    loadingLive = false

                    liveError =
                        e.message
                            ?: "No se pudo cargar TV"

                    channels.clear()

                    invalidate()
                }
            }

        }.start()
    }

    private fun httpGet(
        requestUrl: String
    ): String {

        var connection:
                HttpURLConnection? = null

        try {

            val url =
                URL(requestUrl)

            connection =
                url.openConnection()
                        as HttpURLConnection

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

            val responseCode =
                connection.responseCode

            if (
                responseCode !in 200..299
            ) {

                throw Exception(
                    "HTTP $responseCode"
                )
            }

            return connection
                .inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }

        } finally {

            connection?.disconnect()
        }
    }

    // =========================================================
    // RESPONSIVE
    // =========================================================

    private val sidebarWidth: Float
        get() =
            min(
                width * 0.19f,
                310f
            )

    private val horizontalMargin: Float
        get() =
            max(
                22f,
                width * 0.025f
            )

    private val contentLeft: Float
        get() =
            sidebarWidth +
                    horizontalMargin

    private val contentRight: Float
        get() =
            width -
                    horizontalMargin

    private val contentWidth: Float
        get() =
            contentRight -
                    contentLeft

    private val scale: Float
        get() =
            min(
                width / 1920f,
                height / 1080f
            ).coerceAtLeast(
                0.70f
            )

    // =========================================================
    // DRAW
    // =========================================================

    override fun onDraw(
        canvas: Canvas
    ) {

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
    // BACKGROUND
    // =========================================================

    private fun drawBackground(
        canvas: Canvas
    ) {

        paint.style =
            Paint.Style.FILL

        paint.color =
            Color.rgb(
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

        paint.color =
            Color.rgb(
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

        paint.color =
            Color.rgb(
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

    private fun drawSidebar(
        canvas: Canvas
    ) {

        paint.style =
            Paint.Style.FILL

        paint.color =
            Color.rgb(
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

        paint.color =
            Color.rgb(
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

        paint.color =
            Color.WHITE

        paint.textSize =
            27f * scale

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "▶ TVBOX",
            sidebarWidth * 0.12f,
            55f * scale,
            paint
        )

        paint.color =
            Color.rgb(
                55,
                165,
                255
            )

        paint.textSize =
            12f * scale

        paint.isFakeBoldText =
            false

        canvas.drawText(
            "PREMIUM",
            sidebarWidth * 0.31f,
            77f * scale,
            paint
        )

        paint.color =
            Color.rgb(
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

        val startY =
            155f * scale

        val spacing =
            64f * scale

        sections.forEachIndexed {
                index,
                title ->

            val y =
                startY +
                        index *
                        spacing

            val focused =
                focusZone == 3 &&
                        selectedSection == index

            if (focused) {

                paint.color =
                    Color.rgb(
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

                paint.color =
                    Color.rgb(
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

            paint.isFakeBoldText =
                focused

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

    private fun drawHeader(
        canvas: Canvas
    ) {

        paint.color =
            Color.rgb(
                190,
                205,
                220
            )

        paint.textSize =
            14f * scale

        paint.isFakeBoldText =
            false

        canvas.drawText(
            "TVBOX PREMIUM",
            contentLeft,
            42f * scale,
            paint
        )

        paint.color =
            Color.rgb(
                130,
                150,
                170
            )

        paint.textSize =
            15f * scale

        canvas.drawText(
            "⌕  Buscar",
            contentRight - 150f * scale,
            42f * scale,
            paint
        )

        paint.color =
            Color.rgb(
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

    private fun drawHero(
        canvas: Canvas
    ) {

        val left =
            contentLeft

        val top =
            65f * scale

        val right =
            contentRight

        val heroHeight =
            min(
                285f * scale,
                height * 0.31f
            )

        val bottom =
            top + heroHeight

        val heroFocused =
            focusZone == 0

        // -----------------------------------------------------
        // FONDO
        // -----------------------------------------------------

        paint.style =
            Paint.Style.FILL

        paint.color =
            Color.rgb(
                5,
                18,
                32
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

        // -----------------------------------------------------
        // VISUAL DERECHA
        // -----------------------------------------------------

        paint.color =
            Color.rgb(
                5,
                38,
                65
            )

        val visualPath =
            Path()

        visualPath.moveTo(
            left +
                    (right - left) *
                    0.48f,
            top
        )

        visualPath.lineTo(
            right,
            top
        )

        visualPath.lineTo(
            right,
            bottom
        )

        visualPath.lineTo(
            left +
                    (right - left) *
                    0.38f,
            bottom
        )

        visualPath.close()

        canvas.drawPath(
            visualPath,
            paint
        )

        // -----------------------------------------------------
        // ELEMENTOS CINEMATOGRÁFICOS
        // -----------------------------------------------------

        paint.color =
            Color.rgb(
                7,
                60,
                98
            )

        canvas.drawCircle(
            right - 230f * scale,
            top + heroHeight * 0.48f,
            135f * scale,
            paint
        )

        paint.color =
            Color.rgb(
                10,
                85,
                135
            )

        canvas.drawCircle(
            right - 230f * scale,
            top + heroHeight * 0.48f,
            90f * scale,
            paint
        )

        paint.color =
            Color.rgb(
                18,
                110,
                175
            )

        canvas.drawCircle(
            right - 230f * scale,
            top + heroHeight * 0.48f,
            45f * scale,
            paint
        )

        // -----------------------------------------------------
        // PLAY
        // -----------------------------------------------------

        paint.color =
            Color.argb(
                65,
                255,
                255,
                255
            )

        canvas.drawCircle(
            right - 230f * scale,
            top + heroHeight * 0.48f,
            78f * scale,
            paint
        )

        paint.color =
            Color.WHITE

        val play =
            Path()

        val playX =
            right - 230f * scale

        val playY =
            top + heroHeight * 0.48f

        play.moveTo(
            playX - 20f * scale,
            playY - 31f * scale
        )

        play.lineTo(
            playX + 35f * scale,
            playY
        )

        play.lineTo(
            playX - 20f * scale,
            playY + 31f * scale
        )

        play.close()

        canvas.drawPath(
            play,
            paint
        )

        // -----------------------------------------------------
        // DEGRADADO
        // -----------------------------------------------------

        val gradientWidth =
            (right - left) * 0.65f

        val gradientSteps =
            16

        for (
            i in 0 until gradientSteps
        ) {

            val progress =
                i.toFloat() /
                        gradientSteps.toFloat()

            val alpha =
                (
                    175f *
                            (1f - progress)
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        175
                    )

            paint.color =
                Color.argb(
                    alpha,
                    2,
                    9,
                    18
                )

            val sectionLeft =
                left +
                        gradientWidth *
                        progress

            val sectionRight =
                left +
                        gradientWidth *
                        (
                            (i + 1).toFloat() /
                                    gradientSteps.toFloat()
                            )

            canvas.drawRect(
                sectionLeft,
                top,
                sectionRight,
                bottom,
                paint
            )
        }

        // -----------------------------------------------------
        // FOCO
        // -----------------------------------------------------

        if (heroFocused) {

            paint.style =
                Paint.Style.STROKE

            paint.strokeWidth =
                3f * scale

            paint.color =
                Color.argb(
                    235,
                    255,
                    255,
                    255
                )

            canvas.drawRoundRect(
                RectF(
                    left - 2f * scale,
                    top - 2f * scale,
                    right + 2f * scale,
                    bottom + 2f * scale
                ),
                23f * scale,
                23f * scale,
                paint
            )

            paint.style =
                Paint.Style.FILL
        }

        // -----------------------------------------------------
        // TEXTO
        // -----------------------------------------------------

        paint.color =
            Color.rgb(
                75,
                180,
                255
            )

        paint.textSize =
            14f * scale

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "CONTENIDO DESTACADO",
            left + 36f * scale,
            top + 42f * scale,
            paint
        )

        paint.color =
            Color.WHITE

        paint.textSize =
            40f * scale

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "TVBOX PREMIUM",
            left + 36f * scale,
            top + 100f * scale,
            paint
        )

        paint.color =
            Color.rgb(
                205,
                220,
                235
            )

        paint.textSize =
            18f * scale

        paint.isFakeBoldText =
            false

        canvas.drawText(
            "Tu entretenimiento en un solo lugar.",
            left + 36f * scale,
            top + 140f * scale,
            paint
        )

        // -----------------------------------------------------
        // BOTÓN
        // -----------------------------------------------------

        val buttonLeft =
            left + 36f * scale

        val buttonTop =
            top + 175f * scale

        val buttonRight =
            buttonLeft +
                    168f * scale

        val buttonBottom =
            buttonTop +
                    52f * scale

        paint.color =
            if (heroFocused)
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

        paint.color =
            Color.WHITE

        paint.textSize =
            16f * scale

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "▶  VER AHORA",
            buttonLeft + 20f * scale,
            buttonTop + 33f * scale,
            paint
        )
    }

    // =========================================================
    // TV
    // =========================================================

    private fun drawLiveSection(
        canvas: Canvas
    ) {

        val heroBottom =
            65f * scale +
                    min(
                        285f * scale,
                        height * 0.31f
                    )

        val titleY =
            heroBottom +
                    43f * scale

        paint.color =
            Color.WHITE

        paint.textSize =
            23f * scale

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "TV EN VIVO",
            contentLeft,
            titleY,
            paint
        )

        paint.color =
            Color.rgb(
                110,
                140,
                165
            )

        paint.textSize =
            12f * scale

        paint.isFakeBoldText =
            false

        if (loadingLive) {

            paint.color =
                Color.rgb(
                    110,
                    140,
                    165
                )

            paint.textSize =
                13f * scale

            canvas.drawText(
                "Cargando canales reales...",
                contentLeft + 135f * scale,
                titleY,
                paint
            )

            return
        }

        if (liveError.isNotEmpty()) {

            paint.color =
                Color.rgb(
                    255,
                    105,
                    105
                )

            paint.textSize =
                13f * scale

            canvas.drawText(
                "Error TV: $liveError",
                contentLeft + 135f * scale,
                titleY,
                paint
            )

            return
        }

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
            finalWidth *
                    0.66f

        channels.forEachIndexed {
                index,
                channel ->

            val x =
                contentLeft +
                        index *
                        (
                            finalWidth +
                                    gap
                            ) -
                        tvScroll

            if (
                x + finalWidth <
                contentLeft ||
                x > contentRight
            ) {
                return@forEachIndexed
            }

            val focused =
                focusZone == 1 &&
                        selectedCard == index

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

            if (focused) {

                paint.style =
                    Paint.Style.STROKE

                paint.strokeWidth =
                    3f * scale

                paint.color =
                    Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f * scale,
                        top - 2f * scale,
                        x + finalWidth +
                                2f * scale,
                        top + cardHeight +
                                2f * scale
                    ),
                    16f * scale,
                    16f * scale,
                    paint
                )

                paint.style =
                    Paint.Style.FILL
            }

            paint.color =
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

            paint.color =
                Color.WHITE

            val miniPlay =
                Path()

            val cx =
                x +
                        finalWidth / 2f

            val cy =
                top +
                        cardHeight * 0.37f

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

            paint.color =
                Color.WHITE

            paint.textSize =
                14f * scale

            paint.isFakeBoldText =
                true

            val textWidth =
                paint.measureText(
                    channel
                )

            canvas.drawText(
                channel,
                x +
                        (
                            finalWidth -
                                    textWidth
                            ) / 2f,
                top +
                        cardHeight *
                        0.73f,
                paint
            )

            paint.color =
                Color.rgb(
                    125,
                    195,
                    255
                )

            paint.textSize =
                10f * scale

            paint.isFakeBoldText =
                false

            val live =
                "● EN VIVO"

            val liveWidth =
                paint.measureText(
                    live
                )

            canvas.drawText(
                live,
                x +
                        (
                            finalWidth -
                                    liveWidth
                            ) / 2f,
                top +
                        cardHeight *
                        0.89f,
                paint
            )
        }
    }

    // =========================================================
    // PELÍCULAS
    // =========================================================

    private fun drawMoviesSection(
        canvas: Canvas
    ) {

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
                (
                    contentWidth -
                            5f *
                            14f *
                            scale
                    ) / 6f
                ).coerceIn(
                    135f * scale,
                    235f * scale
                )

        val liveHeight =
            liveWidth *
                    0.66f

        val titleY =
            liveTop +
                    liveHeight +
                    48f * scale

        paint.color =
            Color.WHITE

        paint.textSize =
            23f * scale

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "PELÍCULAS POPULARES",
            contentLeft,
            titleY,
            paint
        )

        paint.color =
            Color.rgb(
                110,
                140,
                165
            )

        paint.textSize =
            12f * scale

        paint.isFakeBoldText =
            false

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
            finalWidth *
                    1.34f

        movies.forEachIndexed {
                index,
                movie ->

            val x =
                contentLeft +
                        index *
                        (
                            finalWidth +
                                    gap
                            ) -
                        movieScroll

            if (
                x + finalWidth <
                contentLeft ||
                x > contentRight
            ) {
                return@forEachIndexed
            }

            val focused =
                focusZone == 2 &&
                        selectedCard == index

            paint.color =
                if (focused)
                    Color.rgb(
                        20,
                        80,
                        135
                    )
                else
                    Color.rgb(
                        10 + index * 4,
                        26 + index * 4,
                        45 + index * 5
                    )

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
                    x + finalWidth -
                            7f * scale,
                    top +
                            cardHeight *
                            0.77f
                ),
                9f * scale,
                9f * scale,
                paint
            )

            if (focused) {

                paint.style =
                    Paint.Style.STROKE

                paint.strokeWidth =
                    3f * scale

                paint.color =
                    Color.WHITE

                canvas.drawRoundRect(
                    RectF(
                        x - 2f * scale,
                        top - 2f * scale,
                        x + finalWidth +
                                2f * scale,
                        top + cardHeight +
                                2f * scale
                    ),
                    13f * scale,
                    13f * scale,
                    paint
                )

                paint.style =
                    Paint.Style.FILL
            }

            paint.color =
                Color.WHITE

            paint.textSize =
                13f * scale

            paint.isFakeBoldText =
                true

            canvas.drawText(
                movie,
                x + 11f * scale,
                top +
                        cardHeight *
                        0.87f,
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

                touchStartX =
                    event.x

                touchStartY =
                    event.y

                return true
            }

            MotionEvent.ACTION_UP -> {

                val x =
                    event.x

                val y =
                    event.y

                val dx =
                    x - touchStartX

                val dy =
                    y - touchStartY

                if (
                    abs(dx) > 70f &&
                    abs(dx) > abs(dy)
                ) {

                    if (
                        focusZone == 1
                    ) {

                        tvScroll +=
                            if (dx < 0)
                                220f * scale
                            else
                                -220f * scale

                        tvScroll =
                            tvScroll.coerceIn(
                                0f,
                                max(0f, (channels.size * 220f * scale) - contentWidth)
                            )

                    } else if (
                        focusZone == 2
                    ) {

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

                handleTouch(
                    x,
                    y
                )

                performClick()

                return true
            }
        }

        return true
    }

    override fun performClick():
            Boolean {

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

        // -----------------------------------------------------
        // SIDEBAR
        // -----------------------------------------------------

        if (
            x <= sidebarWidth
        ) {

            val startY =
                120f * scale

            val spacing =
                64f * scale

            sections.forEachIndexed {
                    index,
                    _ ->

                val top =
                    startY +
                            index *
                            spacing

                val bottom =
                    top +
                            60f * scale

                if (
                    y >= top &&
                    y <= bottom
                ) {

                    selectedSection =
                        index

                    selectedCard =
                        0

                    focusZone =
                        3

                    tvScroll =
                        0f

                    movieScroll =
                        0f

                    invalidate()

                    return
                }
            }
        }

        // -----------------------------------------------------
        // HERO COMPLETO
        // -----------------------------------------------------

        val heroTop =
            65f * scale

        val heroHeight =
            min(
                285f * scale,
                height * 0.31f
            )

        val heroBottom =
            heroTop +
                    heroHeight

        if (
            x >= contentLeft &&
            x <= contentRight &&
            y >= heroTop &&
            y <= heroBottom
        ) {

            selectedSection =
                0

            selectedCard =
                0

            focusZone =
                0

            invalidate()

            return
        }

        // -----------------------------------------------------
        // VER AHORA
        // -----------------------------------------------------

        val buttonTop =
            heroTop +
                    165f * scale

        if (
            x >= contentLeft &&
            x <= contentLeft +
                    240f * scale &&
            y >= buttonTop &&
            y <= buttonTop +
                    70f * scale
        ) {

            selectedSection =
                1

            selectedCard =
                0

            focusZone =
                1

            invalidate()

            return
        }

        // -----------------------------------------------------
        // TV
        // -----------------------------------------------------

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
                (
                    contentWidth -
                            5f * gap
                    ) / 6f
                ).coerceIn(
                    135f * scale,
                    235f * scale
                )

        val liveHeight =
            liveWidth *
                    0.66f

        if (
            y >= liveTop &&
            y <= liveTop +
                    liveHeight
        ) {

            channels.forEachIndexed {
                    index,
                    _ ->

                val cardLeft =
                    contentLeft +
                            index *
                            (
                                liveWidth +
                                        gap
                                ) -
                            tvScroll

                if (
                    x >= cardLeft &&
                    x <= cardLeft +
                            liveWidth
                ) {

                    selectedSection =
                        1

                    selectedCard =
                        index

                    focusZone =
                        1

                    invalidate()

                    return
                }
            }
        }

        // -----------------------------------------------------
        // PELÍCULAS
        // -----------------------------------------------------

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
                (
                    contentWidth -
                            5f *
                            movieGap
                    ) / 6f
                ).coerceIn(
                    120f * scale,
                    195f * scale
                )

        val movieHeight =
            movieWidth *
                    1.34f

        if (
            y >= movieTop &&
            y <= movieTop +
                    movieHeight
        ) {

            movies.forEachIndexed {
                    index,
                    _ ->

                val cardLeft =
                    contentLeft +
                            index *
                            (
                                movieWidth +
                                        movieGap
                                ) -
                            movieScroll

                if (
                    x >= cardLeft &&
                    x <= cardLeft +
                            movieWidth
                ) {

                    selectedSection =
                        2

                    selectedCard =
                        index

                    focusZone =
                        2

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

                        focusZone =
                            1

                        selectedCard =
                            0
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

                        focusZone =
                            0
                    }
                }

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {

                when (focusZone) {

                    1 -> {

                        if (
                            selectedCard > 0
                        ) {

                            selectedCard--

                            keepTvCardVisible()

                        } else {

                            focusZone =
                                3
                        }
                    }

                    2 -> {

                        if (
                            selectedCard > 0
                        ) {

                            selectedCard--

                            keepMovieCardVisible()

                        } else {

                            focusZone =
                                3
                        }
                    }

                    else -> {

                        focusZone =
                            3
                    }
                }

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {

                when (focusZone) {

                    0 -> {

                        focusZone =
                            1

                        selectedCard =
                            0
                    }

                    1 -> {

                        focusZone =
                            2

                        selectedCard =
                            0
                    }

                    2 -> {

                        focusZone =
                            3

                        selectedSection =
                            0
                    }

                    3 -> {

                        selectedSection++

                        if (
                            selectedSection >
                            sections.lastIndex
                        ) {

                            selectedSection =
                                0
                        }
                    }
                }

                invalidate()

                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {

                when (focusZone) {

                    0 -> {

                        focusZone =
                            3
                    }

                    1 -> {

                        focusZone =
                            0
                    }

                    2 -> {

                        focusZone =
                            1

                        selectedCard =
                            0
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

                if (
                    focusZone != 3
                ) {

                    focusZone =
                        3

                    invalidate()

                    return true
                }

                selectedSection =
                    0

                selectedCard =
                    0

                focusZone =
                    0

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
                (
                    contentWidth -
                            5f * gap
                    ) / 6f
                ).coerceIn(
                    135f * scale,
                    235f * scale
                )

        val position =
            selectedCard *
                    (
                        cardWidth +
                                gap
                        )

        val visibleRight =
            contentWidth -
                    cardWidth

        if (
            position -
                    tvScroll >
            visibleRight
        ) {

            tvScroll =
                position -
                        visibleRight
        }

        if (
            position -
                    tvScroll <
            0f
        ) {

            tvScroll =
                position
        }

        tvScroll =
            tvScroll.coerceAtLeast(
                0f
            )
    }

    // =========================================================
    // VISIBILIDAD PELÍCULAS
    // =========================================================

    private fun keepMovieCardVisible() {

        val gap =
            16f * scale

        val cardWidth =
            (
                (
                    contentWidth -
                            5f * gap
                    ) / 6f
                ).coerceIn(
                    120f * scale,
                    195f * scale
                )

        val position =
            selectedCard *
                    (
                        cardWidth +
                                gap
                        )

        val visibleRight =
            contentWidth -
                    cardWidth

        if (
            position -
                    movieScroll >
            visibleRight
        ) {

            movieScroll =
                position -
                        visibleRight
        }

        if (
            position -
                    movieScroll <
            0f
        ) {

            movieScroll =
                position
        }

        movieScroll =
            movieScroll.coerceAtLeast(
                0f
            )
    }

    // =========================================================
    // ENTER
    // =========================================================

    private fun handleEnter() {

        when (focusZone) {

            0 -> {

                focusZone =
                    1

                selectedCard =
                    0
            }

            1 -> {

                // Próximo paso:
                // abrir Live TV real
            }

            2 -> {

                // Próximo paso:
                // abrir VOD real
            }

            3 -> {

                when (selectedSection) {

                    0 -> {

                        focusZone =
                            0
                    }

                    1 -> {

                        focusZone =
                            1

                        selectedCard =
                            0
                    }

                    2 -> {

                        focusZone =
                            2

                        selectedCard =
                            0
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
