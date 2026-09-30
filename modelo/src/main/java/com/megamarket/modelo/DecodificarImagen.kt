package com.megamarket.modelo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream

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
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size, opciones) ?: return null
    return bitmap.orientar(this)
}

private fun Bitmap.orientar(origen: ByteArray): Bitmap {
    val grados = runCatching {
        val exif = ExifInterface(ByteArrayInputStream(origen))
        when (
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (grados == 0f) return this
    val rotado = runCatching {
        Bitmap.createBitmap(
            this,
            0,
            0,
            width,
            height,
            Matrix().apply { postRotate(grados) },
            true
        )
    }.getOrNull() ?: return this
    if (rotado != this) recycle()
    return rotado
}
