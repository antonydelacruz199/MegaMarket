package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.remote.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Envoltorio delgado sobre los endpoints de billetera (`api/billetera`), simulada y sin bancos reales. */
class BilleteraRepository(
    private val api: ApiService,
    private val usuarioRemotoId: () -> String?,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun obtenerSaldo(): ResultadoPagos<BilleteraCliente> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        try {
            val respuesta = api.obtenerSaldoBilletera(owner)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            val cuerpo = respuesta.body()
            val cuenta = cuerpo?.cuenta?.aCuentaCliente()
                ?: return@withContext respuestaInvalidaPago()
            ResultadoPagos.Exito(BilleteraCliente(cuenta, cuerpo.saldoInicialCentimos, emptyList()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    suspend fun listarMovimientos(): ResultadoPagos<BilleteraCliente> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        try {
            val respuesta = api.listarMovimientosBilletera(owner)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            val cuerpo = respuesta.body()
            val cuenta = cuerpo?.cuenta?.aCuentaCliente()
                ?: return@withContext respuestaInvalidaPago()
            ResultadoPagos.Exito(
                BilleteraCliente(
                    cuenta = cuenta,
                    saldoInicialCentimos = null,
                    movimientos = cuerpo.movimientos.orEmpty().mapNotNull { it.aMovimientoCliente() }
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    /** Saldo + movimientos: ambas consultas deben resolver; el saldo de `/saldo` manda sobre el de `/movimientos`. */
    suspend fun cargar(): ResultadoPagos<BilleteraCliente> {
        val saldo = obtenerSaldo()
        if (saldo is ResultadoPagos.Error) return saldo
        val movimientos = listarMovimientos()
        if (movimientos is ResultadoPagos.Error) return movimientos
        val base = (saldo as ResultadoPagos.Exito).valor
        return ResultadoPagos.Exito(
            base.copy(movimientos = (movimientos as ResultadoPagos.Exito).valor.movimientos)
        )
    }
}
