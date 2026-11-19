package com.megamarket.app.data.repository

import com.google.gson.Gson
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.BilleteraMovimientosResponse
import com.megamarket.app.data.remote.BilleteraSaldoResponse
import com.megamarket.app.data.remote.ErrorApiDto
import com.megamarket.app.data.remote.MovimientoBilleteraDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

const val ETIQUETA_SIMULACION = "SIMULACIÓN"

data class MovimientoComercio(
    val id: String,
    val tipo: String,
    val sentido: String,
    val montoCentimos: Long,
    val saldoPosteriorCentimos: Long?,
    val pedidoId: String?,
    val descripcion: String?,
    val createdAt: String?
) {
    val esCredito: Boolean get() = sentido.equals("CREDITO", ignoreCase = true)
    val montoConSigno: Long get() = if (esCredito) montoCentimos else -montoCentimos
}

data class BilleteraComercio(
    val saldoCentimos: Long,
    val moneda: String,
    val saldoInicialCentimos: Long?,
    val movimientos: List<MovimientoComercio>
)

sealed class ResultadoBilleteraComercio {
    data class Ok(val billetera: BilleteraComercio) : ResultadoBilleteraComercio()
    data class Error(val mensaje: String) : ResultadoBilleteraComercio()
}

/** Cliente delgado de `api/billetera` para el rol administrador. El servidor es la única fuente del saldo. */
class BilleteraComercioRepository(
    private val api: ApiService,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val gson: Gson = Gson()
) {

    suspend fun cargar(): ResultadoBilleteraComercio = withContext(io) {
        try {
            val saldo = api.obtenerBilleteraComercio()
            if (!saldo.isSuccessful) return@withContext ResultadoBilleteraComercio.Error(mensajeError(saldo))
            val movimientos = api.listarMovimientosBilletera()
            if (!movimientos.isSuccessful) {
                return@withContext ResultadoBilleteraComercio.Error(mensajeError(movimientos))
            }
            aDominio(saldo.body(), movimientos.body())
                ?: ResultadoBilleteraComercio.Error("El servidor devolvió una billetera inválida")
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            ResultadoBilleteraComercio.Error("Sin conexión. No se pudo consultar la billetera.")
        }
    }

    private fun aDominio(
        saldo: BilleteraSaldoResponse?,
        movimientos: BilleteraMovimientosResponse?
    ): ResultadoBilleteraComercio.Ok? {
        val monto = saldo?.cuenta?.saldoCentimos ?: return null
        return ResultadoBilleteraComercio.Ok(
            BilleteraComercio(
                saldoCentimos = monto,
                moneda = saldo.cuenta.moneda?.takeIf { it.isNotBlank() } ?: "PEN",
                saldoInicialCentimos = saldo.saldoInicialCentimos,
                movimientos = movimientos?.movimientos.orEmpty().mapNotNull { it.aDominio() }
            )
        )
    }

    private fun MovimientoBilleteraDto.aDominio(): MovimientoComercio? {
        val idLimpio = id?.takeIf { it.isNotBlank() } ?: return null
        val monto = montoCentimos ?: return null
        return MovimientoComercio(
            id = idLimpio,
            tipo = tipo.orEmpty(),
            sentido = sentido.orEmpty(),
            montoCentimos = monto,
            saldoPosteriorCentimos = saldoPosteriorCentimos,
            pedidoId = pedidoId,
            descripcion = descripcion,
            createdAt = createdAt
        )
    }

    private fun mensajeError(respuesta: Response<*>): String {
        val texto = respuesta.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { cuerpo ->
            runCatching { gson.fromJson(cuerpo, ErrorApiDto::class.java)?.error }.getOrNull()
        }
        if (!texto.isNullOrBlank()) return texto
        return when (respuesta.code()) {
            401 -> "Sesión expirada. Inicia sesión de nuevo."
            403 -> "Solo el administrador puede ver la billetera del comercio."
            else -> "No se pudo consultar la billetera."
        }
    }
}
