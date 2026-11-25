package com.megamarket.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.megamarket.app.data.remote.ErrorApiDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * Ejecuta llamadas Retrofit y normaliza errores (red, JSON del servidor, HTTP) a
 * [ResultadoAbastecimiento]. Mismo criterio que [BilleteraComercioRepository].
 */
internal class EjecutorApi(
    private val io: CoroutineDispatcher,
    val gson: Gson = Gson()
) {

    suspend fun <R, T> ejecutar(
        mensajeSinRed: String,
        mensajeBase: String,
        llamada: suspend () -> Response<R>,
        mapear: (R) -> T?
    ): ResultadoAbastecimiento<T> = withContext(io) {
        try {
            val respuesta = llamada()
            if (!respuesta.isSuccessful) {
                return@withContext ResultadoAbastecimiento.Error(
                    mensaje = mensajeError(respuesta.code(), leerCuerpoError(respuesta), mensajeBase),
                    codigoHttp = respuesta.code()
                )
            }
            val cuerpo = respuesta.body()
            val valor = cuerpo?.let(mapear)
            if (valor == null) {
                ResultadoAbastecimiento.Error("El servidor devolvió una respuesta inválida")
            } else {
                ResultadoAbastecimiento.Ok(valor)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            ResultadoAbastecimiento.Error(mensajeSinRed)
        } catch (_: JsonParseException) {
            ResultadoAbastecimiento.Error("El servidor devolvió una respuesta ilegible")
        }
    }

    fun leerCuerpoError(respuesta: Response<*>): String? =
        runCatching { respuesta.errorBody()?.string() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun mensajeError(codigo: Int, cuerpo: String?, mensajeBase: String): String {
        val texto = cuerpo?.let { runCatching { gson.fromJson(it, ErrorApiDto::class.java)?.error }.getOrNull() }
        if (!texto.isNullOrBlank()) return texto
        return when (codigo) {
            401 -> "Sesión expirada. Inicia sesión de nuevo."
            403 -> "Solo el administrador puede realizar esta acción."
            404 -> "No se encontró el recurso solicitado."
            else -> mensajeBase
        }
    }

    fun <T> aDto(cuerpo: String?, clase: Class<T>): T? =
        cuerpo?.let { runCatching { gson.fromJson(it, clase) }.getOrNull() }
}
