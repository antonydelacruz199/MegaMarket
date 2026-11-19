package com.megamarket.cliente.data.repository

import com.google.gson.Gson
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.ErrorApiDto
import com.megamarket.cliente.model.FranjaEntrega
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

sealed class ResultadoFranjas<out T> {
    data class Exito<T>(val valor: T) : ResultadoFranjas<T>()
    data class Error(
        val mensaje: String,
        val codigo: String? = null,
        val sesionVencida: Boolean = false
    ) : ResultadoFranjas<Nothing>()
}

class FranjasRepository(
    private val api: ApiService,
    private val usuarioRemotoId: () -> String?,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun listar(): ResultadoFranjas<List<FranjaEntrega>> = withContext(io) {
        val owner = usuarioRemotoId()
        try {
            val respuesta = api.listarFranjas(owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val lista = respuesta.body()?.franjas.orEmpty()
                .mapNotNull { FranjaEntrega.desdeDto(it) }
                .sortedWith(compareBy({ it.fechaIso }, { it.horaInicio }))
            ResultadoFranjas.Exito(lista)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            ResultadoFranjas.Error(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Sin conexión. No se pudieron cargar las franjas."
            )
        }
    }

    suspend fun obtener(id: String): ResultadoFranjas<FranjaEntrega> = withContext(io) {
        val franjaId = id.trim()
        if (franjaId.isEmpty()) {
            return@withContext ResultadoFranjas.Error("Franja no encontrada", "FRANJA_NO_DISPONIBLE")
        }
        val owner = usuarioRemotoId()
        try {
            val respuesta = api.obtenerFranja(franjaId, owner)
            if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
            val dto = respuesta.body()?.franja
                ?: return@withContext ResultadoFranjas.Error("Franja no encontrada", "FRANJA_NO_DISPONIBLE")
            val franja = FranjaEntrega.desdeDto(dto)
                ?: return@withContext ResultadoFranjas.Error("Franja no encontrada", "FRANJA_NO_DISPONIBLE")
            ResultadoFranjas.Exito(franja)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            ResultadoFranjas.Error(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Sin conexión. No se pudo consultar la franja."
            )
        }
    }

    private fun <T> fallo(response: Response<*>): ResultadoFranjas<T> {
        val crudo = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val dto = runCatching { gson.fromJson(crudo, ErrorApiDto::class.java) }.getOrNull()
        return ResultadoFranjas.Error(
            mensaje = dto?.error?.takeIf { it.isNotBlank() }
                ?: when (response.code()) {
                    401 -> "Sesión expirada. Vuelve a iniciar sesión."
                    404 -> "Franja no disponible"
                    else -> "No se pudieron obtener las franjas"
                },
            codigo = dto?.codigo,
            sesionVencida = response.code() == 401
        )
    }
}
