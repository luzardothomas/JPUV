package com.luzardothomas.jpuv.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luzardothomas.jpuv.databinding.ActivityImageSearchBinding
import kotlinx.coroutines.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.luzardothomas.jpuv.importer.ImageImporter
import java.io.File
import com.luzardothomas.jpuv.R

/**
 * Buscador de imagenes para covers
 */
class ImageSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageSearchBinding
    private val imageAdapter = ImageAdapter()
    private var currentFirst = 1
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuración moderna de pantalla completa
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()

        if (isTvDevice()) {
            setupTvMode(savedInstanceState)
        } else {
            setupMobileMode()
        }
    }

    private fun hideSystemUI() {
        val window = window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupTvMode(savedInstanceState: Bundle?) {
        binding.mobileLayout.visibility = View.GONE
        binding.mobileLayout.isFocusable = false
        binding.loadingLayout.visibility = View.GONE
        binding.loadingLayout.isFocusable = false
        binding.tvFragmentContainer.visibility = View.VISIBLE

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_fragment_container, ImageSearchTvFragment(), "BUSCADOR_TAG")
                .commit()
        }
    }

    private fun setupMobileMode() {
        binding.tvFragmentContainer.visibility = View.GONE
        binding.mobileLayout.visibility = View.VISIBLE
        initViews()
    }

    private fun initViews() {
        val colorBackAndSearch = androidx.core.content.ContextCompat.getColorStateList(this, R.color.fastlane_background)

        // En móvil usamos 4 columnas para que las fotos sean grandes
        val manager = GridLayoutManager(this, 4)
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (imageAdapter.getItemViewType(position) == 1) 4 else 1
            }
        }

        binding.recyclerImages.layoutManager = manager
        binding.recyclerImages.adapter = imageAdapter

        binding.btnBackMobile.visibility = View.VISIBLE
        binding.btnBackMobile.backgroundTintList = colorBackAndSearch
        binding.btnBackMobile.setOnClickListener { finish() }

        binding.btnSearch.backgroundTintList = colorBackAndSearch
        binding.btnSearch.setTextColor(Color.WHITE)
        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString()
            if (query.isNotBlank()) performSearch(query)
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString()
                if (query.isNotBlank()) performSearch(query)
                true
            } else false
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isTvDevice() && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            val fragment = supportFragmentManager.findFragmentByTag("BUSCADOR_TAG") as? ImageSearchTvFragment
            val gridView = fragment?.view?.findViewById<androidx.leanback.widget.VerticalGridView>(androidx.leanback.R.id.container_list)

            if (gridView == null || gridView.selectedPosition <= 0) {
                val searchOrb = fragment?.view?.findViewById<View>(androidx.leanback.R.id.lb_search_bar_speech_orb)
                    ?: fragment?.view?.findViewById<View>(androidx.leanback.R.id.lb_search_frame)

                if (searchOrb != null) {
                    searchOrb.isFocusable = true
                    searchOrb.requestFocus()
                    Log.d("FOCUS_DEBUG", "¡SALTO AL MICRO FORZADO!")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            setVoiceResults(spokenText)
        }
    }

    fun setVoiceResults(text: String?) {
        if (text == null) return
        runOnUiThread {
            binding.etSearch.setText(text)
            if (isTvDevice()) {
                val fragment = supportFragmentManager.findFragmentByTag("BUSCADOR_TAG") as? ImageSearchTvFragment
                fragment?.setVoiceResults(text)
            } else {
                performSearch(text)
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        currentQuery = query
        currentFirst = 1

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

        setLoading(true, "Buscando '$query'...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val urls = ImageImporter.searchImages(query, first = currentFirst)

                // --- LÓGICA DE FILAS COMPLETAS (Múltiplo de 4) ---
                // Si llegan 17 items: 10/4 = 3 enteros. 4*4 = 16. Tomamos los primeros 16.
                val validSize = (urls.size / 4) * 4
                val filteredUrls = urls.take(validSize)

                withContext(Dispatchers.Main) {
                    // Importante: Chequear si tras el filtro quedó vacía
                    if (urls.isNotEmpty() && filteredUrls.isEmpty()) {
                        Toast.makeText(this@ImageSearchActivity, "Resultados insuficientes para llenar una fila.", Toast.LENGTH_SHORT).show()
                        imageAdapter.updateList(emptyList())
                    } else {
                        imageAdapter.updateList(filteredUrls)
                    }

                    setLoading(false)
                    binding.recyclerImages.scrollToPosition(0)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(this@ImageSearchActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadMoreMobile() {
        // Aumentamos el puntero para la siguiente página
        // Nota: Como descartamos visualmente algunos items, puede que veas un "salto" de imagen,
        // pero es necesario para mantener la estética estricta de 4 columnas.
        currentFirst += 10

        setLoading(true, "Cargando más...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newUrls = ImageImporter.searchImages(currentQuery, first = currentFirst)

                // --- LÓGICA DE FILAS COMPLETAS (Múltiplo de 4) ---
                val validSize = (newUrls.size / 4) * 4
                val filteredNewUrls = newUrls.take(validSize)

                withContext(Dispatchers.Main) {
                    if (filteredNewUrls.isNotEmpty()) {
                        imageAdapter.addUrls(filteredNewUrls)
                    }
                    setLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setLoading(false) }
            }
        }
    }

    fun processImageSelection(imageUrl: String) {
        runOnUiThread {
            setLoading(false) // Ocultamos el loading de búsqueda para mostrar la modal

            // --- COLORES Y RECURSOS (Tu estilo original) ---
            val colorBg = Color.parseColor("#303030")
            val colorTeal = Color.parseColor("#80CBC4")
            val colorHover = Color.parseColor("#505050")
            val tealStateList = android.content.res.ColorStateList.valueOf(colorTeal)

            val hoverDrawable = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.ColorDrawable(colorHover))
                addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            }

            val allFiles = intent.getStringArrayListExtra("TARGET_JSONS") ?: arrayListOf()
            val itemsAsArray = allFiles.toTypedArray()

            // CONTENEDORES
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 40, 60, 20)
            }

            val customTitle = TextView(this).apply {
                text = "Aplicar portada a..."
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(60, 40, 60, 10)
            }

            // CHECKBOX "SELECCIONAR TODOS"
            val cbSelectAll = CheckBox(this).apply {
                text = "Seleccionar todos"
                isChecked = true
                setTextColor(Color.WHITE)
                textSize = 20f
                buttonTintList = tealStateList
                background = hoverDrawable.constantState?.newDrawable()
                setPadding(20, 20, 20, 20)
            }

            // LISTVIEW Y ADAPTADOR
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, itemsAsArray) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    (v.findViewById<View>(android.R.id.text1) as TextView).apply {
                        setTextColor(Color.WHITE)
                        textSize = 18f
                    }
                    if (v is android.widget.CheckedTextView) v.checkMarkTintList = tealStateList
                    return v
                }
            }

            val listView = ListView(this).apply {
                choiceMode = ListView.CHOICE_MODE_MULTIPLE
                this.adapter = adapter
                selector = hoverDrawable
                // ESTADO INICIAL: Bloqueado por el "Seleccionar todos"
                isEnabled = false
                alpha = 0.5f
                isFocusable = false
            }

            // LÓGICA DE CONTROL (Bloqueo/Desbloqueo)
            cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // 1. Si se tilda: Seleccionamos TODOS y bloqueamos la lista
                    for (i in itemsAsArray.indices) {
                        listView.setItemChecked(i, true)
                    }
                    listView.isEnabled = false
                    listView.alpha = 0.5f
                    listView.isFocusable = false
                } else {
                    // 2. Si se destilda: DESELECCIONAMOS TODO y habilitamos la lista
                    // Esto obliga al usuario a elegir uno por uno desde cero
                    for (i in itemsAsArray.indices) {
                        listView.setItemChecked(i, false)
                    }
                    listView.isEnabled = true
                    listView.alpha = 1.0f
                    listView.isFocusable = true

                    // Opcional: Mandar el foco a la lista para que el usuario empiece a elegir
                    listView.requestFocus()
                }
            }

            root.addView(cbSelectAll)
            val listParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density * 200).toInt())
            root.addView(listView, listParams)

            // ESTADO INICIAL (Al abrir el diálogo)
            for (i in itemsAsArray.indices) {
                listView.setItemChecked(i, true)
            }

            // DIÁLOGO FINAL
            val dialog = AlertDialog.Builder(this)
                .setCustomTitle(customTitle)
                .setView(root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("CONFIRMAR") { _, _ ->
                    val finalSelection = mutableListOf<String>()

                    // Si el master está tildado, usamos todos. Si no, recorremos la lista.
                    if (cbSelectAll.isChecked) {
                        finalSelection.addAll(allFiles)
                    } else {
                        for (i in itemsAsArray.indices) {
                            if (listView.isItemChecked(i)) {
                                finalSelection.add(itemsAsArray[i])
                            }
                        }
                    }

                    if (finalSelection.isEmpty()) {
                        Toast.makeText(this, "Tenes que seleccionar algo para actualizar portadas", Toast.LENGTH_SHORT).show()
                    } else {
                        executeFinalUpdate(imageUrl, finalSelection)
                    }
                }.create()

            // Estilos de botones y fondo del diálogo
            dialog.setOnShowListener {
                val d = it as AlertDialog
                d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colorBg))

                val btnPos = d.getButton(AlertDialog.BUTTON_POSITIVE)
                val btnNeg = d.getButton(AlertDialog.BUTTON_NEGATIVE)

                listOf(btnPos, btnNeg).forEach { btn ->
                    btn.setTextColor(colorTeal)
                    btn.setPadding(30, 0, 30, 0)
                    btn.setOnFocusChangeListener { v, hasFocus ->
                        v.setBackgroundColor(if (hasFocus) colorHover else Color.TRANSPARENT)
                    }
                }
                cbSelectAll.requestFocus()
            }

            dialog.show()
        }
    }

    private fun executeFinalUpdate(imageUrl: String, targetJsons: List<String>) {
        setLoading(true, "Escribiendo cambios...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folder = File(filesDir, "json_imports")
                targetJsons.forEach { fileName ->
                    val file = File(folder, fileName)
                    if (file.exists()) {
                        val content = file.readText()
                        val updated = content.replace(Regex("\"cardImageUrl\"\\s*:\\s*\".*?\""), "\"cardImageUrl\": \"$imageUrl\"")
                        file.writeText(updated)
                    }
                }
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setLoading(false) }
            }
        }
    }


    fun setLoading(isLoading: Boolean, text: String = "Cargando...") {
        runOnUiThread {
            if (isLoading) {
                binding.tvStatus.text = text
                binding.loadingLayout.visibility = View.VISIBLE
            } else {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    private fun isTvDevice(): Boolean {
        val cfg = resources.configuration
        val pm = packageManager

        val uiModeType =
            cfg.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        val isTelevision =
            uiModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

        val hasLeanback =
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                    pm.hasSystemFeature("android.software.leanback")

        val hasTelevisionFeature =
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        val noTouch =
            cfg.touchscreen == android.content.res.Configuration.TOUCHSCREEN_NOTOUCH
        val dpad =
            cfg.navigation == android.content.res.Configuration.NAVIGATION_DPAD

        return isTelevision || hasLeanback || hasTelevisionFeature || (noTouch && dpad)
    }

    // --- ADAPTER ---
    inner class ImageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val urls = mutableListOf<String>()

        fun updateList(newUrls: List<String>) {
            urls.clear()
            urls.addAll(newUrls)
            notifyDataSetChanged()
        }

        fun addUrls(newUrls: List<String>) {
            val lastPos = urls.size
            urls.addAll(newUrls)
            notifyItemRangeInserted(lastPos, newUrls.size)
        }

        override fun getItemCount() = if (urls.isEmpty()) 0 else urls.size + 1
        override fun getItemViewType(p: Int) = if (p < urls.size) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val cardView = androidx.cardview.widget.CardView(parent.context).apply {
                    // Mantenemos la altura fija para que la grilla sea simétrica
                    val heightPx = (resources.displayMetrics.density * 220).toInt()
                    layoutParams = GridLayoutManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        heightPx
                    ).apply { setMargins(8, 8, 8, 8) }

                    radius = 12f
                    cardElevation = 4f
                    // Fondo para bordes de la card
                    setCardBackgroundColor(Color.parseColor("#241F4C"))
                    isClickable = true
                    isFocusable = true

                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    foreground = context.getDrawable(outValue.resourceId)
                }

                val img = ImageView(parent.context).apply {
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                    // CAMBIO CLAVE: FIT_CENTER asegura que la imagen se vea COMPLETA
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                cardView.addView(img)
                ImgViewHolder(cardView)
            } else {
                val btn = Button(parent.context).apply {
                    layoutParams = GridLayoutManager.LayoutParams(-1, -2).apply { setMargins(20, 20, 20, 40) }
                    text = "CARGAR MÁS"
                    setBackgroundColor(Color.parseColor("#A32877"))
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                LoadMoreViewHolder(btn)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ImgViewHolder) {
                val url = urls[position]
                val card = holder.itemView as androidx.cardview.widget.CardView
                val img = card.getChildAt(0) as ImageView

                Glide.with(img.context)
                    .load(url)
                    // CAMBIO CLAVE: .fitCenter() en Glide para mantener el ratio original
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(img)

                card.setOnClickListener { processImageSelection(url) }
            } else if (holder is LoadMoreViewHolder) {
                holder.itemView.setOnClickListener { loadMoreMobile() }
            }
        }
    }

    inner class ImgViewHolder(v: View) : RecyclerView.ViewHolder(v)
    inner class LoadMoreViewHolder(v: View) : RecyclerView.ViewHolder(v)
}
