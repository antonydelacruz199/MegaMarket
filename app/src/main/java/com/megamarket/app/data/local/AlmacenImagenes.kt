package com.megamarket.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.megamarket.modelo.decodificarImagen
import java.io.File
import java.io.FileOutputStream

object AlmacenImagenes {
    private const val CARPETA = "imagenes"

    fun guardar(contexto: Context, origen: Uri): String? {
        val bitmap = bitmap(contexto, origen, 1280) ?: return null
        val nombre = "${System.currentTimeMillis()}.jpg"
        val destino = File(directorio(contexto), nombre)
        return try {
            FileOutputStream(destino).use { salida ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, salida)) {
                    destino.delete()
                    return null
                }
            }
            nombre
        } catch (_: Exception) {
            destino.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun eliminar(contexto: Context, clave: String) {
        archivo(contexto, clave)?.delete()
    }

    fun bitmap(contexto: Context, clave: String, lado: Int): Bitmap? {
        val archivo = archivo(contexto, clave) ?: return null
        return runCatching { archivo.readBytes().decodificarImagen(lado) }.getOrNull()
    }

    fun bitmap(contexto: Context, origen: Uri, lado: Int): Bitmap? {
        return runCatching {
            contexto.contentResolver.openInputStream(origen)?.use { entrada ->
                entrada.readBytes().decodificarImagen(lado)
            }
        }.getOrNull()
    }

    fun archivo(contexto: Context, clave: String): File? {
        if (!clave.matches(Regex("\\d+\\.jpg"))) return null
        val archivo = File(directorio(contexto), clave)
        return archivo.takeIf { it.isFile }
    }

    private fun directorio(contexto: Context): File {
        return File(contexto.filesDir, CARPETA).apply { mkdirs() }
    }
}
