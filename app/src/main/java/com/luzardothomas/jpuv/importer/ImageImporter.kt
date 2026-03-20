package com.luzardothomas.jpuv.importer

import android.util.Log

import com.luzardothomas.jpuv.utils.UnsafeOkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

/**
 * Importador de imágenes desde Bing
 */
object ImageImporter {
    private const val TAG = "ImageImporter"

    suspend fun searchImages(query: String, first: Int = 1): List<String> {
        val results = ArrayList<String>()
        try {
            val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()

            // &first=$first indica a Bing desde qué resultado empezar a mostrar
            val urlStr = "https://www.bing.com/images/search?q=${query.replace(" ", "+")}+poster&qft=+filterui:aspect-tall&first=$first"

            Log.d(TAG, "Buscando Posters en Bing (Inicio: $first): $urlStr")

            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .build()

            // Ejecutamos en un hilo de IO
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body?.string() ?: ""
            val p = Pattern.compile("https?://[^\"'\\s,]*bing[^\"'\\s,]*/th\\?id=[^\"'\\s,]*")
            val m = p.matcher(html)

            while (m.find()) {
                val link = m.group().replace("\\", "")
                if (!link.isNullOrEmpty() && !results.contains(link)) {
                    if (link.length > 20) results.add(link)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error buscando: ${e.message}")
        }
        // Retornamos hasta 100 por tanda
        return results.distinct().take(100)
    }
}
