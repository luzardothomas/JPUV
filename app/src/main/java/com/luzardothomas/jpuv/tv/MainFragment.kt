package com.luzardothomas.jpuv.tv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.util.Log
import android.util.StateSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.luzardothomas.jpuv.importer.LocalAutoImporter
import com.luzardothomas.jpuv.importer.RandomizeImporter
import com.luzardothomas.jpuv.importer.ServerAutoImporter
import com.luzardothomas.jpuv.server.SmbGateway
import com.luzardothomas.jpuv.utils.ApiSwitchPresenter
import com.luzardothomas.jpuv.utils.JsonDataManager
import com.luzardothomas.jpuv.utils.Movie
import com.luzardothomas.jpuv.utils.VideoItem
import com.luzardothomas.jpuv.utils.ImportedJson
import com.luzardothomas.jpuv.video.DetailsActivity
import com.luzardothomas.jpuv.search.ImageSearchActivity
import com.luzardothomas.jpuv.search.SearchActivity
import com.luzardothomas.jpuv.R
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Main de la aplicación de TV
 */

class MainFragment : BrowseSupportFragment() {

    private val mHandler = Handler(Looper.getMainLooper())
    private val jsonDataManager = JsonDataManager()

    private lateinit var randomizeImporter: RandomizeImporter

    private lateinit var smbGateway: SmbGateway

    private val preloadedPosterUrls = HashSet<String>()

    private var pendingFocusLastPlayed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideHeadersDockCompletely(view)
        disableSearchOrbFocus(view)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.default_background))
        setupSmoothScrolling(view)

        jsonDataManager.loadData(requireContext())
        randomizeImporter = RandomizeImporter(requireContext(), jsonDataManager)
        smbGateway = SmbGateway(requireContext())
        smbGateway.ensureProxyStarted(8081)

        preloadPostersForImportedJsons()

        setupUIElements()
        loadRows()
        setupEventListeners()

        mHandler.post { focusFirstItemReal() }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { smbGateway.stopDiscovery() } catch (_: Exception) {}
        try { smbGateway.stopProxy() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        mHandler.post { focusLastPlayedIfAny() }
        // Aseguramos que Glide vuelva a cargar si quedó pausado
        Glide.with(requireContext()).resumeRequests()
    }

    private fun setupSmoothScrolling(root: View) {
        // Buscamos la lista vertical interna de Leanback
        val verticalGrid = root.findViewById<VerticalGridView>(androidx.leanback.R.id.browse_grid) ?: return

        verticalGrid.apply {
            // Aumentar caché: Guarda 20 filas en memoria.
            setItemViewCacheSize(20)

            // Optimización de layout
            setHasFixedSize(true)

            // PAUSAR IMÁGENES AL SCROLLEAR
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        // Si frenó, cargamos imágenes
                        if (isAdded) Glide.with(requireContext()).resumeRequests()
                    } else {
                        // Si se mueve, PAUSA total. Prioridad a la animación.
                        if (isAdded) Glide.with(requireContext()).pauseRequests()
                    }
                }
            })
        }
    }

    private fun setupUIElements() {
        // Como el logo ya tiene el nombre,
        // lo mejor es dejar el título vacío o con un solo espacio para que no se pisen.
        val rawTitle = " "
        val spannableTitle = SpannableString(rawTitle)

        val density = resources.displayMetrics.density

        // Altura fija de 60dp para que no se corte arriba
        val heightInPx = (60 * density).toInt()

        val logoDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.logo_with_name)

        logoDrawable?.apply {
            // 2. CÁLCULO DE PROPORCIÓN:
            // Calculamos el ancho basándonos en la relación aspecto original del PNG
            val aspectRatio = intrinsicWidth.toFloat() / intrinsicHeight.toFloat()
            val widthInPx = (heightInPx * aspectRatio).toInt()

            // 3. Aplicamos el tamaño calculado
            setBounds(0, 0, widthInPx, heightInPx)

            val imageSpan = ImageSpan(this, ImageSpan.ALIGN_BOTTOM)
            spannableTitle.setSpan(imageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Asignamos el spannable que contiene solo el logo (ya que tiene el nombre incluido)
        title = spannableTitle

        // Configuración Leanback
        headersState = HEADERS_HIDDEN
        isHeadersTransitionOnBackEnabled = false
        brandColor = ContextCompat.getColor(requireContext(), R.color.default_background)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.search_opaque)
    }

    private fun createOptimizedListRowPresenter(): ListRowPresenter {
        return ListRowPresenter().apply {
            shadowEnabled = false        // Sin sombras (GPU heavy)
            selectEffectEnabled = false  // Sin efecto zoom/dim automático
        }
    }

    private fun loadRows() {
        val rowsAdapter = ArrayObjectAdapter(createOptimizedListRowPresenter())
        val cardPresenter = CardPresenter()

        // =========================
        // BOTÓN API CON SWITCH (TV)
        // =========================
        val apiAdapter = ArrayObjectAdapter(ApiSwitchPresenter()).apply {
            add("__action_toggle_api__") // Solo agregamos el ID, el Presenter se encarga del resto
        }
        rowsAdapter.add(ListRow(HeaderItem(999L, ""), apiAdapter))

        // =========================
        // ACCIONES PRINCIPALES
        // =========================
        val actionsAdapter = ArrayObjectAdapter(GridItemPresenter()).apply {
            add("Importar de DISPOSITIVO")
            add("Actualizar portadas de JSONS")
            add(getString(R.string.erase_json))
            add("Borrar todos los JSON")
        }
        rowsAdapter.add(ListRow(HeaderItem(0L, "ACCIONES PRINCIPALES"), actionsAdapter))

        // =========================
        // ARMADO DE REPRODUCCIÓN
        // =========================
        val playbackBuildAdapter = ArrayObjectAdapter(GridItemPresenter()).apply {
            add("Generar playlist RANDOM")
            add("Actualizar playlist RANDOM")
            add("Borrar playlists RANDOM")
            add("Borrar TODAS las playlists RANDOM")
        }
        rowsAdapter.add(ListRow(HeaderItem(1L, "ARMADO DE REPRODUCCIÓN"), playbackBuildAdapter))

        // =========================
        // ACCIONES AVANZADAS
        // =========================
        val advancedActions = ArrayObjectAdapter(GridItemPresenter()).apply {
            add("Importar de SMB")
            add("Conectarse a un SMB")
            add("Limpiar credenciales especificas")
            add("Limpiar credenciales")
        }
        rowsAdapter.add(ListRow(HeaderItem(2L, "ACCIONES AVANZADAS"), advancedActions))

        // =========================
        // CATALOGO
        // =========================
        val importedAll = jsonDataManager.getImportedJsons()

        fun isRandomImported(imported: ImportedJson): Boolean =
            imported.fileName.uppercase(Locale.ROOT).startsWith("RANDOM")

        val importedSorted = importedAll.sortedWith(
            compareByDescending<ImportedJson> { isRandomImported(it) }
                .thenBy { prettyTitle(it.fileName).lowercase(Locale.ROOT) }
        )

        val randomCover = "android.resource://${requireContext().packageName}/drawable/dices"

        importedSorted.forEachIndexed { idx, imported ->
            val rowAdapter = ArrayObjectAdapter(cardPresenter)

            if (isRandomImported(imported)) {
                rowAdapter.add(
                    Movie(
                        title = prettyTitle(imported.fileName),
                        videoUrl = "playlist://${imported.fileName}",
                        cardImageUrl = randomCover,
                        backgroundImageUrl = null,
                        skipToSecond = 0,
                        delaySkip = 0,
                        description = "Playlist RANDOM"
                    )
                )
            } else {
                imported.videos.forEach { v -> rowAdapter.add(v.toMovie()) }
            }

            val headerId = 1000L + idx
            rowsAdapter.add(
                ListRow(
                    HeaderItem(headerId, prettyTitle(imported.fileName)),
                    rowAdapter
                )
            )
        }

        adapter = rowsAdapter
    }

    // ==========================================
    // Gestión de colores de las cartas de acción
    // ==========================================
    private inner class GridItemPresenter : Presenter() {

        private var density: Float = 0f
        private var initialized = false

        private val colorFocused = 0xFFA32877.toInt()
        private val colorDefault = 0xFF2C2C2C.toInt()
        private val strokeFocused = 0xFFFFFFFF.toInt()
        private val strokeDefault = 0x66FFFFFF.toInt()

        private fun initMetrics(ctx: Context) {
            if (!initialized) {
                density = ctx.resources.displayMetrics.density
                initialized = true
            }
        }

        private fun dp(v: Int): Int = (v * density).toInt()

        private fun createStateListDrawable(): StateListDrawable {
            val res = StateListDrawable()

            val focusedDr = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(colorFocused)
                setStroke(dp(2), strokeFocused)
            }

            val defaultDr = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(colorDefault)
                setStroke(dp(2), strokeDefault)
            }

            res.addState(intArrayOf(android.R.attr.state_focused), focusedDr)
            res.addState(StateSet.WILD_CARD, defaultDr)

            // Eliminamos fade duration para respuesta instantánea en scroll rápido
            res.setEnterFadeDuration(0)
            res.setExitFadeDuration(0)

            return res
        }

        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val ctx = parent.context
            initMetrics(ctx)

            val container = FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(dp(180), dp(180))
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = createStateListDrawable()
                isFocusable = true
                isFocusableInTouchMode = true
            }

            val tv = TextView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.05f)
            }

            container.addView(tv)

            container.setOnFocusChangeListener { v, hasFocus ->
                // Animación muy corta y simple
                v.animate()
                    .scaleX(if (hasFocus) 1.05f else 1f)
                    .scaleY(if (hasFocus) 1.05f else 1f)
                    .setDuration(100)
                    .start()
            }

            return ViewHolder(container)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder?, item: Any?) {
            val container = viewHolder?.view as? FrameLayout ?: return
            val tv = container.getChildAt(0) as? TextView ?: return
            val textValue = (item as? String)?.uppercase(java.util.Locale.getDefault()) ?: ""
            tv.text = textValue
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    // ==========================================
    // Helpers
    // ==========================================

    private fun hideHeadersDockCompletely(root: View) {
        root.post {
            val bg = ContextCompat.getColor(requireContext(), R.color.default_background)
            root.findViewById<ViewGroup>(androidx.leanback.R.id.browse_headers_dock)?.apply {
                setBackgroundColor(bg)
                layoutParams = layoutParams.apply { width = 1 }
                alpha = 0f
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            root.findViewById<ViewGroup>(androidx.leanback.R.id.browse_headers)?.apply {
                alpha = 0f
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            root.findViewById<View>(androidx.leanback.R.id.browse_frame)?.apply {
                setPadding(0, paddingTop, paddingRight, paddingBottom)
            }
        }
    }

    private fun disableSearchOrbFocus(root: View) {
        root.post {
            root.findViewById<View>(androidx.leanback.R.id.search_orb)?.apply {
                isFocusable = false
                clearFocus()
            }
        }
    }

    private fun focusFirstItemReal() {
        val root = view ?: return

        root.post main@{
            setSelectedPosition(0, false)
            val vgrid = root.findViewById<VerticalGridView>(androidx.leanback.R.id.browse_grid) ?: return@main

            vgrid.post grid@{
                val firstRowView = vgrid.getChildAt(0) ?: return@grid
                val rowContent = firstRowView.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content) ?: return@grid

                rowContent.post row@{
                    rowContent.getChildAt(0)?.requestFocus() ?: rowContent.requestFocus()
                }
            }
        }
    }

    private fun writeLastPlayedUrl(url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LAST_PLAYED, u).apply()
    }

    private fun readLastPlayedUrl(): String? {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_PLAYED, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun focusLastPlayedIfAny(): Boolean {
        val lastUrl = readLastPlayedUrl() ?: return false
        val rows = adapter as? ArrayObjectAdapter ?: return false

        var targetRowIndex = -1
        var targetColIndex = -1

        for (r in 0 until rows.size()) {
            val row = rows.get(r) as? ListRow ?: continue
            val rowAdapter = row.adapter ?: continue
            for (c in 0 until rowAdapter.size()) {
                val m = rowAdapter.get(c) as? Movie ?: continue
                if (m.videoUrl == lastUrl) {
                    targetRowIndex = r; targetColIndex = c; break
                }
            }
            if (targetRowIndex >= 0) break
        }

        if (targetRowIndex < 0) return false

        setSelectedPosition(targetRowIndex, false, object : Presenter.ViewHolderTask() {
            private var tries = 0
            override fun run(holder: Presenter.ViewHolder?) {
                tries++
                if (holder == null || holder.view == null) {
                    if (tries < 20) mHandler.postDelayed({ setSelectedPosition(targetRowIndex, false, this) }, 50)
                    return
                }
                val rowContent = holder.view.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
                if (rowContent == null) {
                    if (tries < 20) mHandler.postDelayed({ setSelectedPosition(targetRowIndex, false, this) }, 50)
                    return
                }
                rowContent.scrollToPosition(targetColIndex)
                rowContent.post {
                    rowContent.setSelectedPosition(targetColIndex)
                    rowContent.requestFocus()
                }
            }
        })
        return true
    }

    private fun preloadPostersForImportedJsons() {
        val ctx = requireContext()
        val sizePx = (POSTER_PRELOAD_SIZE_DP * ctx.resources.displayMetrics.density).toInt()
        val all = jsonDataManager.getImportedJsons().flatMap { it.videos }.take(20)
        for (v in all) {
            val url = v.cardImageUrl.trim().orEmpty()
            if (url.isNotEmpty() && preloadedPosterUrls.add(url)) {
                Glide.with(ctx).load(url).override(sizePx, sizePx).centerCrop().preload()
            }
        }
    }

    private fun prettyTitle(fileName: String): String {
        return fileName.removeSuffix(".json")
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } }
    }

    private fun VideoItem.toMovie() = Movie(
        title = title,
        videoUrl = videoUrl,
        cardImageUrl = cardImageUrl,
        backgroundImageUrl = backgroundImageUrl,
        skipToSecond = skip,
        delaySkip = delaySkip,
        description = "Importado desde un JSON"
    )

    private fun setupEventListeners() {
        setOnSearchClickedListener { startActivity(Intent(requireContext(), SearchActivity::class.java)) }
        onItemViewClickedListener = ItemViewClickedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any, rowViewHolder: RowPresenter.ViewHolder, row: Row) {
            when (item) {
                is Movie -> navigateToDetails(itemViewHolder, item, row)

                is String -> {
                    // Interceptamos el switch de la API
                    if (item == "__action_toggle_api__") {
                        return
                    }
                    else {
                        //  Si no es lo de la API, ejecutamos la lógica normal de strings
                        handleStringAction(item)
                    }
                }
            }
        }

        private fun navigateToDetails(itemViewHolder: Presenter.ViewHolder, movie: Movie, row: Row) {
            val clickedUrl = movie.videoUrl?.trim().orEmpty()
            if (clickedUrl.isNotEmpty()) writeLastPlayedUrl(clickedUrl)

            if (movie.videoUrl?.startsWith("playlist://") == true) {
                val url = movie.videoUrl ?: return
                val playlistName = url.removePrefix("playlist://").trim()

                // Solo verificamos que exista para no abrir un reproductor vacío
                val imported = jsonDataManager.getImportedJsons().firstOrNull { it.fileName == playlistName }
                if (imported == null || imported.videos.isEmpty()) {
                    Toast.makeText(requireContext(), "Playlist vacía", Toast.LENGTH_LONG).show()
                    return
                }

                // Agarramos solo el primer video para la vista de detalles
                val firstMovie = imported.videos[0].toMovie()

                val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.MOVIE, firstMovie)
                    putExtra("EXTRA_PLAYLIST_NAME", playlistName)
                    putExtra(DetailsActivity.EXTRA_INDEX, 0)
                    putExtra("EXTRA_LOOP_PLAYLIST", true)
                    putExtra("EXTRA_DISABLE_LAST_PLAYED", true)
                }
                pendingFocusLastPlayed = false
                startActivityWithAnim(itemViewHolder, intent)
                return
            }

            val listRow = row as? ListRow
            val adapter = listRow?.adapter
            val playlist = ArrayList<Movie>()
            if (adapter != null) {
                for (i in 0 until adapter.size()) {
                    val obj = adapter.get(i)
                    if (obj is Movie) playlist.add(obj)
                }
            }
            val index = playlist.indexOfFirst { it.videoUrl == movie.videoUrl }.let { if (it >= 0) it else 0 }
            val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                putExtra(DetailsActivity.MOVIE, movie)
                if (playlist.size > 1) {
                    putExtra(DetailsActivity.EXTRA_PLAYLIST, playlist)
                    putExtra(DetailsActivity.EXTRA_INDEX, index)
                }
            }
            pendingFocusLastPlayed = true
            startActivityWithAnim(itemViewHolder, intent)
        }

        private fun startActivityWithAnim(vh: Presenter.ViewHolder, intent: Intent) {
            val cardView = vh.view as? ImageCardView
            val shared = cardView?.mainImageView
            if (shared != null) {
                val opts = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), shared, DetailsActivity.SHARED_ELEMENT_NAME)
                startActivity(intent, opts.toBundle())
            } else {
                startActivity(intent)
            }
        }

        // Este objeto se encarga de lanzar la actividad y recibir el resultado de vuelta
        private val imageSearchLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                // Limpiamos la memoria de imágenes para que Glide no use la portada vieja
                Glide.get(requireContext()).clearMemory()

                // Recargar todo
                refreshUI()

                Toast.makeText(requireContext(), "Portadas actualizadas ✅", Toast.LENGTH_SHORT).show()
            }
        }

        private fun handleStringAction(item: String) {
            when (item) {
                "Conectarse a un SMB" -> openSmbConnectFlow()
                "Limpiar credenciales especificas" -> showSelectServerToDeleteDialog()
                "Limpiar credenciales" -> showClearSmbDialog()
                "Importar de SMB" -> runAutoImport()
                "Generar playlist RANDOM" -> runRandomGenerate()
                "Actualizar playlist RANDOM" -> runRandomUpdate()
                "Borrar playlists RANDOM" -> runRandomDeleteSelected()
                "Borrar TODAS las playlists RANDOM" -> runRandomDeleteAll()
                getString(R.string.erase_json) -> showDeleteDialog()
                "Borrar todos los JSON" -> showDeleteAllDialog()
                "Importar de DISPOSITIVO" -> requestLocalImportWithPermission()
                "Actualizar portadas de JSONS" -> {
                    val importedJsons = jsonDataManager.getImportedJsons()
                    // Obtenemos nombres, filtramos y ordenamos A-Z
                    val listForIntent = ArrayList(importedJsons.map { it.fileName }.sorted())

                    if (listForIntent.isEmpty()) {
                        Toast.makeText(requireContext(), "No hay JSONs", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(requireContext(), ImageSearchActivity::class.java).apply {
                            putStringArrayListExtra("TARGET_JSONS", listForIntent)
                        }
                        imageSearchLauncher.launch(intent)
                    }
                }
                else -> Toast.makeText(requireContext(), item, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // Acciones Lógicas (Import, SMB, Dialogs)
    // ==========================================
    private fun runRandomGenerate() {
        randomizeImporter.actionGenerateRandom(
            toast = { msg -> activity?.runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() } },
            onDone = { activity?.runOnUiThread { refreshUI() } },
            onError = { err -> activity?.runOnUiThread { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show() } }
        )
    }

    private fun runRandomUpdate() {
        randomizeImporter.actionUpdateRandom(
            toast = { msg -> activity?.runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() } },
            onDone = { activity?.runOnUiThread { refreshUI() } },
            onError = { err -> activity?.runOnUiThread { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show() } }
        )
    }

    private fun runRandomDeleteSelected() {
        randomizeImporter.actionDeleteRandomPlaylists(
            toast = { msg -> activity?.runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() } },
            onDone = { activity?.runOnUiThread { refreshUI() } },
            onError = { err -> activity?.runOnUiThread { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show() } }
        )
    }

    private fun runRandomDeleteAll() {
        randomizeImporter.actionDeleteAllRandomPlaylists(
            toast = { msg -> activity?.runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() } },
            onDone = { activity?.runOnUiThread { refreshUI() } },
            onError = { err -> activity?.runOnUiThread { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show() } }
        )
    }

    private fun showClearSmbDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Limpiar credenciales")
            .setMessage("¿Borrar todas las credenciales y shares?")
            .setPositiveButton("Borrar") { _, _ ->
                smbGateway.clearAllSmbData()
                Toast.makeText(requireContext(), "Credenciales eliminadas ✅", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showSelectServerToDeleteDialog() {
        val savedServers = smbGateway.listCachedServers()

        if (savedServers.isEmpty()) {
            Toast.makeText(requireContext(), "No hay credenciales guardadas", Toast.LENGTH_SHORT).show()
            return
        }

        val options = savedServers.map { "${it.host} (${it.name})" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Selecciona el servidor")
            .setItems(options) { _, which ->
                val selectedServer = savedServers[which]
                onDeleteServerClicked(selectedServer.id, selectedServer.name)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun onDeleteServerClicked(serverId: String, serverName: String) {
        val savedShares = smbGateway.getSavedShares(serverId).toList()

        if (savedShares.isEmpty()) {
            smbGateway.deleteSpecificSmbData(serverId)
            refreshUI()
            return
        }

        val options = savedShares.toTypedArray()
        val selectedIndices = mutableSetOf<Int>()

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Administrar shares de $serverName")
            .setMultiChoiceItems(options, null) { _, which, isChecked ->
                if (isChecked) selectedIndices.add(which) else selectedIndices.remove(which)
            }
            // Asignamos los roles, pero los reubicaremos programáticamente
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Eliminar seleccionados") { _, _ ->
                if (selectedIndices.isNotEmpty()) {
                    val sharesToDelete = selectedIndices.map { options[it] }
                    showConfirmationDialog("Borrar shares", "¿Eliminar ${sharesToDelete.size} share(s)?") {
                        smbGateway.removeMultipleShares(serverId, sharesToDelete)
                        refreshUI()
                    }
                }
            }
            .setPositiveButton("BORRAR TODO EL SMB") { _, _ ->
                showConfirmationDialog("Borrar todo", "¿Eliminar todo el SMB?") {
                    smbGateway.deleteSpecificSmbData(serverId)
                    refreshUI()
                }
            }

        val dialog = builder.create()
        dialog.show()

        val btnPositive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        val btnNeutral = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
        val btnNegative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

        // Obtenemos el contenedor de los botones (el parent)
        val buttonParent = btnPositive.parent as? android.view.ViewGroup
        if (buttonParent != null) {
            // Limpiamos el orden actual
            buttonParent.removeAllViews()

            // Los agregamos en tu orden exacto: Izquierda -> Medio -> Derecha
            buttonParent.addView(btnNegative) // Cancelar
            buttonParent.addView(btnNeutral)  // Eliminar Seleccionados
            buttonParent.addView(btnPositive) // Borrar Todo el Server
        }
    }

    // También actualizamos esta para que use el mismo estilo y no de error
    private fun showConfirmationDialog(title: String, msg: String, onConfirm: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(title)
            .setMessage(msg)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Eliminar") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun ensureAllFilesAccessTv(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
                return false
            }
        }
        return true
    }

    private fun requestLocalImportWithPermission() {
        if (ensureAllFilesAccessTv()) runLocalAutoImport()
        else Toast.makeText(requireContext(), "Habilitá permisos de almacenamiento", Toast.LENGTH_LONG).show()
    }

    private fun runLocalAutoImport() {
        LocalAutoImporter(requireContext(), jsonDataManager, 8080).run(
            toast = { msg -> activity?.runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() } },
            onDone = { activity?.runOnUiThread { refreshUI() } },
            onError = { err -> activity?.runOnUiThread { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show() } }
        )
    }

    private fun runAutoImport() {
        ServerAutoImporter(requireContext(), smbGateway, jsonDataManager, 8081).run(
            toast = { msg -> activity?.runOnUiThread { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() } },
            onDone = { activity?.runOnUiThread { refreshUI() } },
            onError = { err -> activity?.runOnUiThread { Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show() } }
        )
    }

    private fun showDeleteAllDialog() {
        val count = jsonDataManager.getImportedJsons().size
        if (count == 0) {
            Toast.makeText(requireContext(), "No hay JSONs para borrar", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar TODOS")
            .setMessage("Se borrarán $count JSONs. ¿Seguro?")
            .setPositiveButton("Eliminar") { _, _ ->
                jsonDataManager.removeAll(requireContext())
                preloadedPosterUrls.clear()
                refreshUI()
                Toast.makeText(requireContext(), "JSONs eliminados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun openSmbConnectFlow() {
        val options = arrayOf("Servidor Local (Escanear)", "Servidor Externo (IP)")

        AlertDialog.Builder(requireContext())
            .setTitle("Seleccionar tipo de conexión")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startLocalNetworkDiscovery()
                    1 -> showManualIpDialog()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startLocalNetworkDiscovery() {
        Toast.makeText(requireContext(), "Buscando en red local...", Toast.LENGTH_SHORT).show()
        val found = LinkedHashMap<String, SmbGateway.SmbServer>()

        smbGateway.discoverAll(
            onFound = { server -> found[server.id] = server },
            onError = { err -> Toast.makeText(requireContext(), "Error: $err", Toast.LENGTH_LONG).show() }
        )

        Handler(Looper.getMainLooper()).postDelayed({
            smbGateway.stopDiscovery()
            if (found.isEmpty()) {
                Toast.makeText(requireContext(), "No se encontraron servidores", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
            val servers = found.values.toList()
            val labels = servers.map { "${it.name} (${it.host}:${it.port})" }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Servidores en Red")
                .setItems(labels) { _, which -> showCredentialsDialog(servers[which]) }
                .setNegativeButton("Atrás", { _, _ -> openSmbConnectFlow() })
                .show()
        }, 2500)
    }

    private fun showManualIpDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 0)
        }

        val userInput = EditText(requireContext()).apply {
            hint = "Usuario (opcional)"
            setSingleLine()
        }
        val passInput = EditText(requireContext()).apply {
            hint = "Contraseña (opcional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val ipInput = EditText(requireContext()).apply {
            hint = "IP (obligatoria)"
            setSingleLine()
        }
        val portInput = EditText(requireContext()).apply {
            hint = "PORT (obligatorio)"
            setSingleLine()
        }
        val shareInput = EditText(requireContext()).apply {
            hint = "Share (obligatorio)"
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }

        layout.addView(ipInput)
        layout.addView(portInput)
        layout.addView(userInput)
        layout.addView(passInput)
        layout.addView(shareInput)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Conectar Servidor Externo")
            .setView(layout)
            .setPositiveButton("Conectar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ip = ipInput.text.toString().trim()
                val port = portInput.text.toString().trim()
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString()
                val share = shareInput.text.toString().trim().replace("/", "")

                if (ip.isEmpty()) {
                    Toast.makeText(requireContext(), "IP requerido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (port.isEmpty()) {
                    Toast.makeText(requireContext(), "Puerto requerido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (share.isBlank()) {
                    Toast.makeText(requireContext(), "Share requerido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val creds = SmbGateway.SmbCreds(user,pass, null, false)
                val serverId = smbGateway.makeServerId(ip, 445)

                // Deshabilitamos el botón para evitar múltiples clics mientras testea
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                Thread {
                    try {
                        // Validamos que el servidor responda y que el share sea accesible
                        smbGateway.testLogin(ip, creds)
                        smbGateway.testShareAccess(ip, creds, share)

                        // Si las pruebas pasan, guardamos de forma persistente
                        smbGateway.saveCreds(
                            serverId = serverId,
                            host = ip,
                            creds = creds,
                            port = 445,
                            serverName = "Externo ($ip)"
                        )
                        smbGateway.saveLastShare(serverId, share)

                        val prefs = requireContext().getSharedPreferences("server_ports", Context.MODE_PRIVATE)
                        prefs.edit().putInt("port_$serverId", port.toIntOrNull() ?: 8081).apply()

                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Servidor externo conectado ✅", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e("SMB_MANUAL", "Fallo al conectar a servidor externo", e)
                        activity?.runOnUiThread {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            val errorMsg = when {
                                e.message?.contains("Timeout") == true -> "Tiempo de espera agotado"
                                e.message?.contains("Access Denied") == true -> "Acceso denegado al share '$share'"
                                else -> "Error: ${e.message}"
                            }
                            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }

        shareInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                true
            } else false
        }

        dialog.show()
    }

    private fun showCredentialsDialog(server: SmbGateway.SmbServer) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 0)
        }

        // Campo Usuario
        val userInput = EditText(requireContext()).apply {
            hint = "Usuario (obligatorio)"
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        // Campo Contraseña
        val passInput = EditText(requireContext()).apply {
            hint = "Contraseña (obligatoria)"
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        // Campo Share
        val shareInput = EditText(requireContext()).apply {
            hint = "Share (obligatorio)"
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        layout.addView(userInput)
        layout.addView(passInput)
        layout.addView(shareInput)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Login: ${server.host}")
            .setView(layout)
            .setPositiveButton("Conectar") { _, _ ->
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString()
                val share = shareInput.text.toString().trim()

                if (user.isBlank()) {
                    Toast.makeText(requireContext(), "Usuario requerido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pass.isBlank()) {
                    Toast.makeText(requireContext(), "Contraseña requerida", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (share.isBlank()) {
                    Toast.makeText(requireContext(), "Share requerido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                Thread {
                    try {
                        val creds = SmbGateway.SmbCreds(user, pass, null)
                        smbGateway.testLogin(server.host, creds)
                        smbGateway.testShareAccess(server.host, creds, share)
                        smbGateway.saveCreds(server.id, server.host, creds, server.port, server.name)
                        smbGateway.saveLastShare(server.id, share)
                        activity?.runOnUiThread { Toast.makeText(requireContext(), "Servidor local conectado ✅", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        activity?.runOnUiThread { Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        // Al darle a "Done" en el último campo se ejecute el botón positivo
        shareInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                true
            } else false
        }

        dialog.show()
    }

    private fun showDeleteDialog() {
        // Obtener y ordenar la lista A-Z por nombre de archivo
        val imported = jsonDataManager.getImportedJsons()
            .sortedBy { it.fileName.lowercase(java.util.Locale.ROOT) }

        if (imported.isEmpty()) {
            Toast.makeText(requireContext(), "No hay JSONs para borrar", Toast.LENGTH_SHORT).show()
            return
        }

        // Generar los títulos "lindos" para mostrar en la lista
        val labels = imported.map { prettyTitle(it.fileName) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar JSON")
            .setItems(labels) { _, which ->
                val targetFile = imported[which].fileName

                // Diálogo de confirmación (Seguridad)
                AlertDialog.Builder(requireContext())
                    .setTitle("¿Confirmar eliminación?")
                    .setMessage("Vas a borrar: ${prettyTitle(targetFile)}")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("ELIMINAR") { _, _ ->
                        // Ejecutar el borrado real
                        jsonDataManager.removeJson(requireContext(), targetFile)
                        preloadedPosterUrls.clear()
                        refreshUI()
                        Toast.makeText(requireContext(), "JSON eliminado", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
            .setNegativeButton("CERRAR", null)
            .show()
    }

    private fun refreshUI() {
        jsonDataManager.loadData(requireContext())
        preloadPostersForImportedJsons()
        loadRows()
        mHandler.post { focusFirstItemReal() }
    }

    companion object {
        private const val POSTER_PRELOAD_SIZE_DP = 180
        private const val PREFS_NAME = "jpuv_prefs"
        private const val KEY_LAST_PLAYED = "LAST_PLAYED_VIDEO_URL"
    }
}
