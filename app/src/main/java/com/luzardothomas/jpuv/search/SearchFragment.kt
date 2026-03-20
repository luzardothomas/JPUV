package com.luzardothomas.jpuv.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import com.luzardothomas.jpuv.tv.CardPresenter
import com.luzardothomas.jpuv.utils.ImportedJson
import com.luzardothomas.jpuv.utils.JsonDataManager
import com.luzardothomas.jpuv.utils.Movie
import com.luzardothomas.jpuv.utils.prettyTitle
import com.luzardothomas.jpuv.video.DetailsActivity
import java.util.Locale
import java.util.HashSet

/**
 *  Fragmento principal del buscador de colecciones (JSONs) series y películas
 */

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val handler = Handler(Looper.getMainLooper())

    private val jsonDataManager = JsonDataManager()
    private val cardPresenter = CardPresenter()

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    private var allImported: List<ImportedJson> = emptyList()

    // ✅ DEDUPE
    private var lastScheduledQuery: String = ""
    private var lastExecutedQuery: String = ""

    private val speechLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                setSearchQuery(results[0], true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jsonDataManager.loadData(requireContext())
        allImported = jsonDataManager.getImportedJsons()
        setSearchResultProvider(this)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchOrb = view.findViewById<View>(androidx.leanback.R.id.lb_search_bar_speech_orb)
        searchOrb?.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Decí qué querés buscar")
            }
            try {
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                // Manejar si no hay reconocimiento de voz disponible
            }
        }

        setOnItemViewClickedListener { itemViewHolder, item, _, row ->
            val movie = item as? Movie ?: return@setOnItemViewClickedListener

            val listRow = row as? ListRow
            val adapter = listRow?.adapter

            val playlist = ArrayList<Movie>()
            if (adapter != null) {
                for (i in 0 until adapter.size()) {
                    val obj = adapter.get(i)
                    if (obj is Movie) playlist.add(obj)
                }
            }

            val index = playlist.indexOfFirst { it.videoUrl == movie.videoUrl }
                .let { if (it >= 0) it else 0 }

            val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                putExtra(DetailsActivity.MOVIE, movie)
                if (playlist.size > 1) {
                    putExtra(DetailsActivity.EXTRA_PLAYLIST, playlist)
                    putExtra(DetailsActivity.EXTRA_INDEX, index)
                }
            }

            val cardView = itemViewHolder.view as? ImageCardView
            val shared = cardView?.mainImageView

            if (shared != null) {
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    shared,
                    DetailsActivity.SHARED_ELEMENT_NAME
                )
                // Aquí podrías usar el nuevo launcher si DetailsActivity devolviera algo,
                // pero startActivity simple está bien si no necesitas resultado.
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        handler.post {
            Log.e(TAG, "FOCUSDBG_SEARCH onResume() -> try focusLastPlayedIfAny")
            focusLastPlayedIfAny()
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String?): Boolean {
        // No hacemos nada mientras se escribe
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        val q = query.orEmpty().trim()
        if (q.isNotBlank()) {
            scheduleSearch(q)
        }
        return true
    }

    private fun scheduleSearch(raw: String) {
        val q = normalize(raw)

        if (q == lastScheduledQuery) return
        lastScheduledQuery = q

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ performSearch(raw) }, 180)
    }

    private fun performSearch(raw: String) {
        val q = normalize(raw)
        if (q == lastExecutedQuery) return
        lastExecutedQuery = q

        rowsAdapter.clear()
        if (q.isBlank()) return

        val coverByUrl = buildNonRandomCoverByUrl()
        val out = mutableListOf<Movie>()

        // Set para rastrear URLs ya añadidas
        val seenUrls = HashSet<String>()

        for (json in allImported) {
            val jsonTitle = safePrettyTitle(json.fileName)
            val jsonMatch = normalize(jsonTitle).contains(q)

            for (v in json.videos) {
                val titleMatch = normalize(v.title).contains(q)

                if (titleMatch || jsonMatch) {

                    val url = v.videoUrl.trim().orEmpty()

                    // Evita repetidos de random lists
                    if (url.isEmpty() || seenUrls.contains(url)) {
                        continue
                    }

                    // Marcar como visto
                    seenUrls.add(url)

                    var movie = Movie(
                        title = v.title,
                        videoUrl = v.videoUrl,
                        cardImageUrl = v.cardImageUrl,
                        backgroundImageUrl = v.backgroundImageUrl,
                        skipToSecond = v.skip,
                        delaySkip = v.delaySkip,
                        description = jsonTitle
                    )

                    if (isDicesCover(movie.cardImageUrl)) {
                        val realCover = coverByUrl[movie.videoUrl?.trim().orEmpty()]
                        if (!realCover.isNullOrBlank()) {
                            movie = movie.copy(cardImageUrl = realCover)
                        }
                    }

                    out.add(movie)
                }
            }
        }

        // Primero coincidencia en título, luego JSON, luego por nombre
        val sorted = out.sortedWith(
            compareByDescending<Movie> { normalize(it.title.orEmpty()).contains(q) }
                .thenBy { it.description.orEmpty() }
                .thenBy { it.title.orEmpty() }
        )

        val itemsPerRow = 6
        sorted.chunked(itemsPerRow).forEach { chunk ->
            val rowAdapter = ArrayObjectAdapter(cardPresenter)
            chunk.forEach { rowAdapter.add(it) }
            rowsAdapter.add(ListRow(null, rowAdapter))
        }

        // ✅ reintentar foco al último reproducido
        handler.post {
            focusLastPlayedIfAny()
        }
    }

    private fun readLastPlayedUrl(): String? {
        val u = requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PLAYED, null)
            ?.trim()

        Log.e(TAG, "FOCUSDBG_SEARCH readLastPlayedUrl=$u")
        return u?.takeIf { it.isNotEmpty() }
    }

    private fun focusLastPlayedIfAny(): Boolean {
        Log.e(TAG, "FOCUSDBG_SEARCH ENTER focusLastPlayedIfAny()")

        val lastUrl = readLastPlayedUrl() ?: return false

        var targetRowIndex = -1
        var targetColIndex = -1

        for (r in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(r) as? ListRow ?: continue
            val rowAdapter = row.adapter ?: continue

            for (c in 0 until rowAdapter.size()) {
                val m = rowAdapter.get(c) as? Movie ?: continue
                if (m.videoUrl == lastUrl) {
                    targetRowIndex = r
                    targetColIndex = c
                    break
                }
            }
            if (targetRowIndex >= 0) break
        }

        if (targetRowIndex < 0 || targetColIndex < 0) {
            Log.e(TAG, "FOCUSDBG_SEARCH NOT FOUND in results url=$lastUrl")
            return false
        }

        Log.e(TAG, "FOCUSDBG_SEARCH FOUND row=$targetRowIndex col=$targetColIndex url=$lastUrl")

        val rowsFrag = rowsSupportFragment
        if (rowsFrag == null) {
            Log.e(TAG, "FOCUSDBG_SEARCH rowsSupportFragment == null (aun no creado)")
            return false
        }

        // ✅ EXACTO como MainFragment: setSelectedPosition con ViewHolderTask
        rowsFrag.setSelectedPosition(targetRowIndex, false, object : Presenter.ViewHolderTask() {
            override fun run(holder: Presenter.ViewHolder?) {
                val rowView = holder?.view
                if (rowView == null) {
                    Log.e(TAG, "FOCUSDBG_SEARCH holder==null en task")
                    return
                }

                val rowContent = rowView.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
                if (rowContent == null) {
                    Log.e(TAG, "FOCUSDBG_SEARCH row_content==null en task")
                    return
                }

                rowContent.scrollToPosition(targetColIndex)

                rowContent.post {
                    rowContent.setSelectedPosition(targetColIndex)
                    rowContent.requestFocus()

                    Log.e(
                        TAG,
                        "FOCUSDBG_SEARCH APPLIED row=$targetRowIndex col=$targetColIndex selected=${rowContent.selectedPosition}"
                    )
                }
            }
        })

        return true
    }

    private fun isRandomJsonName(name: String): Boolean =
        name.trim().uppercase(Locale.ROOT).startsWith("RANDOM")

    private fun isDicesCover(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        return u.contains("/drawable/dices")
    }

    private fun buildNonRandomCoverByUrl(): Map<String, String> {
        val nonRandom = allImported.filterNot { isRandomJsonName(it.fileName) }

        val map = HashMap<String, String>(nonRandom.size * 20)

        nonRandom.forEach { ij ->
            ij.videos.forEach { v ->
                val url = v.videoUrl.trim().orEmpty()
                val cover = v.cardImageUrl.trim().orEmpty()
                if (url.isNotEmpty() && cover.isNotEmpty()) {
                    map.putIfAbsent(url, cover)
                }
            }
        }
        return map
    }

    private fun normalize(s: String): String =
        s.trim()
            .lowercase(Locale.getDefault())
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")

    private fun safePrettyTitle(fileName: String): String {
        return try {
            prettyTitle(fileName)
        } catch (_: Throwable) {
            fileName.removeSuffix(".json")
                .replace("_", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    companion object {
        private const val TAG = "SearchFragment"
        private const val PREFS_NAME = "jpuv_prefs"
        private const val KEY_LAST_PLAYED = "LAST_PLAYED_VIDEO_URL"
    }
}
