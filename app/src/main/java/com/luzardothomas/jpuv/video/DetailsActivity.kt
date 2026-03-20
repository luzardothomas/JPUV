package com.luzardothomas.jpuv.video

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.luzardothomas.jpuv.R
import com.luzardothomas.jpuv.main.MainActivity
import com.luzardothomas.jpuv.utils.Movie

/**
 * Controla el comportamiento del clic de una carta para ver un video
 * Controla que la collección tenga el anidamiento correcto
 * Controla que la playlist random tenga el anidamiento circular
 *
 */

class DetailsActivity : FragmentActivity() {

    private val TAG = "DetailsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        if (savedInstanceState != null) return

        // Movie con IntentCompat (Elimina deprecated y casteo inseguro)
        val movie = IntentCompat.getSerializableExtra(intent, MOVIE, Movie::class.java)

        if (movie == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Playlist con IntentCompat (Especificando el tipo ArrayList)
        val playlistName = intent.getStringExtra("EXTRA_PLAYLIST_NAME")
        @Suppress("UNCHECKED_CAST")
        val playlist = IntentCompat.getSerializableExtra(intent, EXTRA_PLAYLIST, ArrayList::class.java) as? ArrayList<Movie>
        val index = intent.getIntExtra(EXTRA_INDEX, 0)

        Log.e(TAG, "INTENT movie=${movie.videoUrl} playlistSize=${playlist?.size} index=$index")

        val frag = PlaybackVideoFragment().apply {
            arguments = Bundle().apply {
                putSerializable("movie", movie)

                if (!playlistName.isNullOrEmpty()) {
                    putString("playlist_name", playlistName)
                    putInt("index", index)
                }
                else if (!playlist.isNullOrEmpty()) {
                    putSerializable("playlist", playlist)
                    // Usamos index.coerceIn para evitar IndexOutOfBoundsException
                    putInt("index", index.coerceIn(0, playlist.lastIndex))
                }
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.details_fragment, frag)
            .commit()
    }

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val MOVIE = "Movie"
        const val EXTRA_PLAYLIST = "wo_playlist"
        const val EXTRA_INDEX = "wo_index"
    }
}
