package com.megamarket.picker.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Codifica el contenido simulado que entrega la API en un QR visual (solo presentación). */
object QrBitmap {

    /** Matriz QR pura (sin Android) para poder verificarla en pruebas JVM. */
    fun crearMatriz(contenido: String, tamanoPx: Int = TAMANO_POR_DEFECTO): BitMatrix? {
        if (contenido.isBlank() || tamanoPx <= 0) return null
        return try {
            QRCodeWriter().encode(
                contenido,
                BarcodeFormat.QR_CODE,
                tamanoPx,
                tamanoPx,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 1
                )
            )
        } catch (_: WriterException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun generar(contenido: String, tamanoPx: Int = TAMANO_POR_DEFECTO): Bitmap? {
        val matriz = crearMatriz(contenido, tamanoPx) ?: return null
        val ancho = matriz.width
        val alto = matriz.height
        val pixeles = IntArray(ancho * alto)
        for (y in 0 until alto) {
            val fila = y * ancho
            for (x in 0 until ancho) {
                pixeles[fila + x] = if (matriz[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888)
    }

    const val TAMANO_POR_DEFECTO = 512
}
