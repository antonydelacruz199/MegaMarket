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

    suspend fun listarMovimientos(
        page: Int = 1,
        limit: Int = 50
    ): ResultadoPagos<PaginaBilletera> = withContext(io) {
        val owner = usuarioRemotoId() ?: return@withContext sesionVencidaPago()
        try {
            val respuesta = api.listarMovimientosBilletera(owner, page = page, limit = limit)
            if (!respuesta.isSuccessful) return@withContext falloPago(respuesta)
            val cuerpo = respuesta.body()
            val cuenta = cuerpo?.cuenta?.aCuentaCliente()
                ?: return@withContext respuestaInvalidaPago()
            ResultadoPagos.Exito(
                PaginaBilletera(
                    billetera = BilleteraCliente(
                        cuenta = cuenta,
                        saldoInicialCentimos = null,
                        movimientos = cuerpo.movimientos.orEmpty().mapNotNull { it.aMovimientoCliente() }
                    ),
                    pagina = cuerpo.pagina ?: page,
                    hayMas = cuerpo.hayMas == true,
                    total = cuerpo.total ?: cuerpo.movimientos.orEmpty().size
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            falloRedPago(error)
        }
    }

    /** Saldo + movimientos: ambas consultas deben resolver; el saldo de `/saldo` manda sobre el de `/movimientos`. */
    suspend fun cargar(page: Int = 1): ResultadoPagos<PaginaBilletera> {
        val saldo = obtenerSaldo()
        if (saldo is ResultadoPagos.Error) return saldo
        val movimientos = listarMovimientos(page = page)
        if (movimientos is ResultadoPagos.Error) return movimientos
        val base = (saldo as ResultadoPagos.Exito).valor
        val pagina = (movimientos as ResultadoPagos.Exito).valor
        return ResultadoPagos.Exito(
            pagina.copy(
                billetera = base.copy(movimientos = pagina.billetera.movimientos)
            )
        )
    }
}

data class PaginaBilletera(
    val billetera: BilleteraCliente,
    val pagina: Int,
    val hayMas: Boolean,
    val total: Int
)
