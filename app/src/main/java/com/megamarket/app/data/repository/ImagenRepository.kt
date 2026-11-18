package com.megamarket.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.Gson
import com.megamarket.app.data.local.AlmacenImagenes
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.ErrorApiDto
import com.megamarket.modelo.ClavesImagen
import com.megamarket.modelo.MediosImagen
import com.megamarket.modelo.SolicitudImagenProducto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException

class ImagenRepository(
    contexto: Context,
    private val api: ApiService,
    private val baseUrl: String
) {
    private val contexto = contexto.applicationContext
    private val gson = Gson()

    /** Para limpiezas que deben terminar aunque el ViewModel que las pidió ya no exista. */
    private val alcanceLimpieza = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Copia y comprime la imagen elegida. Devuelve la clave local de vista previa. */
    suspend fun guardarImagen(origen: Uri): String? = withContext(Dispatchers.IO) {
        AlmacenImagenes.guardar(contexto, origen)
    }

    suspend fun eliminarImagen(clave: String) = withContext(Dispatchers.IO) {
        if (ClavesImagen.esLocal(clave)) AlmacenImagenes.eliminar(contexto, clave)
    }

    fun eliminarEnSegundoPlano(clave: String) {
        if (!ClavesImagen.esLocal(clave)) return
        alcanceLimpieza.launch { AlmacenImagenes.eliminar(contexto, clave) }
    }

    suspend fun cargarImagen(clave: String, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!ClavesImagen.esLocal(clave)) null else AlmacenImagenes.bitmap(contexto, clave, lado)
    }

    fun solicitud(remoteId: String?, imagenKey: String?): SolicitudImagenProducto? =
        MediosImagen.solicitudProducto(baseUrl, remoteId, imagenKey)

    suspend fun subirProducto(productoRemotoId: String, claveLocal: String): ResultadoSubidaImagen =
        withContext(Dispatchers.IO) {
            if (!ClavesImagen.esLocal(claveLocal)) {
                return@withContext ResultadoSubidaImagen.Error("La vista previa no es un archivo local")
            }
            val archivo = AlmacenImagenes.archivo(contexto, claveLocal)
                ?: return@withContext ResultadoSubidaImagen.Error(
                    "No se encontró la foto en el teléfono. Elige la imagen otra vez."
                )
            val parte = MultipartBody.Part.createFormData(
                "archivo",
                "foto.jpg",
                archivo.asRequestBody("image/jpeg".toMediaType())
            )
            try {
                val respuesta = api.subirImagenProducto(productoRemotoId, parte)
                if (!respuesta.isSuccessful) {
                    return@withContext ResultadoSubidaImagen.Error(mensajeError(respuesta.code(), respuesta.errorBody()?.string()))
                }
                val cuerpo = respuesta.body()
                val clave = cuerpo?.producto?.imagenKey ?: cuerpo?.imagenKey
                if (!ClavesImagen.perteneceAProducto(clave, productoRemotoId)) {
                    return@withContext ResultadoSubidaImagen.Error("El servidor no devolvió una clave de imagen válida")
                }
                ResultadoSubidaImagen.Ok(
                    imagenKey = checkNotNull(clave),
                    version = cuerpo?.producto?.version,
                    updatedAt = cuerpo?.producto?.updatedAt
                )
            } catch (_: IOException) {
                ResultadoSubidaImagen.Error("Sin conexión. La foto sigue guardada en este teléfono.")
            }
        }

    private fun mensajeError(codigo: Int, raw: String?): String {
        val texto = raw?.takeIf { it.isNotBlank() }?.let { cuerpo ->
            runCatching { gson.fromJson(cuerpo, ErrorApiDto::class.java)?.error }.getOrNull()
        }
        if (!texto.isNullOrBlank()) return texto
        return when (codigo) {
            401 -> "Sesión expirada. Inicia sesión de nuevo."
            413 -> "La imagen es demasiado grande."
            else -> "No se pudo subir la imagen"
        }
    }
}

sealed class ResultadoSubidaImagen {
    data class Ok(
        val imagenKey: String,
        val version: Long?,
        val updatedAt: String?
    ) : ResultadoSubidaImagen()

    data class Error(val mensaje: String) : ResultadoSubidaImagen()
}
