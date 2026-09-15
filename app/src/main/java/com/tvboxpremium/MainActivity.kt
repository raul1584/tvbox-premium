package com.tvboxpremium

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val SERVER_URL = "http://38.242.252.80:80"
    }

    private val executor = Executors.newFixedThreadPool(4)

    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var loading: ProgressBar

    /*
     * Las credenciales solamente viven en memoria mientras
     * la aplicación está abierta.
     */
    private var username = ""
    private var password = ""

    private var currentSection = "home"

    private var liveCategories = mutableListOf<LiveCategory>()
    private var vodCategories = mutableListOf<VodCategory>()

    private var liveStreams = mutableListOf<LiveStream>()
    private var vodStreams = mutableListOf<VodStream>()

    private var selectedLiveCategoryId: String? = null
    private var selectedVodCategoryId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showLogin()
    }

    // ============================================================
    // LOGIN
    // ============================================================

    private fun showLogin() {

        root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(3, 12, 25))

        setContentView(root)

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.setPadding(
            dp(35),
            dp(25),
            dp(35),
            dp(25)
        )

        root.addView(
            container,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val title = TextView(this)

        title.text = "TVBOX PREMIUM"
        title.textSize = 30f
        title.setTextColor(Color.WHITE)
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        title.gravity = Gravity.CENTER

        container.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        val subtitle = TextView(this)

        subtitle.text = "Inicia sesión para cargar tu contenido"
        subtitle.textSize = 15f
        subtitle.setTextColor(Color.rgb(160, 180, 200))
        subtitle.gravity = Gravity.CENTER

        container.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(45)
            )
        )

        // --------------------------------------------------------
        // Usuario
        // --------------------------------------------------------

        val userInput = EditText(this)

        userInput.hint = "Usuario"
        userInput.setTextColor(Color.WHITE)
        userInput.setHintTextColor(Color.rgb(130, 145, 160))
        userInput.setSingleLine(true)

        userInput.setPadding(
            dp(18),
            0,
            dp(18),
            0
        )

        userInput.setBackgroundColor(
            Color.rgb(15, 30, 48)
        )

        val userParams = LinearLayout.LayoutParams(
            dp(380),
            dp(55)
        )

        userParams.setMargins(
            0,
            dp(18),
            0,
            dp(10)
        )

        container.addView(
            userInput,
            userParams
        )

        // --------------------------------------------------------
        // Contraseña
        // --------------------------------------------------------

        val passInput = EditText(this)

        passInput.hint = "Contraseña"
        passInput.setTextColor(Color.WHITE)
        passInput.setHintTextColor(Color.rgb(130, 145, 160))
        passInput.setSingleLine(true)

        passInput.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD

        passInput.setPadding(
            dp(18),
            0,
            dp(18),
            0
        )

        passInput.setBackgroundColor(
            Color.rgb(15, 30, 48)
        )

        val passParams = LinearLayout.LayoutParams(
            dp(380),
            dp(55)
        )

        passParams.setMargins(
            0,
            0,
            0,
            dp(18)
        )

        container.addView(
            passInput,
            passParams
        )

        // --------------------------------------------------------
        // Botón login
        // --------------------------------------------------------

        val loginButton = Button(this)

        loginButton.text = "INICIAR SESIÓN"
        loginButton.textSize = 15f
        loginButton.setTextColor(Color.WHITE)
        loginButton.setBackgroundColor(
            Color.rgb(22, 133, 245)
        )

        val loginParams = LinearLayout.LayoutParams(
            dp(380),
            dp(55)
        )

        container.addView(
            loginButton,
            loginParams
        )

        // --------------------------------------------------------
        // Estado
        // --------------------------------------------------------

        val status = TextView(this)

        status.textSize = 14f
        status.gravity = Gravity.CENTER
        status.setTextColor(
            Color.rgb(150, 170, 190)
        )

        container.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        // --------------------------------------------------------
        // Acción login
        // --------------------------------------------------------

        loginButton.setOnClickListener {

            val user = userInput.text.toString().trim()
            val pass = passInput.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {

                status.text =
                    "Introduce usuario y contraseña"

                return@setOnClickListener
            }

            loginButton.isEnabled = false

            status.text =
                "Conectando con el servidor..."

            executor.execute {

                try {

                    val params = mapOf(
                        "username" to user,
                        "password" to pass
                    )

                    val json = apiRequest(params)

                    val userInfo =
                        json.optJSONObject("user_info")

                    val auth =
                        userInfo?.optString(
                            "auth",
                            "0"
                        )

                    if (
                        auth == "1" ||
                        auth.equals("true", true)
                    ) {

                        username = user
                        password = pass

                        runOnUiThread {

                            status.text =
                                "Login correcto"

                            showHome()
                        }

                    } else {

                        runOnUiThread {

                            loginButton.isEnabled = true

                            status.text =
                                "Usuario o contraseña incorrectos"
                        }
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        loginButton.isEnabled = true

                        status.text =
                            "Error de conexión: " +
                                    (e.message
                                        ?: "desconocido")
                    }
                }
            }
        }
    }

    // ============================================================
    // HOME
    // ============================================================

    private fun showHome() {

        root.removeAllViews()

        val main = LinearLayout(this)

        main.orientation =
            LinearLayout.VERTICAL

        main.setBackgroundColor(
            Color.rgb(3, 12, 25)
        )

        root.addView(
            main,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // --------------------------------------------------------
        // TOP BAR
        // --------------------------------------------------------

        val topBar = LinearLayout(this)

        topBar.orientation =
            LinearLayout.HORIZONTAL

        topBar.gravity =
            Gravity.CENTER_VERTICAL

        topBar.setPadding(
            dp(25),
            0,
            dp(25),
            0
        )

        topBar.setBackgroundColor(
            Color.rgb(5, 18, 34)
        )

        main.addView(
            topBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)
            )
        )

        val logo = TextView(this)

        logo.text = "TVBOX PREMIUM"
        logo.textSize = 21f
        logo.setTextColor(Color.WHITE)
        logo.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        topBar.addView(
            logo,
            LinearLayout.LayoutParams(
                dp(220),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val spacer = Space(this)

        topBar.addView(
            spacer,
            LinearLayout.LayoutParams(
                0,
                1,
                1f
            )
        )

        val tvButton =
            createTopButton("TV")

        topBar.addView(tvButton)

        val vodButton =
            createTopButton("PELÍCULAS")

        topBar.addView(vodButton)

        val logoutButton =
            createTopButton("SALIR")

        topBar.addView(logoutButton)

        tvButton.setOnClickListener {
            showLive()
        }

        vodButton.setOnClickListener {
            showVod()
        }

        logoutButton.setOnClickListener {

            username = ""
            password = ""

            liveCategories.clear()
            vodCategories.clear()
            liveStreams.clear()
            vodStreams.clear()

            selectedLiveCategoryId = null
            selectedVodCategoryId = null

            showLogin()
        }

        // --------------------------------------------------------
        // CONTENIDO SCROLL
        // --------------------------------------------------------

        content = LinearLayout(this)

        content.orientation =
            LinearLayout.VERTICAL

        content.setPadding(
            dp(25),
            dp(20),
            dp(25),
            dp(20)
        )

        val scroll = ScrollView(this)

        scroll.isFillViewport = true

        scroll.addView(
            content,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
        )

        main.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // --------------------------------------------------------
        // Loading global
        // --------------------------------------------------------

        loading = ProgressBar(this)

        loading.visibility = View.GONE

        root.addView(
            loading,
            FrameLayout.LayoutParams(
                dp(55),
                dp(55),
                Gravity.CENTER
            )
        )

        showDashboard()
    }

    // ============================================================
    // DASHBOARD
    // ============================================================

    private fun showDashboard() {

        currentSection = "home"

        content.removeAllViews()

        addHero()

        addSectionTitle(
            "TV EN VIVO",
            "Canales de televisión"
        )

        val tvButton =
            createLargeMenuButton(
                "VER TELEVISIÓN EN VIVO"
            )

        content.addView(tvButton)

        tvButton.setOnClickListener {
            showLive()
        }

        addSectionTitle(
            "PELÍCULAS",
            "Catálogo VOD"
        )

        val movieButton =
            createLargeMenuButton(
                "VER PELÍCULAS"
            )

        content.addView(movieButton)

        movieButton.setOnClickListener {
            showVod()
        }

        /*
         * Precargamos categorías.
         */
        loadLiveCategories()
        loadVodCategories()
    }

    // ============================================================
    // HERO
    // ============================================================

    private fun addHero() {

        val hero = LinearLayout(this)

        hero.orientation =
            LinearLayout.VERTICAL

        hero.gravity =
            Gravity.BOTTOM

        hero.setPadding(
            dp(30),
            dp(25),
            dp(30),
            dp(25)
        )

        hero.setBackgroundColor(
            Color.rgb(8, 30, 52)
        )

        content.addView(
            hero,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(230)
            )
        )

        val title = TextView(this)

        title.text = "TVBOX PREMIUM"
        title.textSize = 32f
        title.setTextColor(Color.WHITE)
        title.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        hero.addView(title)

        val subtitle = TextView(this)

        subtitle.text =
            "Tu entretenimiento en un solo lugar"

        subtitle.textSize = 16f

        subtitle.setTextColor(
            Color.rgb(190, 205, 220)
        )

        hero.addView(subtitle)

        val button =
            createBlueButton("EXPLORAR TV")

        val buttonParams =
            LinearLayout.LayoutParams(
                dp(180),
                dp(50)
            )

        buttonParams.setMargins(
            0,
            dp(18),
            0,
            0
        )

        hero.addView(
            button,
            buttonParams
        )

        button.setOnClickListener {
            showLive()
        }
    }

    // ============================================================
    // LIVE
    // ============================================================

    private fun showLive() {

        currentSection = "live"

        content.removeAllViews()

        addPageTitle(
            "TV EN VIVO",
            "Canales disponibles"
        )

        addLoading()

        if (liveCategories.isEmpty()) {

            loadLiveCategories()

        } else {

            renderLiveCategories()
        }
    }

    // ============================================================
    // LIVE CATEGORIES
    // ============================================================

    private fun loadLiveCategories() {

        executor.execute {

            try {

                val array =
                    apiRequestArray(
                        mapOf(
                            "action" to
                                    "get_live_categories"
                        )
                    )

                val result =
                    mutableListOf<LiveCategory>()

                for (i in 0 until array.length()) {

                    val item =
                        array.optJSONObject(i)
                            ?: continue

                    val categoryId =
                        item.optString(
                            "category_id"
                        )

                    val categoryName =
                        item.optString(
                            "category_name"
                        )

                    if (
                        categoryId.isNotEmpty() &&
                        categoryName.isNotEmpty()
                    ) {

                        result.add(
                            LiveCategory(
                                categoryId,
                                categoryName
                            )
                        )
                    }
                }

                liveCategories = result

                runOnUiThread {

                    if (
                        currentSection == "live"
                    ) {
                        renderLiveCategories()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    if (
                        currentSection == "live"
                    ) {

                        showError(
                            "No se pudieron cargar " +
                                    "las categorías TV:\n" +
                                    (e.message
                                        ?: "error desconocido")
                        )
                    }
                }
            }
        }
    }

    // ============================================================
    // RENDER LIVE CATEGORIES
    // ============================================================

    private fun renderLiveCategories() {

        content.removeAllViews()

        addPageTitle(
            "TV EN VIVO",
            "${liveCategories.size} categorías"
        )

        val categoriesScroll =
            HorizontalScrollView(this)

        val categories =
            LinearLayout(this)

        categories.orientation =
            LinearLayout.HORIZONTAL

        categoriesScroll.addView(categories)

        content.addView(
            categoriesScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(65)
            )
        )

        val allButton =
            createCategoryButton("TODOS")

        categories.addView(allButton)

        allButton.setOnClickListener {

            selectedLiveCategoryId = null

            loadLiveStreams(null)
        }

        for (category in liveCategories) {

            val button =
                createCategoryButton(
                    category.categoryName
                )

            categories.addView(button)

            button.setOnClickListener {

                selectedLiveCategoryId =
                    category.categoryId

                loadLiveStreams(
                    category.categoryId
                )
            }
        }

        addSectionTitle(
            "CANALES",
            "Selecciona una categoría"
        )

        if (liveStreams.isEmpty()) {

            val info = TextView(this)

            info.text =
                "Selecciona una categoría " +
                        "para cargar los canales."

            info.textSize = 15f

            info.setTextColor(
                Color.rgb(160, 180, 200)
            )

            content.addView(info)

        } else {

            renderLiveStreams()
        }
    }

    // ============================================================
    // LIVE STREAMS
    // ============================================================

    private fun loadLiveStreams(
        categoryId: String?
    ) {

        showLoading(true)

        executor.execute {

            try {

                val params =
                    mutableMapOf(
                        "action" to
                                "get_live_streams"
                    )

                if (
                    !categoryId.isNullOrEmpty()
                ) {
                    params["category_id"] =
                        categoryId
                }

                val array =
                    apiRequestArray(params)

                val result =
                    mutableListOf<LiveStream>()

                for (i in 0 until array.length()) {

                    val item =
                        array.optJSONObject(i)
                            ?: continue

                    result.add(
                        LiveStream(
                            streamId =
                                item.optString(
                                    "stream_id"
                                ),

                            name =
                                item.optString(
                                    "name"
                                ),

                            streamIcon =
                                item.optString(
                                    "stream_icon"
                                ),

                            categoryId =
                                item.optString(
                                    "category_id"
                                ),

                            streamType =
                                item.optString(
                                    "stream_type"
                                )
                        )
                    )
                }

                liveStreams = result

                runOnUiThread {

                    showLoading(false)

                    if (
                        currentSection == "live"
                    ) {
                        renderLiveStreams()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    showLoading(false)

                    showError(
                        "Error cargando canales:\n" +
                                (e.message
                                    ?: "desconocido")
                    )
                }
            }
        }
    }

    // ============================================================
    // RENDER LIVE STREAMS
    // ============================================================

    private fun renderLiveStreams() {

        renderLiveCategoryHeaderOnly()

        addSectionTitle(
            "CANALES",
            "${liveStreams.size} canales"
        )

        for (stream in liveStreams) {

            val card =
                createLiveCard(stream)

            val cardParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(76)
                )

            cardParams.setMargins(
                0,
                0,
                0,
                dp(8)
            )

            content.addView(
                card,
                cardParams
            )

            card.setOnClickListener {

                onLiveSelected(stream)
            }
        }
    }

    // ============================================================
    // LIVE CARD
    // ============================================================

    private fun createLiveCard(
        stream: LiveStream
    ): LinearLayout {

        val card = LinearLayout(this)

        card.orientation =
            LinearLayout.HORIZONTAL

        card.gravity =
            Gravity.CENTER_VERTICAL

        card.setPadding(
            dp(15),
            dp(8),
            dp(15),
            dp(8)
        )

        card.setBackgroundColor(
            Color.rgb(9, 25, 43)
        )

        val icon = ImageView(this)

        icon.scaleType =
            ImageView.ScaleType.CENTER_CROP

        icon.setBackgroundColor(
            Color.rgb(15, 30, 48)
        )

        card.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(95),
                dp(60)
            )
        )

        if (
            stream.streamIcon.isNotEmpty()
        ) {
            loadImage(
                stream.streamIcon,
                icon
            )
        }

        val info = LinearLayout(this)

        info.orientation =
            LinearLayout.VERTICAL

        info.gravity =
            Gravity.CENTER_VERTICAL

        info.setPadding(
            dp(18),
            0,
            0,
            0
        )

        val name = TextView(this)

        name.text = stream.name
        name.textSize = 17f
        name.setTextColor(Color.WHITE)
        name.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        info.addView(name)

        val live = TextView(this)

        live.text = "● EN VIVO"
        live.textSize = 12f

        live.setTextColor(
            Color.rgb(80, 190, 255)
        )

        info.addView(live)

        card.addView(
            info,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        return card
    }

    // ============================================================
    // VOD
    // ============================================================

    private fun showVod() {

        currentSection = "vod"

        content.removeAllViews()

        addPageTitle(
            "PELÍCULAS",
            "Catálogo VOD"
        )

        addLoading()

        if (vodCategories.isEmpty()) {

            loadVodCategories()

        } else {

            renderVodCategories()
        }
    }

    // ============================================================
    // VOD CATEGORIES
    // ============================================================

    private fun loadVodCategories() {

        executor.execute {

            try {

                val array =
                    apiRequestArray(
                        mapOf(
                            "action" to
                                    "get_vod_categories"
                        )
                    )

                val result =
                    mutableListOf<VodCategory>()

                for (i in 0 until array.length()) {

                    val item =
                        array.optJSONObject(i)
                            ?: continue

                    val categoryId =
                        item.optString(
                            "category_id"
                        )

                    val categoryName =
                        item.optString(
                            "category_name"
                        )

                    if (
                        categoryId.isNotEmpty() &&
                        categoryName.isNotEmpty()
                    ) {

                        result.add(
                            VodCategory(
                                categoryId,
                                categoryName
                            )
                        )
                    }
                }

                vodCategories = result

                runOnUiThread {

                    if (
                        currentSection == "vod"
                    ) {
                        renderVodCategories()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    if (
                        currentSection == "vod"
                    ) {

                        showError(
                            "No se pudieron cargar " +
                                    "las categorías VOD:\n" +
                                    (e.message
                                        ?: "error desconocido")
                        )
                    }
                }
            }
        }
    }

    // ============================================================
    // RENDER VOD CATEGORIES
    // ============================================================

    private fun renderVodCategories() {

        content.removeAllViews()

        addPageTitle(
            "PELÍCULAS",
            "${vodCategories.size} categorías"
        )

        val categoriesScroll =
            HorizontalScrollView(this)

        val categories =
            LinearLayout(this)

        categories.orientation =
            LinearLayout.HORIZONTAL

        categoriesScroll.addView(categories)

        content.addView(
            categoriesScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(65)
            )
        )

        val allButton =
            createCategoryButton("TODAS")

        categories.addView(allButton)

        allButton.setOnClickListener {

            selectedVodCategoryId = null

            loadVodStreams(null)
        }

        for (category in vodCategories) {

            val button =
                createCategoryButton(
                    category.categoryName
                )

            categories.addView(button)

            button.setOnClickListener {

                selectedVodCategoryId =
                    category.categoryId

                loadVodStreams(
                    category.categoryId
                )
            }
        }

        addSectionTitle(
            "PELÍCULAS",
            "Selecciona una categoría"
        )

        val info = TextView(this)

        info.text =
            "Selecciona una categoría " +
                    "para cargar las películas."

        info.textSize = 15f

        info.setTextColor(
            Color.rgb(160, 180, 200)
        )

        content.addView(info)
    }

    // ============================================================
    // VOD STREAMS
    // ============================================================

    private fun loadVodStreams(
        categoryId: String?
    ) {

        showLoading(true)

        executor.execute {

            try {

                val params =
                    mutableMapOf(
                        "action" to
                                "get_vod_streams"
                    )

                if (
                    !categoryId.isNullOrEmpty()
                ) {
                    params["category_id"] =
                        categoryId
                }

                val array =
                    apiRequestArray(params)

                val result =
                    mutableListOf<VodStream>()

                for (i in 0 until array.length()) {

                    val item =
                        array.optJSONObject(i)
                            ?: continue

                    result.add(
                        VodStream(
                            streamId =
                                item.optString(
                                    "stream_id"
                                ),

                            name =
                                item.optString(
                                    "name"
                                ),

                            streamIcon =
                                item.optString(
                                    "stream_icon"
                                ),

                            categoryId =
                                item.optString(
                                    "category_id"
                                ),

                            containerExtension =
                                item.optString(
                                    "container_extension",
                                    "mp4"
                                ),

                            rating =
                                item.optString(
                                    "rating"
                                ),

                            plot =
                                item.optString(
                                    "plot"
                                )
                        )
                    )
                }

                vodStreams = result

                runOnUiThread {

                    showLoading(false)

                    if (
                        currentSection == "vod"
                    ) {
                        renderVodStreams()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    showLoading(false)

                    showError(
                        "Error cargando películas:\n" +
                                (e.message
                                    ?: "desconocido")
                    )
                }
            }
        }
    }

    // ============================================================
    // RENDER VOD
    // ============================================================

    private fun renderVodStreams() {

        renderVodCategoryHeaderOnly()

        addSectionTitle(
            "PELÍCULAS",
            "${vodStreams.size} títulos"
        )

        val grid = LinearLayout(this)

        grid.orientation =
            LinearLayout.VERTICAL

        content.addView(grid)

        var row: LinearLayout? = null

        for (index in vodStreams.indices) {

            if (index % 3 == 0) {

                row = LinearLayout(this)

                row.orientation =
                    LinearLayout.HORIZONTAL

                val rowParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(260)
                    )

                rowParams.setMargins(
                    0,
                    0,
                    0,
                    dp(8)
                )

                grid.addView(
                    row,
                    rowParams
                )
            }

            val movie =
                vodStreams[index]

            val card =
                createVodCard(movie)

            val cardParams =
                LinearLayout.LayoutParams(
                    0,
                    dp(245),
                    1f
                )

            cardParams.setMargins(
                0,
                0,
                dp(8),
                0
            )

            row?.addView(
                card,
                cardParams
            )

            card.setOnClickListener {

                onVodSelected(movie)
            }
        }
    }

    // ============================================================
    // VOD CARD
    // ============================================================

    private fun createVodCard(
        movie: VodStream
    ): LinearLayout {

        val card = LinearLayout(this)

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            dp(6),
            dp(6),
            dp(6),
            dp(6)
        )

        card.setBackgroundColor(
            Color.rgb(9, 25, 43)
        )

        val poster = ImageView(this)

        poster.scaleType =
            ImageView.ScaleType.CENTER_CROP

        poster.setBackgroundColor(
            Color.rgb(15, 30, 48)
        )

        card.addView(
            poster,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(175)
            )
        )

        if (
            movie.streamIcon.isNotEmpty()
        ) {

            loadImage(
                movie.streamIcon,
                poster
            )
        }

        val title = TextView(this)

        title.text = movie.name
        title.textSize = 14f
        title.setTextColor(Color.WHITE)

        title.maxLines = 2

        title.ellipsize =
            TextUtils.TruncateAt.END

        title.setPadding(
            dp(5),
            dp(8),
            dp(5),
            0
        )

        card.addView(title)

        if (
            movie.rating.isNotEmpty()
        ) {

            val rating = TextView(this)

            rating.text =
                "★ ${movie.rating}"

            rating.textSize = 12f

            rating.setTextColor(
                Color.rgb(150, 190, 255)
            )

            rating.setPadding(
                dp(5),
                dp(3),
                0,
                0
            )

            card.addView(rating)
        }

        return card
    }

    // ============================================================
    // SELECT LIVE
    // ============================================================

    private fun onLiveSelected(
        stream: LiveStream
    ) {

        /*
         * Xtream normalmente usa .ts para live.
         * Si la API informa m3u8, usamos m3u8.
         *
         * OJO:
         * El servidor puede redirigir posteriormente
         * este endpoint hacia HLS. Eso lo resolveremos
         * en el reproductor.
         */

        val extension =
            when {

                stream.streamType.equals(
                    "m3u8",
                    true
                ) -> "m3u8"

                stream.streamType.equals(
                    "hls",
                    true
                ) -> "m3u8"

                else -> "ts"
            }

        val url =
            "$SERVER_URL/live/" +
                    "${encode(username)}/" +
                    "${encode(password)}/" +
                    "${stream.streamId}." +
                    extension

        /*
         * Por ahora no mostramos la URL ni
         * la escribimos en logs.
         */

        showStreamDialog(
            stream.name,
            url,
            false
        )
    }

    // ============================================================
    // SELECT VOD
    // ============================================================

    private fun onVodSelected(
        movie: VodStream
    ) {

        val extension =
            movie.containerExtension
                .ifEmpty {
                    "mp4"
                }

        val url =
            "$SERVER_URL/movie/" +
                    "${encode(username)}/" +
                    "${encode(password)}/" +
                    "${movie.streamId}." +
                    extension

        showStreamDialog(
            movie.name,
            url,
            true
        )
    }

    // ============================================================
    // STREAM DIALOG
    // ============================================================

    private fun showStreamDialog(
        title: String,
        url: String,
        isVod: Boolean
    ) {

        val box = LinearLayout(this)

        box.orientation =
            LinearLayout.VERTICAL

        box.setPadding(
            dp(30),
            dp(20),
            dp(30),
            dp(20)
        )

        val titleView = TextView(this)

        titleView.text = title
        titleView.textSize = 20f
        titleView.setTextColor(Color.WHITE)

        titleView.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        box.addView(titleView)

        val info = TextView(this)

        info.text =
            if (isVod) {
                "Película seleccionada.\n\n" +
                        "El siguiente paso será " +
                        "conectarla al reproductor."
            } else {
                "Canal seleccionado.\n\n" +
                        "El siguiente paso será " +
                        "conectarlo al reproductor."
            }

        info.textSize = 14f

        info.setTextColor(
            Color.rgb(170, 190, 210)
        )

        info.setPadding(
            0,
            dp(15),
            0,
            dp(15)
        )

        box.addView(info)

        val dialog =
            android.app.AlertDialog.Builder(this)
                .setView(box)
                .setPositiveButton(
                    "OK",
                    null
                )
                .create()

        dialog.setOnShowListener {

            /*
             * No mostramos la URL para evitar
             * exponer usuario/contraseña.
             */

            val button =
                dialog.getButton(
                    android.app.AlertDialog.BUTTON_POSITIVE
                )

            button.setTextColor(
                Color.rgb(22, 133, 245)
            )
        }

        dialog.show()
    }

    // ============================================================
    // API JSON OBJECT
    // ============================================================

    private fun apiRequest(
        params: Map<String, String>
    ): JSONObject {

        val url =
            buildApiUrl(params)

        val connection =
            URL(url).openConnection()
                    as HttpURLConnection

        connection.requestMethod = "GET"

        connection.connectTimeout =
            15000

        connection.readTimeout =
            20000

        connection.useCaches = false

        try {

            val code =
                connection.responseCode

            val input =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            if (input == null) {

                throw Exception(
                    "HTTP $code"
                )
            }

            val reader =
                BufferedReader(
                    InputStreamReader(
                        input,
                        Charsets.UTF_8
                    )
                )

            val response =
                reader.use {
                    it.readText()
                }

            if (code !in 200..299) {

                throw Exception(
                    "HTTP $code: $response"
                )
            }

            return JSONObject(response)

        } finally {

            connection.disconnect()
        }
    }

    // ============================================================
    // API JSON ARRAY
    // ============================================================

    private fun apiRequestArray(
        params: Map<String, String>
    ): JSONArray {

        val url =
            buildApiUrl(params)

        val connection =
            URL(url).openConnection()
                    as HttpURLConnection

        connection.requestMethod = "GET"

        connection.connectTimeout =
            15000

        connection.readTimeout =
            30000

        connection.useCaches = false

        try {

            val code =
                connection.responseCode

            val input =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            if (input == null) {

                throw Exception(
                    "HTTP $code"
                )
            }

            val reader =
                BufferedReader(
                    InputStreamReader(
                        input,
                        Charsets.UTF_8
                    )
                )

            val response =
                reader.use {
                    it.readText()
                }

            if (code !in 200..299) {

                throw Exception(
                    "HTTP $code: $response"
                )
            }

            return JSONArray(response)

        } finally {

            connection.disconnect()
        }
    }

    // ============================================================
    // BUILD API URL
    // ============================================================

    private fun buildApiUrl(
        params: Map<String, String>
    ): String {

        val query =
            StringBuilder()

        query.append(
            "username="
        )

        query.append(
            encode(username)
        )

        query.append(
            "&password="
        )

        query.append(
            encode(password)
        )

        for ((key, value) in params) {

            query.append("&")

            query.append(
                encode(key)
            )

            query.append("=")

            query.append(
                encode(value)
            )
        }

        return "$SERVER_URL/player_api.php?$query"
    }

    // ============================================================
    // IMAGE LOADER
    // ============================================================

    private fun loadImage(
        imageUrl: String,
        imageView: ImageView
    ) {

        executor.execute {

            try {

                val connection =
                    URL(imageUrl)
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    15000

                connection.useCaches = true

                val bitmap =
                    connection.inputStream.use {
                        BitmapFactory.decodeStream(it)
                    }

                connection.disconnect()

                if (bitmap != null) {

                    runOnUiThread {

                        if (
                            !isFinishing &&
                            !isDestroyed
                        ) {
                            imageView.setImageBitmap(
                                bitmap
                            )
                        }
                    }
                }

            } catch (_: Exception) {

                /*
                 * Dejamos el placeholder.
                 */
            }
        }
    }

    // ============================================================
    // TOP BUTTON
    // ============================================================

    private fun createTopButton(
        text: String
    ): Button {

        val button = Button(this)

        button.text = text
        button.textSize = 12f
        button.setTextColor(Color.WHITE)

        button.setBackgroundColor(
            Color.TRANSPARENT
        )

        button.setPadding(
            dp(12),
            0,
            dp(12),
            0
        )

        return button
    }

    // ============================================================
    // CATEGORY BUTTON
    // ============================================================

    private fun createCategoryButton(
        text: String
    ): Button {

        val button = Button(this)

        button.text = text
        button.textSize = 12f

        button.setTextColor(
            Color.WHITE
        )

        button.setBackgroundColor(
            Color.rgb(15, 45, 75)
        )

        val params =
            LinearLayout.LayoutParams(
                dp(150),
                dp(50)
            )

        params.setMargins(
            0,
            0,
            dp(8),
            0
        )

        button.layoutParams = params

        return button
    }

    // ============================================================
    // BLUE BUTTON
    // ============================================================

    private fun createBlueButton(
        text: String
    ): Button {

        val button = Button(this)

        button.text = text
        button.textSize = 13f

        button.setTextColor(
            Color.WHITE
        )

        button.setBackgroundColor(
            Color.rgb(22, 133, 245)
        )

        return button
    }

    // ============================================================
    // LARGE MENU BUTTON
    // ============================================================

    private fun createLargeMenuButton(
        text: String
    ): Button {

        val button =
            createBlueButton(text)

        val params =
            LinearLayout.LayoutParams(
                dp(260),
                dp(55)
            )

        params.setMargins(
            0,
            0,
            0,
            dp(15)
        )

        button.layoutParams = params

        return button
    }

    // ============================================================
    // PAGE TITLE
    // ============================================================

    private fun addPageTitle(
        title: String,
        subtitle: String
    ) {

        val titleView = TextView(this)

        titleView.text = title
        titleView.textSize = 27f

        titleView.setTextColor(
            Color.WHITE
        )

        titleView.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        content.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(45)
            )
        )

        val subtitleView = TextView(this)

        subtitleView.text = subtitle
        subtitleView.textSize = 14f

        subtitleView.setTextColor(
            Color.rgb(150, 175, 195)
        )

        content.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(35)
            )
        )
    }

    // ============================================================
    // SECTION TITLE
    // ============================================================

    private fun addSectionTitle(
        title: String,
        subtitle: String
    ) {

        val titleView = TextView(this)

        titleView.text = title
        titleView.textSize = 21f

        titleView.setTextColor(
            Color.WHITE
        )

        titleView.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )

        params.setMargins(
            0,
            dp(20),
            0,
            0
        )

        content.addView(
            titleView,
            params
        )

        val subtitleView = TextView(this)

        subtitleView.text = subtitle
        subtitleView.textSize = 13f

        subtitleView.setTextColor(
            Color.rgb(140, 165, 185)
        )

        content.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)
            )
        )
    }

    // ============================================================
    // LOADING
    // ============================================================

    private fun addLoading() {

        val progress =
            ProgressBar(this)

        progress.tag =
            "content_loading"

        content.addView(
            progress,
            LinearLayout.LayoutParams(
                dp(45),
                dp(45)
            )
        )
    }

    private fun showLoading(
        visible: Boolean
    ) {

        if (::loading.isInitialized) {

            loading.visibility =
                if (visible) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
    }

    // ============================================================
    // ERROR
    // ============================================================

    private fun showError(
        message: String
    ) {

        content.removeAllViews()

        val error = TextView(this)

        error.text = message
        error.textSize = 15f

        error.setTextColor(
            Color.rgb(255, 130, 130)
        )

        error.setPadding(
            dp(20),
            dp(30),
            dp(20),
            dp(30)
        )

        content.addView(
            error,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // ============================================================
    // LIVE CATEGORY HEADER
    // ============================================================

    private fun renderLiveCategoryHeaderOnly() {

        content.removeAllViews()

        addPageTitle(
            "TV EN VIVO",
            "${liveCategories.size} categorías"
        )

        val scroll =
            HorizontalScrollView(this)

        val row =
            LinearLayout(this)

        row.orientation =
            LinearLayout.HORIZONTAL

        scroll.addView(row)

        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(65)
            )
        )

        val all =
            createCategoryButton("TODOS")

        row.addView(all)

        all.setOnClickListener {

            selectedLiveCategoryId = null

            loadLiveStreams(null)
        }

        for (category in liveCategories) {

            val button =
                createCategoryButton(
                    category.categoryName
                )

            row.addView(button)

            button.setOnClickListener {

                selectedLiveCategoryId =
                    category.categoryId

                loadLiveStreams(
                    category.categoryId
                )
            }
        }
    }

    // ============================================================
    // VOD CATEGORY HEADER
    // ============================================================

    private fun renderVodCategoryHeaderOnly() {

        content.removeAllViews()

        addPageTitle(
            "PELÍCULAS",
            "${vodCategories.size} categorías"
        )

        val scroll =
            HorizontalScrollView(this)

        val row =
            LinearLayout(this)

        row.orientation =
            LinearLayout.HORIZONTAL

        scroll.addView(row)

        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(65)
            )
        )

        val all =
            createCategoryButton("TODAS")

        row.addView(all)

        all.setOnClickListener {

            selectedVodCategoryId = null

            loadVodStreams(null)
        }

        for (category in vodCategories) {

            val button =
                createCategoryButton(
                    category.categoryName
                )

            row.addView(button)

            button.setOnClickListener {

                selectedVodCategoryId =
                    category.categoryId

                loadVodStreams(
                    category.categoryId
                )
            }
        }
    }

    // ============================================================
    // DATA MODELS
    // ============================================================

    data class LiveCategory(
        val categoryId: String,
        val categoryName: String
    )

    data class VodCategory(
        val categoryId: String,
        val categoryName: String
    )

    data class LiveStream(
        val streamId: String,
        val name: String,
        val streamIcon: String,
        val categoryId: String,
        val streamType: String
    )

    data class VodStream(
        val streamId: String,
        val name: String,
        val streamIcon: String,
        val categoryId: String,
        val containerExtension: String,
        val rating: String,
        val plot: String
    )

    // ============================================================
    // URL ENCODING
    // ============================================================

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    // ============================================================
    // DP
    // ============================================================

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                    resources
                        .displayMetrics
                        .density
            ).toInt()
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        executor.shutdownNow()

        super.onDestroy()
    }
}
