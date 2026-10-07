package com.megamarket.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.megamarket.app.data.local.AlmacenImagenes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImagenRepository(
    contexto: Context
) {
    private val contexto = contexto.applicationContext

    /** Para limpiezas que deben terminar aunque el ViewModel que las pidió ya no exista. */
    private val alcanceLimpieza = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Copia y comprime la imagen elegida. Devuelve la clave del archivo o null si no se pudo leer. */
    suspend fun guardar(origen: Uri): String? = withContext(Dispatchers.IO) {
        AlmacenImagenes.guardar(contexto, origen)
    }

    suspend fun eliminar(clave: String) = withContext(Dispatchers.IO) {
        if (clave.isNotBlank()) AlmacenImagenes.eliminar(contexto, clave)
    }

    fun eliminarEnSegundoPlano(clave: String) {
        if (clave.isBlank()) return
        alcanceLimpieza.launch { AlmacenImagenes.eliminar(contexto, clave) }
    }

    suspend fun cargar(clave: String, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (clave.isBlank()) null else AlmacenImagenes.bitmap(contexto, clave, lado)
    }
}
