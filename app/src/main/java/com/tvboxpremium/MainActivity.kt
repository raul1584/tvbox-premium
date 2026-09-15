package com.tvboxpremium

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.concurrent.thread

// =============================================================
// MODELOS
// =============================================================

data class XtreamSession(
    val serverUrl: String,
    val username: String,
    val password: String
)

data class LiveCategory(
    val id: String,
    val name: String
)

data class VodCategory(
    val id: String,
    val name: String
)

data class LiveChannel(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String?
)

data class Movie(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String?
)

// =============================================================
// API XTREAM
// =============================================================

object XtreamApi {

    fun request(
        session: XtreamSession,
        action: String
    ): String {

        val user =
            URLEncoder.encode(
                session.username,
                "UTF-8"
            )

        val password =
            URLEncoder.encode(
                session.password,
                "UTF-8"
            )

        val actionEncoded =
            URLEncoder.encode(
                action,
                "UTF-8"
            )

        val urlString =
            "${session.serverUrl}/player_api.php" +
                    "?username=$user" +
                    "&password=$password" +
                    "&action=$actionEncoded"

        var connection: HttpURLConnection? = null

        try {

            val url =
                URL(urlString)

            connection =
                url.openConnection()
                        as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                15000

            connection.readTimeout =
                20000

            connection.instanceFollowRedirects =
                true

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val code =
                connection.responseCode

            if (code !in 200..299) {

                throw Exception(
                    "HTTP $code"
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

    fun getLiveCategories(
        session: XtreamSession
    ): List<LiveCategory> {

        val response =
            request(
                session,
                "get_live_categories"
            )

        val array =
            JSONArray(response)

        val result =
            mutableListOf<LiveCategory>()

        for (i in 0 until array.length()) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            val id =
                item.optString(
                    "category_id"
                )

            val name =
                item.optString(
                    "category_name",
                    "Sin categoría"
                )

            if (id.isNotEmpty()) {

                result.add(
                    LiveCategory(
                        id,
                        name
                    )
                )
            }
        }

        return result
    }

    fun getLiveStreams(
        session: XtreamSession
    ): List<LiveChannel> {

        val response =
            request(
                session,
                "get_live_streams"
            )

        val array =
            JSONArray(response)

        val result =
            mutableListOf<LiveChannel>()

        for (i in 0 until array.length()) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            val id =
                item.optString(
                    "stream_id"
                )

            val name =
                item.optString(
                    "name",
                    "Canal"
                )

            val icon =
                item.optString(
                    "stream_icon",
                    ""
                ).ifBlank {
                    null
                }

            val category =
                item.optString(
                    "category_id",
                    ""
                ).ifBlank {
                    null
                }

            if (id.isNotEmpty()) {

                result.add(
                    LiveChannel(
                        id,
                        name,
                        icon,
                        category
                    )
                )
            }
        }

        return result
    }

    fun getVodCategories(
        session: XtreamSession
    ): List<VodCategory> {

        val response =
            request(
                session,
                "get_vod_categories"
            )

        val array =
            JSONArray(response)

        val result =
            mutableListOf<VodCategory>()

        for (i in 0 until array.length()) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            val id =
                item.optString(
                    "category_id"
                )

            val name =
                item.optString(
                    "category_name",
                    "Sin categoría"
                )

            if (id.isNotEmpty()) {

                result.add(
                    VodCategory(
                        id,
                        name
                    )
                )
            }
        }

        return result
    }

    fun getVodStreams(
        session: XtreamSession
    ): List<Movie> {

        val response =
            request(
                session,
                "get_vod_streams"
            )

        val array =
            JSONArray(response)

        val result =
            mutableListOf<Movie>()

        for (i in 0 until array.length()) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            val id =
                item.optString(
                    "stream_id"
                )

            val name =
                item.optString(
                    "name",
                    "Película"
                )

            val icon =
                item.optString(
                    "stream_icon",
                    ""
                ).ifBlank {
                    null
                }

            val category =
                item.optString(
                    "category_id",
                    ""
                ).ifBlank {
                    null
                }

            if (id.isNotEmpty()) {

                result.add(
                    Movie(
                        id,
                        name,
                        icon,
                        category
                    )
                )
            }
        }

        return result
    }
}

// =============================================================
// IMAGE CACHE
// =============================================================

object ImageCache {

    private val cache =
        object :
            LinkedHashMap<String, Bitmap>(
                40,
                0.75f,
                true
            ) {

            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Bitmap>?
            ): Boolean {

                return size > 40
            }
        }

    @Synchronized
    fun get(
        url: String
    ): Bitmap? {

        return cache[url]
    }

    @Synchronized
    fun put(
        url: String,
        bitmap: Bitmap
    ) {

        cache[url] = bitmap
    }

    fun load(
        url: String,
        callback: (Bitmap?) -> Unit
    ) {

        val cached =
            get(url)

        if (cached != null) {

            callback(cached)

            return
        }

        thread {

            var bitmap: Bitmap? = null

            try {

                val connection =
                    URL(url)
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    15000

                connection.instanceFollowRedirects =
                    true

                connection.connect()

                val input =
                    connection.inputStream

                val bytes =
                    input.readBytes()

                input.close()

                bitmap =
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size
                    )

                if (bitmap != null) {

                    put(
                        url,
                        bitmap
                    )
                }

                connection.disconnect()

            } catch (_: Exception) {
            }

            callback(bitmap)
        }
    }
}

// =============================================================
// MAIN ACTIVITY
// =============================================================

class MainActivity : Activity() {

    companion object {

        private const val SERVER_URL =
            "http://38.242.252.80:80"
    }

    private lateinit var root: FrameLayout

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        window.statusBarColor =
            Color.rgb(
                2,
                8,
                16
            )

        window.navigationBarColor =
            Color.rgb(
                2,
                8,
                16
            )

        root =
            FrameLayout(this)

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
                this,
                SERVER_URL
            ) {
                    username,
                    password ->

                authenticate(
                    username,
                    password
                )
            }
        )
    }

    // =========================================================
    // AUTENTICACIÓN
    // =========================================================

    private fun authenticate(
        username: String,
        password: String
    ) {

        thread {

            var connection:
                    HttpURLConnection? = null

            try {

                val user =
                    URLEncoder.encode(
                        username,
                        "UTF-8"
                    )

                val pass =
                    URLEncoder.encode(
                        password,
                        "UTF-8"
                    )

                val apiUrl =
                    "$SERVER_URL/player_api.php" +
                            "?username=$user" +
                            "&password=$pass"

                connection =
                    URL(apiUrl)
                        .openConnection()
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
                    responseCode !in 200..299
                ) {

                    runOnUiThread {

                        showLoginError(
                            "No se pudo conectar al servidor. HTTP $responseCode"
                        )
                    }

                    return@thread
                }

                val response =
                    connection
                        .inputStream
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

                    return@thread
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
                        true
                    )
                ) {

                    val session =
                        XtreamSession(
                            SERVER_URL,
                            username,
                            password
                        )

                    runOnUiThread {

                        hideKeyboard()

                        showHome(
                            session
                        )
                    }

                } else {

                    val status =
                        userInfo.optString(
                            "status",
                            ""
                        )

                    runOnUiThread {

                        showLoginError(
                            if (
                                status.isNotEmpty()
                            )
                                "Acceso rechazado: $status"
                            else
                                "Usuario o contraseña incorrectos."
                        )
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    showLoginError(
                        "Error de conexión: ${
                            e.message
                                ?: "servidor no disponible"
                        }"
                    )
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

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
                this,
                session
            )
        )
    }

    private fun hideKeyboard() {

        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            )
                    as InputMethodManager

        imm.hideSoftInputFromWindow(
            root.windowToken,
            0
        )
    }

    override fun onBackPressed() {

        if (
            root.childCount > 0 &&
            root.getChildAt(0)
                is HomeView
        ) {

            showLogin()

        } else {

            super.onBackPressed()
        }
    }
}

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
                MATCH_PARENT,
                MATCH_PARENT
            )
        )

        createLogin()
    }

    private fun createLogin() {

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

            errorText.text =
                ""

            onLogin(
                user,
                pass
            )
        }

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
// HOME VIEW
// =============================================================

class HomeView(
    context: Context,
    private val session: XtreamSession
) : View(context) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    // =========================================================
    // DATOS REALES
    // =========================================================

    private var liveCategories =
        emptyList<LiveCategory>()

    private var vodCategories =
        emptyList<VodCategory>()

    private var channels =
        emptyList<LiveChannel>()

    private var movies =
        emptyList<Movie>()

    private var loading =
        true

    private var loadingError:
            String? = null

    private var loadedImages =
        mutableMapOf<String, Bitmap>()

    // =========================================================
    // NAVEGACIÓN
    // =========================================================

    private var selectedSection =
        0

    /*
        0 = Hero
        1 = TV
        2 = Películas
        3 = menú lateral
    */

    private var focusZone =
        0

    private var selectedCard =
        0

    private var touchStartX =
        0f

    private var touchStartY =
        0f

    private var tvScroll =
        0f

    private var movieScroll =
        0f

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

    init {

        isFocusable =
            true

        isFocusableInTouchMode =
            true

        requestFocus()

        loadXtreamData()
    }

    // =========================================================
    // CARGA XTREAM
    // =========================================================

    private fun loadXtreamData() {

        loading = true

        loadingError = null

        invalidate()

        thread {

            try {

                val liveCats =
                    try {
                        XtreamApi
                            .getLiveCategories(
                                session
                            )
                    } catch (_: Exception) {
                        emptyList()
                    }

                val liveStreams =
                    XtreamApi
                        .getLiveStreams(
                            session
                        )

                val vodCats =
                    try {
                        XtreamApi
                            .getVodCategories(
                                session
                            )
                    } catch (_: Exception) {
                        emptyList()
                    }

                val vodStreams =
                    XtreamApi
                        .getVodStreams(
                            session
                        )

                runOnUiThread {

                    liveCategories =
                        liveCats

                    vodCategories =
                        vodCats

                    channels =
                        liveStreams

                    movies =
                        vodStreams

                    loading =
                        false

                    loadingError =
                        if (
                            channels.isEmpty() &&
                            movies.isEmpty()
                        )
                            "El servidor no devolvió canales ni películas."
                        else
                            null

                    loadVisibleImages()

                    invalidate()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    loading =
                        false

                    loadingError =
                        "Error cargando catálogo: ${
                            e.message
                                ?: "respuesta inválida"
                        }"

                    invalidate()
                }
            }
        }
    }

    private fun loadVisibleImages() {

        val imageUrls =
            mutableListOf<String>()

        channels
            .take(12)
            .forEach {

                it.icon?.let { url ->

                    if (
                        url.isNotBlank()
                    ) {

                        imageUrls.add(
                            url
                        )
                    }
                }
            }

        movies
            .take(12)
            .forEach {

                it.icon?.let { url ->

                    if (
                        url.isNotBlank()
                    ) {

                        imageUrls.add(
                            url
                        )
                    }
                }
            }

        imageUrls
            .distinct()
            .forEach { imageUrl ->

                if (
                    loadedImages[imageUrl]
                        == null
                ) {

                    ImageCache.load(
                        imageUrl
                    ) { bitmap ->

                        if (
                            bitmap != null
                        ) {

                            post {

                                loadedImages[
                                    imageUrl
                                ] = bitmap

                                invalidate()
                            }
                        }
                    }
                }
            }
    }

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

        if (loading) {

            drawLoading(canvas)
        }

        loadingError?.let {

            drawError(
                canvas,
                it
            )
        }
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

        val sections =
            arrayOf(
                "Inicio",
                "TV",
                "Películas",
                "Buscar",
                "Favoritos",
                "Configuración"
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
            13f * scale

        val info =
            if (loading)
                "Cargando catálogo..."
            else
                "${channels.size} canales  •  ${movies.size} películas"

        canvas.drawText(
            info,
            contentRight -
                    300f * scale,
            42f * scale,
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
            top +
                    heroHeight

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

        paint.color =
            Color.rgb(
                5,
                45,
                75
            )

        canvas.drawCircle(
            right - 220f * scale,
            top + heroHeight / 2f,
            125f * scale,
            paint
        )

        paint.color =
            Color.rgb(
                15,
                95,
                150
            )

        canvas.drawCircle(
            right - 220f * scale,
            top + heroHeight / 2f,
            75f * scale,
            paint
        )

        paint.color =
            Color.WHITE

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

        paint.textSize =
            40f * scale

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

        paint.color =
            Color.rgb(
                15,
                115,
                225
            )

        canvas.drawRoundRect(
            RectF(
                left + 36f * scale,
                top + 175f * scale,
                left + 204f * scale,
                top + 227f * scale
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
            left + 55f * scale,
            top + 208f * scale,
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

        canvas.drawText(
            if (
                liveCategories.isNotEmpty()
            )
                "${liveCategories.size} categorías"
            else
                "Canales disponibles",
            contentLeft +
                    135f * scale,
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

        channels
            .take(30)
            .forEachIndexed {
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

                channel.icon?.let {

                    val bitmap =
                        loadedImages[it]

                    if (
                        bitmap != null
                    ) {

                        drawBitmapCover(
                            canvas,
                            bitmap,
                            x + 6f * scale,
                            top + 6f * scale,
                            finalWidth -
                                    12f * scale,
                            cardHeight *
                                    0.65f
                        )
                    }
                }

                paint.color =
                    Color.WHITE

                paint.textSize =
                    13f * scale

                paint.isFakeBoldText =
                    true

                val displayName =
                    shorten(
                        channel.name,
                        20
                    )

                canvas.drawText(
                    displayName,
                    x + 10f * scale,
                    top +
                            cardHeight *
                            0.84f,
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

                canvas.drawText(
                    "● EN VIVO",
                    x + 10f * scale,
                    top +
                            cardHeight *
                            0.95f,
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
            if (
                vodCategories.isNotEmpty()
            )
                "${vodCategories.size} categorías"
            else
                "Recomendadas para ti",
            contentLeft +
                    245f * scale,
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

        movies
            .take(30)
            .forEachIndexed {
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
                    Color.rgb(
                        9,
                        27,
                        45
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

                movie.icon?.let {

                    val bitmap =
                        loadedImages[it]

                    if (
                        bitmap != null
                    ) {

                        drawBitmapCover(
                            canvas,
                            bitmap,
                            x + 5f * scale,
                            top + 5f * scale,
                            finalWidth -
                                    10f * scale,
                            cardHeight *
                                    0.78f
                        )
                    }
                }

                if (
                    focused
                ) {

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
                    shorten(
                        movie.name,
                        20
                    ),
                    x + 10f * scale,
                    top +
                            cardHeight *
                            0.88f,
                    paint
                )
            }
    }

    // =========================================================
    // LOADING
    // =========================================================

    private fun drawLoading(
        canvas: Canvas
    ) {

        paint.color =
            Color.argb(
                220,
                2,
                9,
                18
            )

        canvas.drawRect(
            contentLeft,
            65f * scale,
            contentRight,
            height.toFloat(),
            paint
        )

        paint.color =
            Color.WHITE

        paint.textAlign =
            Paint.Align.CENTER

        paint.textSize =
            22f * scale

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "CARGANDO CATÁLOGO...",
            (contentLeft +
                    contentRight) / 2f,
            height * 0.52f,
            paint
        )

        paint.color =
            Color.rgb(
                100,
                170,
                230
            )

        paint.textSize =
            13f * scale

        paint.isFakeBoldText =
            false

        canvas.drawText(
            "Conectando con el servidor",
            (contentLeft +
                    contentRight) / 2f,
            height * 0.57f,
            paint
        )

        paint.textAlign =
            Paint.Align.LEFT
    }

    // =========================================================
    // ERROR
    // =========================================================

    private fun drawError(
        canvas: Canvas,
        message: String
    ) {

        paint.color =
            Color.rgb(
                255,
                110,
                110
            )

        paint.textSize =
            13f * scale

        paint.textAlign =
            Paint.Align.CENTER

        canvas.drawText(
            message,
            (contentLeft +
                    contentRight) / 2f,
            height * 0.90f,
            paint
        )

        paint.textAlign =
            Paint.Align.LEFT
    }

    // =========================================================
    // BITMAP COVER
    // =========================================================

    private fun drawBitmapCover(
        canvas: Canvas,
        bitmap: Bitmap,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        val sourceRatio =
            bitmap.width.toFloat() /
                    bitmap.height.toFloat()

        val targetRatio =
            w / h

        var srcWidth =
            bitmap.width

        var srcHeight =
            bitmap.height

        var srcX =
            0

        var srcY =
            0

        if (
            sourceRatio >
            targetRatio
        ) {

            srcWidth =
                (
                    bitmap.height *
                            targetRatio
                    ).toInt()

            srcX =
                (
                    bitmap.width -
                            srcWidth
                    ) / 2

        } else {

            srcHeight =
                (
                    bitmap.width /
                            targetRatio
                    ).toInt()

            srcY =
                (
                    bitmap.height -
                            srcHeight
                    ) / 2
        }

        val src =
            Rect(
                srcX,
                srcY,
                srcX + srcWidth,
                srcY + srcHeight
            )

        val dst =
            RectF(
                x,
                y,
                x + w,
                y + h
            )

        canvas.drawBitmap(
            bitmap,
            src,
            dst,
            paint
        )
    }

    // =========================================================
    // TOUCH
    // =========================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (
            event.action
        ) {

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
                            tvScroll.coerceAtLeast(
                                0f
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
                            movieScroll.coerceAtLeast(
                                0f
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

        if (
            x <= sidebarWidth
        ) {

            val startY =
                120f * scale

            val spacing =
                64f * scale

            for (
                index in 0..5
            ) {

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

                    focusZone =
                        3

                    selectedCard =
                        0

                    invalidate()

                    return
                }
            }
        }

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

        // =====================================================
        // BOTÓN ANTES DEL HERO
        // =====================================================

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

        // =====================================================
        // HERO
        // =====================================================

        if (
            x >= contentLeft &&
            x <= contentRight &&
            y >= heroTop &&
            y <= heroBottom
        ) {

            selectedSection =
                0

            focusZone =
                0

            invalidate()

            return
        }

        // =====================================================
        // TV
        // =====================================================

        val liveTitle =
            heroBottom +
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

            channels
                .take(30)
                .forEachIndexed {
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

        // =====================================================
        // PELÍCULAS
        // =====================================================

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

            movies
                .take(30)
                .forEachIndexed {
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

        when (
            keyCode
        ) {

            KeyEvent.KEYCODE_DPAD_RIGHT -> {

                when (
                    focusZone
                ) {

                    0 -> {

                        focusZone =
                            1

                        selectedCard =
                            0
                    }

                    1 -> {

                        if (
                            selectedCard <
                            min(
                                channels.lastIndex,
                                29
                            )
                        ) {

                            selectedCard++

                            keepTvCardVisible()
                        }
                    }

                    2 -> {

                        if (
                            selectedCard <
                            min(
                                movies.lastIndex,
                                29
                            )
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

                when (
                    focusZone
                ) {

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

                when (
                    focusZone
                ) {

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
                            selectedSection > 5
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

                when (
                    focusZone
                ) {

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
                                5
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

                focusZone =
                    3

                selectedSection =
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
    // VISIBILIDAD MOVIES
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

        when (
            focusZone
        ) {

            0 -> {

                focusZone =
                    1

                selectedCard =
                    0
            }

            1 -> {

                // Próximo paso:
                // abrir reproductor Live real
            }

            2 -> {

                // Próximo paso:
                // abrir detalle VOD
            }

            3 -> {

                when (
                    selectedSection
                ) {

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
                }
            }
        }

        invalidate()
    }

    // =========================================================
    // UTILIDADES
    // =========================================================

    private fun shorten(
        text: String,
        maxLength: Int
    ): String {

        return if (
            text.length <= maxLength
        ) {

            text

        } else {

            text.take(
                maxLength - 1
            ) + "…"
        }
    }
}
