package com.megamarket.modelo

import android.graphics.Bitmap
import android.graphics.BitmapFactory

fun ByteArray.decodificarImagen(ladoMaximo: Int): Bitmap? {
    if (isEmpty() || ladoMaximo <= 0) return null
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, limites)
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
    var muestra = 1
    var ancho = limites.outWidth
    var alto = limites.outHeight
    while (ancho / 2 >= ladoMaximo && alto / 2 >= ladoMaximo) {
        ancho /= 2
        alto /= 2
        muestra *= 2
    }
    val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
    return BitmapFactory.decodeByteArray(this, 0, size, opciones)
}
