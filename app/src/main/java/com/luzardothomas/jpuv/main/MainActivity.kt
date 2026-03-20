package com.luzardothomas.jpuv.main

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import android.view.View
import com.luzardothomas.jpuv.R
import com.luzardothomas.jpuv.mobile.MobileMainFragment
import com.luzardothomas.jpuv.server.BackgroundServer
import com.luzardothomas.jpuv.tv.MainFragment

/**
 * Clase principal de la aplicación.
 */
class MainActivity : FragmentActivity() {

    private lateinit var server: BackgroundServer
    private var backPressedTime: Long = 0
    private var backToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Salir con doble retroceso
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    backToast?.cancel()
                    finish()
                } else {
                    backToast = Toast.makeText(baseContext, "Para salir vuelva a retroceder", Toast.LENGTH_SHORT)
                    backToast?.show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        startServer()

        if (savedInstanceState == null) {
            val splashView = findViewById<View>(R.id.splash_view)

            // Decidimos qué fragmento cargar
            val fragment = if (isTvDevice()) {
                MainFragment()
            } else {
                MobileMainFragment()
            }

            // Ejecutamos la transacción del fragmento
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .commit()

            Handler(Looper.getMainLooper()).postDelayed({
                // Verificamos que la activity siga viva antes de animar
                if (!isFinishing && !isDestroyed) {
                    splashView.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            splashView.visibility = View.GONE
                        }
                        .start()
                }
            }, 2000)
        }

        if (!isTvDevice()) {
            enterImmersiveMode()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isTvDevice()) {
            enterImmersiveMode()
        }
    }

    // Detecta si es un dispositivo TV
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

    // Entramos en modo inmersivo

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Sirve para arrancar el servidor

    private fun startServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                server = BackgroundServer()
                server.start()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al iniciar servidor: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        try { server.stop() } catch (_: Exception) {}
    }
}
