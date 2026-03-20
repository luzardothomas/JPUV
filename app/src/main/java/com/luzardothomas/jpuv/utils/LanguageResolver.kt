package com.luzardothomas.jpuv.utils

import android.util.Log
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.MediaPlayer.TrackDescription
import java.util.Locale

/**
 *  Controla los tracks y subtítulos de los videos
 */

object LanguageResolver {
    private const val TAG = "LanguageResolver"

    // Diccionario de palabras compatibles

    private val rootWords = mapOf(
        "spa" to listOf("spanish", "esp", "espanol", "español", "castellano", "castilian", "latino", "spa"),
        "eng" to listOf("english", "ing", "ingles", "inglés", "eng", "en"),
        "jpn" to listOf("japanese", "jap", "japones", "japonés", "jpn", "jp", "日本語"),
        "chi" to listOf("chinese", "chino", "chi", "zho", "mandarin", "cantonese", "han", "中文"),
        "kor" to listOf("korean", "coreano", "kor", "ko", "hangul", "한국어"),
        "por" to listOf("portuguese", "portugues", "portugués", "por", "pt", "br", "brazilian"),
        "fra" to listOf("french", "francais", "français", "fra", "fr"),
        "ger" to listOf("german", "deutsch", "deu", "ger", "de"),
        "ita" to listOf("italian", "italiano", "ita", "it"),
        "rus" to listOf("russian", "ruso", "rus", "ru"),
        "ara" to listOf("arabic", "arabe", "árabe", "ara", "ar"),
        "tha" to listOf("thai", "tailandes", "tailandés", "tha", "th", "ไทย"),
        "vie" to listOf("vietnamese", "vietnamita", "vie", "vi", "tiếng"),
        "ind" to listOf("indonesian", "indonesia", "ind", "id"),
        "msa" to listOf("malay", "malayo", "msa", "melayu"),
        "hin" to listOf("hindi", "hin", "hi")
    )


    // Obtiene un nombre de track legible para el usuario.

    fun getDisplayName(track: TrackDescription, type: String, index: Int): String {
        if (track.id == -1) return "Sin $type"
        val rawName = track.name ?: ""

        // Extraemos lo que hay dentro de los corchetes si existe (ej: [Spanish])
        val bracketContent = Regex("\\[(.*?)\\]").find(rawName)?.groupValues?.get(1)

        // Limpiamos el nombre base (quitamos corchetes, paréntesis y guiones)
        var cleanName = rawName
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace("-", "")
            .trim()

        // Si el nombre base es "Track X" pero tenemos un idioma en los corchetes, preferimos el idioma.
        val finalName = if ((cleanName.lowercase().contains("track") || cleanName.isBlank()) && !bracketContent.isNullOrBlank()) {
            bracketContent
        } else {
            // Si el nombre es descriptivo (ej: "Latino Broadcast"), quitamos palabras técnicas
            cleanName.replace(Regex("(?i)stream|track|audio|subtitles?"), "").trim()
        }

        return if (finalName.isBlank() || finalName.matches(Regex("\\d+"))) {
            "$type ${index + 1}"
        } else {
            finalName.replaceFirstChar { it.uppercase() }
        }
    }


     // Convierte cualquier nombre de track en una "ID de Idioma" de 3 letras.
     //Ejemplo: "Latin American Spanish" -> "spa"
     // Ejemplo: "Tiếng Việt" -> "vie"

    fun simplify(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val low = name.lowercase(Locale.ROOT)

        for ((root, synonyms) in rootWords) {
            if (synonyms.any { low.contains(it) }) return root
        }

        val onlyLetters = low.replace(Regex("[^a-z]"), "").trim()
        return if (onlyLetters.length >= 3) onlyLetters.take(3) else onlyLetters
    }

    fun getValidTracks(player: MediaPlayer?, isAudio: Boolean): List<TrackDescription> {
        val tracks = if (isAudio) player?.audioTracks else player?.spuTracks
        if (tracks == null) return emptyList()

        return tracks.filter { track ->
            if (!isAudio && track.id == -1) return@filter true
            val name = track.name?.lowercase() ?: ""
            // Filtrar forzados y basura técnica (PGS/DVB)
            val isWrong = name.contains("forced") || name.contains("forzado") || name.contains("sign") || name.contains("karaoke")
            track.id != -1 && !isWrong && !name.contains("pgs")
        }
    }

    fun findBestMatch(tracks: Array<TrackDescription>?, savedPref: String?): Int? {
        if (tracks == null || savedPref.isNullOrBlank() || savedPref == "disable") {
            return null
        }

        val targetRoot = simplify(savedPref)
        Log.d("DEBUG_TRACKS", "Buscando match para: '$targetRoot'")

        // Buscamos un track que:
        // 1. Coincida con la raíz (spa, jpn, etc)
        // 2. NO sea un ID nulo (-1)
        // 3. NO contenga la palabra alguna de las palabras como "forced", "forzado" o "karaoke"
        val match = tracks.firstOrNull { track ->
            val name = track.name?.lowercase() ?: ""
            val isWrong = name.contains("forced")  ||
                          name.contains("forzado") ||
                          name.contains("karaoke")


            simplify(track.name) == targetRoot && track.id != -1 && !isWrong
        }

        if (match != null) {
            Log.d("DEBUG_TRACKS", "  [!!!] MATCH VALIDO ENCONTRADO: ${match.name} (ID ${match.id})")
            return match.id
        }

        Log.d("DEBUG_TRACKS", "  [X] No se encontró track válido no-forzado para '$targetRoot'")
        return null
    }
}