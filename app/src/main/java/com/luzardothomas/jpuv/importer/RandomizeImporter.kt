package com.luzardothomas.jpuv.importer

import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import com.luzardothomas.jpuv.utils.ImportedJson
import com.luzardothomas.jpuv.utils.JsonDataManager
import com.luzardothomas.jpuv.utils.prettyTitle
import java.util.Locale
import kotlin.random.Random

/**
 * Importador de playlist random
 */
class RandomizeImporter(
    private val context: Context?,
    private val jsonDataManager: JsonDataManager
) {

    private val ctx: Context = requireNotNull(context) {
        "RandomizeImporter: context es null (crealo con requireContext())"
    }

    // =========================
    // FUNCIONES PÚBLICAS
    // =========================

    fun actionGenerateRandom(
        toast: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val all = jsonDataManager.getImportedJsons()
                .filterNot { isRandomName(it.fileName) }

            if (all.isEmpty()) {
                onError("No hay JSONs importados para generar un RANDOM.")
                return
            }

            showGenerateDialog(
                source = all,
                toast = toast,
                onDone = onDone,
                onError = onError
            )

        } catch (e: Exception) {
            onError("Error: ${e.message}")
        }
    }

    fun actionUpdateRandom(
        toast: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val randoms = jsonDataManager.getImportedJsons()
                .filter { isRandomName(it.fileName) }
                .sortedBy { it.fileName.lowercase(Locale.ROOT) }
            if (randoms.isEmpty()) {
                onError("No hay playlists RANDOM para actualizar.")
                return
            }

            val items = randoms.map { it.fileName }

            showPickOneDialog(
                title = "Actualizar playlist RANDOM",
                items = items,
                onPicked = { pickedName ->

                    val resolvedName = resolvePickedNameForUpdate(pickedName, randoms)

                    val target = randoms.firstOrNull { it.fileName == resolvedName }
                    if (target == null) {
                        onError("No se encontró esa playlist: $resolvedName")
                        return@showPickOneDialog
                    }

                    val randomCover = "android.resource://${ctx.packageName}/drawable/dices"

                    // FORZAR COVER RANDOM también en update
                    val normalized = target.videos.map { v ->
                        v.copy(cardImageUrl = randomCover)
                    }

                    val shuffled = normalized.shuffled(Random(System.nanoTime()))

                    jsonDataManager.upsertJson(ctx, target.fileName, shuffled)

                    toast("Actualizada: ${prettyTitle(target.fileName)}")
                    onDone()
                }
            )

        } catch (e: Exception) {
            onError("Error: ${e.message}")
        }
    }


    fun actionDeleteRandomPlaylists(
        toast: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val randoms = jsonDataManager.getImportedJsons()
                .filter { isRandomName(it.fileName) }
                .sortedBy { it.fileName.lowercase(Locale.ROOT) }

            if (randoms.isEmpty()) {
                onError("No hay playlists RANDOM para borrar.")
                return
            }

            val labels = randoms.map { prettyTitle(it.fileName) }.toTypedArray()

            AlertDialog.Builder(ctx)
                .setTitle("Borrar playlist RANDOM")
                .setItems(labels) { _, which ->
                    val target = randoms[which]

                    AlertDialog.Builder(ctx)
                        .setTitle("Confirmar")
                        .setMessage("¿Borrar \"${prettyTitle(target.fileName)}\"?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Borrar") { _, _ ->
                            try {
                                jsonDataManager.removeJson(ctx, target.fileName)
                                toast("Borrada: ${prettyTitle(target.fileName)}")
                                onDone()
                            } catch (e: Exception) {
                                onError("Error borrando: ${e.message}")
                            }
                        }
                        .show()
                }
                .setNegativeButton("Cerrar", null)
                .show()

        } catch (e: Exception) {
            onError("Error: ${e.message}")
        }
    }

    fun actionDeleteAllRandomPlaylists(
        toast: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val randoms = jsonDataManager.getImportedJsons().filter { isRandomName(it.fileName) }
            if (randoms.isEmpty()) {
                onError("No hay playlists RANDOM para borrar.")
                return
            }

            AlertDialog.Builder(ctx)
                .setTitle("Borrar TODAS las playlists RANDOM")
                .setMessage("Se van a borrar ${randoms.size} playlists RANDOM. ¿Confirmás?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar") { _, _ ->
                    randoms.forEach { jsonDataManager.removeJson(ctx, it.fileName) }
                    toast("Borradas ${randoms.size} playlists RANDOM.")
                    onDone()
                }
                .show()

        } catch (e: Exception) {
            onError("Error: ${e.message}")
        }
    }

    // =========================
    // DIALOGOS
    // =========================

    private fun showGenerateDialog(
        source: List<ImportedJson>,
        toast: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Orden alfabético por nombre
        val sorted = source.sortedBy { it.fileName.lowercase(Locale.ROOT) }

        // Contenedor vertical
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        // Checkboxes superiores
        val cbSelectAll = android.widget.CheckBox(ctx).apply {
            text = "Seleccionar todos"
            isChecked = true
        }

        val cbNoSkip = android.widget.CheckBox(ctx).apply {
            text = "No saltar intros"
            isChecked = true
        }

        root.addView(cbSelectAll)
        root.addView(cbNoSkip)

        // Lista con checks
        val listView = android.widget.ListView(ctx).apply {
            choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
            dividerHeight = 1
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val labels = sorted.map { it.fileName }.toTypedArray()

        val adapter = android.widget.ArrayAdapter(
            ctx,
            android.R.layout.simple_list_item_multiple_choice,
            labels
        )

        listView.adapter = adapter

        // Por defecto todos checked
        for (i in labels.indices) listView.setItemChecked(i, true)

        // Estado inicial: selectAll=true => lista deshabilitada (pero visible)
        fun applySelectAllState(selectAll: Boolean) {
            listView.isEnabled = !selectAll
            listView.alpha = if (selectAll) 0.55f else 1f
        }
        applySelectAllState(true)

        // Al desmarcar "Seleccionar todos" habilita la lista para elegir
        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                for (i in labels.indices) listView.setItemChecked(i, true)
            } else {
                for (i in labels.indices) listView.setItemChecked(i, false)
                if (labels.isNotEmpty()) {
                    listView.post { listView.setSelection(0) }
                }
            }
            applySelectAllState(isChecked)
        }

        // Usamos Weight para que el diálogo no colapse la lista
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (ctx.resources.displayMetrics.density * 420).toInt()
        ).apply {
            weight = 1f // Esto asegura que la lista use el espacio y active el scroll
            topMargin = (ctx.resources.displayMetrics.density * 14).toInt()
        }
        root.addView(listView, lp)

        AlertDialog.Builder(ctx)
            .setTitle("Generar playlist RANDOM")
            .setView(root)
            .setCancelable(true) // Asegura que no se rompa al cerrar
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Crear RANDOM") { _, _ ->
                try {
                    val selectAll = cbSelectAll.isChecked
                    val noSkipIntros = cbNoSkip.isChecked

                    val chosen = if (selectAll) {
                        sorted
                    } else {
                        val picked = mutableListOf<ImportedJson>()
                        for (i in labels.indices) {
                            if (listView.isItemChecked(i)) picked += sorted[i]
                        }
                        picked
                    }

                    if (chosen.isEmpty()) {
                        onError("No seleccionaste ningún JSON.")
                        return@setPositiveButton
                    }

                    val merged = chosen.flatMap { it.videos }
                    if (merged.isEmpty()) {
                        onError("Los JSON seleccionados no tienen videos.")
                        return@setPositiveButton
                    }

                    val randomCover = "android.resource://${ctx.packageName}/drawable/dices"

                    val normalized = if (noSkipIntros) {
                        merged.map { v ->
                            v.copy(
                                skip = 0,
                                delaySkip = 0,
                                cardImageUrl = randomCover
                            )
                        }
                    } else {
                        merged.map { v -> v.copy(cardImageUrl = randomCover) }
                    }

                    val shuffled = normalized.shuffled(Random(System.nanoTime()))
                    val playlistName = resolvePlaylistName(selectAll, chosen)
                    val finalName = ensureUniqueName(playlistName)

                    jsonDataManager.upsertJson(ctx, finalName, shuffled)

                    toast("Creada: $finalName")
                    onDone()

                } catch (e: Exception) {
                    onError("Error creando RANDOM: ${e.message}")
                }
            }
            .show()
    }

    private fun showPickOneDialog(title: String, items: List<String>, onPicked: (String) -> Unit) {
        val arr = items.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setItems(arr) { _, which -> onPicked(arr[which]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =========================
    // UTILITARIAS
    // =========================

    private fun resolvePlaylistName(selectAll: Boolean, chosen: List<ImportedJson>): String {
        if (selectAll) return ensureNextRandomCompletoName()

        val common = commonBaseNameOrNull(chosen)
        if (!common.isNullOrBlank()) {
            return "RANDOM $common"
        }

        return ensureUniqueSpecificName()
    }

    private fun ensureNextRandomCompletoName(): String {
        // Busca existentes: "RANDOM COMPLETO", "RANDOM COMPLETO 2", etc.
        val existing = jsonDataManager.getImportedJsons().map { it.fileName.trim() }

        val re = Regex("^RANDOM\\s+COMPLETO(?:\\s+(\\d+))?$", RegexOption.IGNORE_CASE)
        val usedNums = existing.mapNotNull { name ->
            val m = re.find(name) ?: return@mapNotNull null
            // si es "RANDOM COMPLETO" sin número => lo tratamos como 1
            m.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        }

        val next = (usedNums.maxOrNull() ?: 0) + 1

        // Si no existe nada todavía, devolvemos "RANDOM COMPLETO 1"
        return "RANDOM COMPLETO $next"
    }

    private fun ensureUniqueSpecificName(): String {
        val existing = jsonDataManager.getImportedJsons().map { it.fileName }

        val re = Regex("^RANDOM\\s+ESPECIFICO\\s+(\\d+)$", RegexOption.IGNORE_CASE)
        val used = existing.mapNotNull { re.find(it.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() }
        val next = (used.maxOrNull() ?: 0) + 1

        return "RANDOM ESPECIFICO $next"
    }

    private fun ensureUniqueName(base: String): String {
        if (!jsonDataManager.exists(base)) return base

        var i = 2
        while (true) {
            val candidate = "$base ($i)"
            if (!jsonDataManager.exists(candidate)) return candidate
            i++
        }
    }

    private fun isRandomName(fileName: String): Boolean {
        val n = fileName.trim().uppercase(Locale.ROOT)
        return n.startsWith("RANDOM")
    }

    private fun baseSeriesName(fileName: String): String {
        var s = prettyTitle(fileName)

        s = s.replace(Regex("(?i)\\b(season|temporada|temp)\\s*\\d+\\b"), "").trim()
        s = s.replace(Regex("(?i)\\bs\\s*\\d+\\b"), "").trim()
        s = s.replace(Regex("(?i)\\bs\\d{1,2}\\b"), "").trim()
        s = s.replace(Regex("(?i)\\b(t|temp)\\d{1,2}\\b"), "").trim()
        s = s.replace(Regex("(?i)\\b\\d{1,2}x\\d{1,2}\\b"), "").trim()

        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    private fun normalizeName(s: String): String =
        s.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun isRandomCompletoNormalized(n: String): Boolean =
        n == "RANDOM COMPLETO" || n.startsWith("RANDOM COMPLETO ")

    private fun parseCompletoN(normalized: String): Int? {
        val m = Regex("^RANDOM\\s+COMPLETO\\s+(\\d+)$").find(normalized) ?: return null
        return m.groupValues.getOrNull(1)?.toIntOrNull()
    }

    // Si el user elige "RANDOM COMPLETO" sin número, apuntamos al N=1 si existe, si no, al primero que haya.
    private fun resolvePickedNameForUpdate(pickedName: String, existing: List<ImportedJson>): String {
        val pickedNorm = normalizeName(pickedName)

        // Caso normal: existe exacto (por fileName)
        existing.firstOrNull { normalizeName(it.fileName) == pickedNorm }?.let { return it.fileName }

        // Caso legacy: "RANDOM COMPLETO" sin número => queremos "RANDOM COMPLETO 1"
        if (pickedNorm == "RANDOM COMPLETO") {
            val one = existing.firstOrNull { normalizeName(it.fileName) == "RANDOM COMPLETO 1" }
            if (one != null) return one.fileName

            // Si no existe el 1, agarrar el menor N disponible
            val minN = existing.mapNotNull { ij ->
                val n = normalizeName(ij.fileName)
                if (isRandomCompletoNormalized(n)) (parseCompletoN(n) ?: 1) to ij.fileName else null
            }.minByOrNull { it.first }?.second

            if (minN != null) return minN
        }

        // Fallback: si no se encontró, devolvemos el original (para que falle con mensaje claro)
        return pickedName
    }

    private fun commonBaseNameOrNull(chosen: List<ImportedJson>): String? {
        if (chosen.isEmpty()) return null

        val bases = chosen
            .map { baseSeriesName(it.fileName.trim()) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (bases.isEmpty()) return null

        val first = bases.first()
        return if (bases.all { it.equals(first, ignoreCase = true) }) first else null
    }
}
