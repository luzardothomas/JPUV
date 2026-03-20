package com.luzardothomas.jpuv.utils

import java.io.Serializable

/**
 *  Clase que sirve para las películas
 */
data class Movie(
    var id: Long = 0,
    var title: String? = null,
    var description: String? = null,
    var backgroundImageUrl: String? = null,
    var cardImageUrl: String? = null,
    var videoUrl: String? = null,
    var studio: String? = null,

    // ⏭ Skip intro
    var skipToSecond: Int = 0,
    var delaySkip: Int = 0
) : Serializable {

    override fun toString(): String {
        return "Movie{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", skipToSecond=" + skipToSecond +
                ", delaySkip=" + delaySkip +
                ", backgroundImageUrl='" + backgroundImageUrl + '\'' +
                ", cardImageUrl='" + cardImageUrl + '\'' +
                '}'
    }

    companion object {
        internal const val serialVersionUID = 727566175075960653L
    }
}
