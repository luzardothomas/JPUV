package com.luzardothomas.jpuv.utils

import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.luzardothomas.jpuv.R
import kotlin.math.roundToInt

/**
 *  Gestionador de las cards para TV del buscador de covers para actualizar
 */

class ImageCardPresenter : Presenter() {

    private var cardWidth = 0
    private var cardHeight = 0
    private val COLUMNS_COUNT = 8

    // Proporción (Alto / Ancho). 325/190 = 1.71
    private val ASPECT_RATIO = 325f / 190f

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context

        if (cardWidth == 0) {
            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels

            // Espacio horizontal que queremos "perder" entre carta y carta (márgenes intermedios)
            val spaceBetweenCards = 20

            // Dividimos el ancho BRUTO de la pantalla entre 8
            val rawColumnWidth = screenWidth / COLUMNS_COUNT

            // Restamos el espacio entre cartas al ancho de la columna para obtener el ancho de la imagen
            cardWidth = rawColumnWidth - spaceBetweenCards

            // Calculamos alto proporcional
            cardHeight = (cardWidth * ASPECT_RATIO).roundToInt()

            Log.d("TV_SIZE", "Ancho Pantalla: $screenWidth | Ancho Carta: $cardWidth | Alto Carta: $cardHeight")
        }

        val cardView = FrameLayout(context).apply {
            val params = FrameLayout.LayoutParams(cardWidth, cardHeight)

            // Márgenes simétricos para centrar la carta en su "columna imaginaria"
            val sideMargin = 10
            params.setMargins(sideMargin, 15, sideMargin, 15)

            params.gravity = Gravity.CENTER
            layoutParams = params

            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.parseColor("#222222"))

            // Lógica Inteligente de Foco
            setOnFocusChangeListener { v, hasFocus ->
                val imageView = (v as FrameLayout).getChildAt(0) as ImageView
                val scaleUp = 1.12f

                if (hasFocus) {
                    // Determinamos dónde está la carta en la pantalla para saber hacia dónde crecer
                    val location = IntArray(2)
                    v.getLocationOnScreen(location)
                    val xOnScreen = location[0]
                    val viewWidth = v.width
                    val screenWidth = context.resources.displayMetrics.widthPixels

                    // Umbral de detección (si está a menos de 50px del borde, es borde)
                    val edgeThreshold = 50

                    when {
                        // Está pegado a la IZQUIERDA -> Pivote a la izquierda (0)
                        xOnScreen < edgeThreshold -> {
                            v.pivotX = 0f
                        }
                        // Está pegado a la DERECHA -> Pivote a la derecha (width)
                        (xOnScreen + viewWidth) > (screenWidth - edgeThreshold) -> {
                            v.pivotX = viewWidth.toFloat()
                        }
                        // Está en el CENTRO -> Pivote al centro
                        else -> {
                            v.pivotX = viewWidth / 2f
                        }
                    }
                    // El eje Y siempre crece desde el centro
                    v.pivotY = v.height / 2f
                    // -------------------------------------

                    val colorReal = androidx.core.content.ContextCompat.getColor(v.context, R.color.fastlane_background)

                    if (v.tag == "LOAD_MORE") {
                        v.setBackgroundColor(colorReal)
                        imageView.setColorFilter(Color.BLACK)
                    } else {
                        v.setBackgroundColor(colorReal)
                    }

                    v.animate()
                        .scaleX(scaleUp)
                        .scaleY(scaleUp)
                        .setDuration(150)
                        .start()

                    v.elevation = 20f

                } else {
                    // AL PERDER FOCO:
                    // Es importante resetear el pivot al centro para que la animación de vuelta se vea natural
                    // o dejarlo como estaba, pero generalmente volver al centro evita saltos raros si scrolleas rápido.

                    // Restaurar colores
                    if (v.tag == "LOAD_MORE") {
                        v.setBackgroundColor(Color.parseColor("#444444"))
                        val colorReal = androidx.core.content.ContextCompat.getColor(v.context, R.color.fastlane_background)
                        imageView.setColorFilter(colorReal)
                    } else {
                        v.setBackgroundColor(Color.parseColor("#222222"))
                    }

                    // Animar de vuelta a tamaño normal
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()

                    v.elevation = 0f

                    // Resetear pivot al terminar la animación si notas comportamientos raros,
                    // aunque usualmente no es necesario porque al volver a scale 1.0 el pivot no afecta visualmente.
                    v.pivotX = v.width / 2f
                }
            }
        }

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        cardView.addView(imageView)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val container = viewHolder.view as FrameLayout
        val imageView = container.getChildAt(0) as ImageView

        // Limpieza para reciclaje
        Glide.with(imageView.context).clear(imageView)
        imageView.setImageDrawable(null)
        imageView.clearColorFilter()
        container.setBackgroundColor(Color.TRANSPARENT)

        val itemString = item as? String ?: ""

        if (itemString == "ACTION_LOAD_MORE") {
            container.tag = "LOAD_MORE"
            container.setBackgroundColor(Color.parseColor("#444444"))
            imageView.setImageResource(android.R.drawable.ic_input_add)
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE

            // Reset listener específico para LOAD_MORE
            val iconColor = if (container.hasFocus()) Color.BLACK else R.color.fastlane_background
            imageView.setColorFilter(iconColor)

        } else {
            // --- IMAGEN NORMAL ---
            container.tag = "IMAGE"
            container.setBackgroundColor(Color.parseColor("#222222"))

            // Usamos FIT_XY para asegurar que rellene el rectángulo calculado al 100%
            imageView.scaleType = ImageView.ScaleType.FIT_XY

            Glide.with(imageView.context)
                .load(itemString)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .override((cardWidth * 1.5).toInt(), (cardHeight * 1.5).toInt())
                .fitCenter()
                .into(imageView)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val container = viewHolder.view as FrameLayout
        val imageView = container.getChildAt(0) as ImageView
        try {
            Glide.with(imageView.context).clear(imageView)
        } catch (e: Exception) {}
        imageView.setImageDrawable(null)
    }
}