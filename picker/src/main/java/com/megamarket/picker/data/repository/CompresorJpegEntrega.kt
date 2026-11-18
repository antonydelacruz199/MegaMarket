package com.megamarket.picker.data.repository

import android.graphics.Bitmap
import com.megamarket.modelo.decodificarImagen
import java.io.ByteArrayOutputStream

/**
 * Reduce la foto a JPEG dentro del tope que impone la API.
 * El resultado vive solo en memoria hasta el POST.
 */
class CompresorJpegEntrega : CompresorFoto {

    override fun jpeg(origen: ByteArray, limiteBytes: Int): ByteArray? {
        if (origen.isEmpty() || limiteBytes < 1024) return null
        if (origen.size <= limiteBytes && esJpeg(origen)) return origen
        var lado = 1600
        var calidad = 82
        repeat(6) {
            val bitmap = origen.decodificarImagen(lado) ?: return null
            try {
                val salida = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)) return null
                val bytes = salida.toByteArray()
                if (bytes.size in 1..limiteBytes && esJpeg(bytes)) return bytes
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            if (calidad > 45) {
                calidad -= 15
            } else {
                lado = (lado * 3) / 4
            }
            if (lado < 320) return null
        }
        return null
    }
}

internal fun esJpeg(bytes: ByteArray): Boolean =
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
