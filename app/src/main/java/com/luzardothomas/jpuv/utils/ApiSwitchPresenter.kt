package com.luzardothomas.jpuv.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Switch
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.luzardothomas.jpuv.R

/**
 * Gestión del switch para usar o no la API
 */

class ApiSwitchPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context

        val switchApi = Switch(context).apply {

            // Replicamos las propiedades de tu XML de mobile
            text = "API"
            setTextColor(Color.WHITE)
            textSize = 16f // En mobile tenías 14sp, puedes ajustarlo

            // Replicamos el thumbTint y trackTint
            thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.selected_background))
            trackTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.selected_background))
            switchPadding = 30

            // Le damos la forma de "Píldora"
            setBackgroundResource(R.drawable.bg_pill)
            setPadding(50, 20, 50, 20)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 20, 0, 40)
            }

            // Lo hacemos navegable para el control remoto
            isFocusable = true
            isFocusableInTouchMode = true

            // Mantenemos el cambio de color de la letra al enfocar
            setOnFocusChangeListener { _, hasFocus ->
                setTextColor(if (hasFocus) Color.CYAN else Color.WHITE)
            }
        }

        return ViewHolder(switchApi)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val switchApi = viewHolder.view as Switch

        // Quitamos el listener temporalmente (evita falsos positivos al hacer scroll en Leanback)
        switchApi.setOnCheckedChangeListener(null)

        val prefs = switchApi.context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Pintar estado inicial sin disparar el listener (Tu lógica mobile)
        val isApiEnabled = prefs.getBoolean("USE_API", true)
        switchApi.isChecked = isApiEnabled

        // Lógica de cambio con la animación nativa de deslizamiento (Tu lógica mobile)
        switchApi.setOnCheckedChangeListener { _, isChecked ->
            // Guardamos el nuevo estado
            prefs.edit().putBoolean("USE_API", isChecked).apply()
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val switchApi = viewHolder.view as Switch
        switchApi.setOnCheckedChangeListener(null)
    }
}