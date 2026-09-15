package com.tvboxpremium

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        container.setPadding(dp(35), dp(25), dp(35), dp(25))

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

        val userInput = EditText(this)
        userInput.hint = "Usuario"
        userInput.setTextColor(Color.WHITE)
        userInput.setHintTextColor(Color.rgb(130, 145, 160))
        userInput.setSingleLine(true)
        userInput.setPadding(dp(18), 0, dp(18), 0)
        userInput.setBackgroundColor(Color.rgb(15, 30, 48))

        val userParams = LinearLayout.LayoutParams(
            dp(380),
            dp(55)
        )
        userParams.setMargins(0, dp(18), 0, dp(10))

        container.addView(userInput, userParams)

        val passInput = EditText(this)
        passInput.hint = "Contraseña"
        passInput.setTextColor(Color.WHITE)
        passInput.setHintTextColor(Color.rgb(130, 145, 160))
        passInput.setSingleLine(true)
        passInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        passInput.setPadding(dp(18), 0, dp(18), 0)
        passInput.setBackgroundColor(Color.rgb(15, 30, 48))

        val passParams = LinearLayout.LayoutParams(
            dp(380),
            dp(55)
        )
        passParams.setMargins(0, 0, 0, dp(18))

        container.addView(passInput, passParams)

        val loginButton = Button(this)
        loginButton.text = "INICIAR SESIÓN"
        loginButton.textSize = 15f
        loginButton.setTextColor(Color.WHITE)
        loginButton.setBackgroundColor(Color.rgb(22, 133, 245))

        val loginParams = LinearLayout.LayoutParams(
            dp(380),
            dp(55)
        )

        container.addView(loginButton, loginParams)

        val status = TextView(this)
        status.textSize = 14f
        status.gravity = Gravity.CENTER
        status.setTextColor(Color.rgb(150, 170, 190))

        val statusParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(55)
        )

        container.addView(status, statusParams)

        loginButton.setOnClickListener {

            val user = userInput.text.toString().trim()
            val pass = passInput.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                status.text = "Introduce usuario y contraseña"
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            status.text = "Conectando con el servidor..."

            executor.execute {

                try {

                    val params = mapOf(
                        "username" to user,
                        "password" to pass
                    )

                    val json = apiRequest(params)

                    val userInfo = json.optJSONObject("user_info")

                    val auth = userInfo?.optString("auth", "0")

                    if (auth == "1" || auth.equals("true", true)) {

                        username = user
                        password = pass

                        runOnUiThread {

                            status.text = "Login correcto"

                            showHome()

                        }

                    } else {

                        runOnUiThread {

                            loginButton.isEnabled = true
                            status.text = "Usuario o contraseña incorrectos"

                        }
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        loginButton.isEnabled = true
                        status.text =
                            "Error de conexión: ${e.message ?: "desconocido"}"

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
        main.orientation = LinearLayout.VERTICAL
        main.setBackgroundColor(Color.rgb(3, 12, 25))

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
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setPadding(dp(25), 0, dp(25), 0)
        topBar.setBackgroundColor(Color.rgb(5, 18, 34))

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
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD)

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

        val tvButton = createTopButton("TV")

        topBar.addView(tvButton)

        val vodButton = createTopButton("PELÍCULAS")

        topBar.addView(vodButton)

        val logoutButton = createTopButton("SALIR")

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

            showLogin()
        }

        // --------------------------------------------------------
        // CONTENT
        // --------------------------------------------------------

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(25), dp(20), dp(25), dp(20))

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

        val tvButton = createLargeMenuButton(
            "Ver televisión en vivo"
        )

        content.addView(tvButton)

        tvButton.setOnClickListener {
            showLive()
        }

        addSectionTitle(
            "PELÍCULAS",
            "Catálogo VOD"
        )

        val movieButton = createLargeMenuButton(
            "Ver películas"
        )

        content.addView(movieButton)

        movieButton.setOnClickListener {
            showVod()
        }

        loadLiveCategories()
        loadVodCategories()
    }

    // ============================================================
    // HERO
    // ============================================================

    private fun addHero() {

        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.BOTTOM
        hero.setPadding(
            dp(30),
            dp(25),
            dp(30),
            dp(25)
        )

        hero.setBackgroundColor(Color.rgb(8, 30, 52))

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
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD)

        hero.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Tu entretenimiento en un solo lugar"
        subtitle.textSize = 16f
        subtitle.setTextColor(Color.rgb(190, 205, 220))

        hero.addView(subtitle)

        val button = createBlueButton("EXPLORAR TV")

        val params = LinearLayout.LayoutParams(
            dp(180),
            dp(50)
        )

        params.setMargins(0, dp(18), 0, 0)

        hero.addView(button, params)

        button.setOnClickListener {
            showLive()
        }
    }

    // ============================================================
    // LIVE TV
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

    private fun loadLiveCategories() {

        executor.execute {

            try {

                val array =
                    apiRequestArray(
                        mapOf(
                            "action" to "get_live_categories"
                        )
                    )

                val result = mutableListOf<LiveCategory>()

                for (i in 0 until array.length()) {

                    val item = array.optJSONObject(i) ?: continue

                    result.add(
                        LiveCategory(
                            categoryId =
                                item.optString("category_id"),
                            categoryName =
                                item.optString("category_name")
                        )
                    )
                }

                liveCategories = result

                runOnUiThread {

                    if (currentSection == "live") {
                        renderLiveCategories()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    if (currentSection == "live") {
                        showError(
                            "No se pudieron cargar las categorías TV:\n${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun renderLiveCategories() {

        content.removeAllViews()

        addPageTitle(
            "TV EN VIVO",
            "${liveCategories.size} categorías"
        )

        val categoriesContainer = HorizontalScrollView(this)

        val categories =
            LinearLayout(this)

        categories.orientation =
            LinearLayout.HORIZONTAL

        categoriesContainer.addView(categories)

        content.addView(
            categoriesContainer,
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
                "Selecciona una categoría para cargar los canales."

            info.textSize = 15f
            info.setTextColor(
                Color.rgb(160, 180, 200)
            )

            content.addView(info)

        } else {

            renderLiveStreams()
        }
    }

    private fun loadLiveStreams(categoryId: String?) {

        showLoading(true)

        executor.execute {

            try {

                val params =
                    mutableMapOf(
                        "action" to "get_live_streams"
                    )

                if (!categoryId.isNullOrEmpty()) {

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

                    if (currentSection == "live") {
                        renderLiveStreams()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    showLoading(false)

                    showError(
                        "Error cargando canales:\n${e.message}"
                    )
                }
            }
        }
    }

    private fun renderLiveStreams() {

        removeContentAfterCategoryArea()

        addSectionTitle(
            "CANALES",
            "${liveStreams.size} canales"
        )

        for (stream in liveStreams) {

            val card =
                createLiveCard(stream)

            content.addView(card)

            card.setOnClickListener {

                onLiveSelected(stream)
            }
        }
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

        if (vodCategories.isEmpty()) {

            loadVodCategories()

        } else {

            renderVodCategories()
        }
    }

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

                    result.add(
                        VodCategory(
                            categoryId =
                                item.optString(
                                    "category_id"
                                ),
                            categoryName =
                                item.optString(
                                    "category_name"
                                )
                        )
                    )
                }

                vodCategories = result

                runOnUiThread {

                    if (currentSection == "vod") {
                        renderVodCategories()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    if (currentSection == "vod") {

                        showError(
                            "No se pudieron cargar las categorías VOD:\n${e.message}"
                        )
                    }
                }
            }
        }
    }

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

        val all =
            createCategoryButton("TODAS")

        categories.addView(all)

        all.setOnClickListener {

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
            "Selecciona una categoría para cargar las películas."

        info.textSize = 15f
        info.setTextColor(
            Color.rgb(160, 180, 200)
        )

        content.addView(info)
    }

    private fun loadVodStreams(categoryId: String?) {

        showLoading(true)

        executor.execute {

            try {

                val params =
                    mutableMapOf(
                        "action" to
                                "get_vod_streams"
                    )

                if (!categoryId.isNullOrEmpty()) {

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

                    if (currentSection == "vod") {
                        renderVodStreams()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    showLoading(false)

                    showError(
                        "Error cargando películas:\n${e.message}"
                    )
                }
            }
        }
    }

    private fun renderVodStreams() {

        removeContentAfterCategoryArea()

        addSectionTitle(
            "PELÍCULAS",
            "${vodStreams.size} títulos"
        )

        val grid =
            LinearLayout(this)

        grid.orientation =
            LinearLayout.VERTICAL

        content.addView(grid)

        var row: LinearLayout? = null

        for (index in vodStreams.indices) {

            if (index % 3 == 0) {

                row = LinearLayout(this)

                row.orientation =
                    LinearLayout.HORIZONTAL

                grid.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(260)
                    )
                )
            }

            val card =
                createVodCard(
                    vodStreams[index]
                )

            row?.addView(
                card,
                LinearLayout.LayoutParams(
                    0,
                    dp(245),
                    1f
                )
            )

            card.setOnClickListener {

                onVodSelected(
                    vodStreams[index]
                )
            }
        }
    }

    // ============================================================
    // LIVE CARD
    // ============================================================

    private fun createLiveCard(
        stream: LiveStream
    ): LinearLayout {

        val card =
            LinearLayout(this)

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

        val icon =
            ImageView(this)

        icon.scaleType =
            ImageView.ScaleType.CENTER_CROP

        card.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(95),
                dp(60)
            )
        )

        if (stream.streamIcon.isNotEmpty()) {

            loadImage(
                stream.streamIcon,
                icon
            )
        }

        val info =
            LinearLayout(this)

        info.orientation =
            LinearLayout.VERTICAL

        info.setPadding(
            dp(18),
            0,
            0,
            0
        )

        val name =
            TextView(this)

        name.text =
            stream.name

        name.textSize =
            17f

        name.setTextColor(
            Color.WHITE
        )

        name.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        info.addView(name)

        val live =
            TextView(this)

        live.text =
            "● EN VIVO"

        live.textSize =
            12f

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
    // VOD CARD
    // ============================================================

    private fun createVodCard(
        movie: VodStream
    ): LinearLayout {

        val card =
            LinearLayout(this)

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            dp(6),
            dp(6),
            dp(6),
            dp(6)
        )

        val poster =
            ImageView(this)

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

        if (movie.streamIcon.isNotEmpty()) {

            loadImage(
                movie.streamIcon,
                poster
            )
        }

        val title =
            TextView(this)

        title.text =
            movie.name

        title.textSize =
            14f

        title.setTextColor(
            Color.WHITE
        )

        title.maxLines = 2

        title.ellipsize =
            android.text.TextUtils.TruncateAt.END

        title.setPadding(
            dp(5),
            dp(8),
            dp(5),
            0
        )

        card.addView(title)

        if (movie.rating.isNotEmpty()) {

            val rating =
                TextView(this)

            rating.text =
                "★ ${movie.rating}"

            rating.textSize =
                12f

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
    // SELECCIÓN
    // ============================================================

    private fun onLiveSelected(
        stream: LiveStream
    ) {

        val extension =
            when {
                stream.streamType.equals(
                    "m3u8",
                    true
                ) -> "m3u8"

                else -> "ts"
            }

        val url =
            "$SERVER_URL/live/" +
                    "${encode(username)}/" +
                    "${encode(password)}/" +
                    "${stream.streamId}.$extension"

        showStreamDialog(
            stream.name,
            url
        )
    }

    private fun onVodSelected(
        movie: VodStream
    ) {

        val extension =
            movie.containerExtension
                .ifEmpty { "mp4" }

        val url =
            "$SERVER_URL/movie/" +
                    "${encode(username)}/" +
                    "${encode(password)}/" +
                    "${movie.streamId}.$extension"

        showStreamDialog(
            movie.name,
            url
        )
    }

    // ============================================================
    // STREAM DIALOG
    // ============================================================

    private fun showStreamDialog(
        title: String,
        url: String
    ) {

        val box =
            LinearLayout(this)

        box.orientation =
            LinearLayout.VERTICAL

        box.setPadding(
            dp(30),
            dp(20),
            dp(30),
            dp(20)
        )

        val titleView =
            TextView(this)

        titleView.text =
            title

        titleView.textSize =
            20f

        titleView.setTextColor(
            Color.WHITE
        )

        titleView.setTypeface(
            Typeface.DEFAULT,
            Typeface.BOLD
        )

        box.addView(titleView)

        val info =
            TextView(this)

        info.text =
            "URL generada correctamente.\n\n" +
                    "El siguiente paso será conectar esta URL al reproductor."

        info.textSize =
            14f

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

        dialog.show()
    }

    // ============================================================
    // API
    // ============================================================

    private fun apiRequest(
        params: Map<String, String>
    ): JSONObject {

        val url =
            buildApiUrl(params)

        val connection =
            URL(url).openConnection()
                    as HttpURLConnection

        connection.requestMethod =
            "GET"

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

    private fun apiRequestArray(
        params: Map<String, String>
    ): JSONArray {

        val url =
            buildApiUrl(params)

        val connection =
            URL(url).openConnection()
                    as HttpURLConnection

        connection.requestMethod =
            "GET"

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
    // IMAGE LOADING
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

                connection.useCaches =
                    true

                val bitmap =
                    connection.inputStream.use {
                        BitmapFactory.decodeStream(it)
                    }

                connection.disconnect()

                if (bitmap != null) {

                    runOnUiThread {

                        imageView.setImageBitmap(
                            bitmap
                        )
                    }
                }

            } catch (_: Exception) {
                // Si un poster falla,
                // mantenemos el placeholder.
            }
        }
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private fun createTopButton(
        text: String
    ): Button {

        val button =
            Button(this)

        button.text =
            text

        button.textSize =
            12f

        button.setTextColor(
            Color.WHITE
        )

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

    private fun createCategoryButton(
        text: String
    ): Button {

        val button =
            Button(this)

        button.text =
            text

        button.textSize =
            12f

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

        button.layoutParams =
            params

        return button
    }

    private fun createBlueButton(
        text: String
    ): Button {

        val button =
            Button(this)

        button.text =
            text

        button.textSize =
            13f

        button.setTextColor(
            Color.WHITE
        )

        button.setBackgroundColor(
            Color.rgb(22, 133, 245)
        )

        return button
    }

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

        button.layoutParams =
            params

        return button
    }

    private fun addPageTitle(
        title: String,
        subtitle: String
    ) {

        val titleView =
            TextView(this)

        titleView.text =
            title

        titleView.textSize =
            27f

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

        val subtitleView =
            TextView(this)

        subtitleView.text =
            subtitle

        subtitleView.textSize =
            14f

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

    private fun addSectionTitle(
        title: String,
        subtitle: String
    ) {

        val titleView =
            TextView(this)

        titleView.text =
            title

        titleView.textSize =
            21f

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

        val subtitleView =
            TextView(this)

        subtitleView.text =
            subtitle

        subtitleView.textSize =
            13f

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
                if (visible)
                    View.VISIBLE
                else
                    View.GONE
        }
    }

    private fun showError(
        message: String
    ) {

        content.removeAllViews()

        val error =
            TextView(this)

        error.text =
            message

        error.textSize =
            15f

        error.setTextColor(
            Color.rgb(255, 130, 130)
        )

        error.setPadding(
            dp(20),
            dp(30),
            dp(20),
            dp(30)
        )

        content.addView(error)
    }

    private fun removeContentAfterCategoryArea() {

        /*
         * La pantalla se vuelve a construir
         * para evitar duplicar las tarjetas
         * cuando cambiamos de categoría.
         *
         * Conservamos las categorías recreándolas
         * mediante renderLiveCategories/renderVodCategories.
         */

        if (currentSection == "live") {

            renderLiveCategoryHeaderOnly()

        } else if (currentSection == "vod") {

            renderVodCategoryHeaderOnly()
        }
    }

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
    // DATA CLASSES
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
    // UTILIDADES
    // ============================================================

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }

    override fun onDestroy() {

        executor.shutdownNow()

        super.onDestroy()
    }
}
