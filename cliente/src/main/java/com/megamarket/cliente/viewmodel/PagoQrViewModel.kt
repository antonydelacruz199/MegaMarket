package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.BilleteraRepository
import com.megamarket.cliente.data.repository.MensajesPagoCliente
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.state.PagoQrUiState
import com.megamarket.cliente.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pago ONLINE. El botón «Pagar ahora» confirma vía API; el QR es opcional y no modifica saldos.
 */
class PagoQrViewModel(
    savedStateHandle: SavedStateHandle,
    private val pagos: PagosRepository,
    private val billetera: BilleteraRepository,
    private val pedidos: PedidoRepository? = null,
    private val nuevoUuid: () -> String = { UUID.randomUUID().toString() },
    private val intervaloConsultaMs: Long = INTERVALO_CONSULTA_MS,
    private val maxConsultas: Int = MAX_CONSULTAS
) : ViewModel() {

    private val pedidoId: String = savedStateHandle.get<String>(Ruta.ARG_PEDIDO_REMOTO).orEmpty()

    private val _estado = MutableStateFlow(PagoQrUiState(pedidoId = pedidoId))
    val estado: StateFlow<PagoQrUiState> = _estado.asStateFlow()

    /** Se reutiliza en reintentos del mismo pago para que el servidor sea idempotente. */
    private var uuidConfirmacion: String? = null
    private var operacionEnCurso = false
    private var seguimiento: Job? = null

    init {
        cargar()
    }

    /** Carga el pago pendiente y el saldo; no genera QR. */
    fun cargar() {
        if (pedidoId.isBlank()) {
            _estado.value = PagoQrUiState(
                cargando = false,
                pedidoId = pedidoId,
                error = MensajesPagoCliente.PEDIDO_NO_DISPONIBLE
            )
            return
        }
        if (operacionEnCurso) return
        operacionEnCurso = true
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null, codigoError = null) }
            val resultado = try {
                coroutineScope {
                    val pagoDeferred = async {
                        try {
                            pagos.obtenerPorPedido(pedidoId)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
                        }
                    }
                    val saldoDeferred = async {
                        try {
                            billetera.obtenerSaldo()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                    Pair(pagoDeferred.await(), saldoDeferred.await())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Pair(
                    ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION),
                    null
                )
            }

            val (pagoRes, saldoRes) = resultado
            val saldo = (saldoRes as? ResultadoPagos.Exito)?.valor?.cuenta?.saldoCentimos
            when (pagoRes) {
                is ResultadoPagos.Exito -> _estado.update {
                    it.copy(
                        cargando = false,
                        pago = pagoRes.valor,
                        confirmado = pagoRes.valor.confirmado,
                        saldoDisponibleCentimos = saldo ?: it.saldoDisponibleCentimos,
                        error = null,
                        codigoError = null,
                        sesionVencida = false
                    )
                }
                is ResultadoPagos.Error -> _estado.update {
                    aplicarError(it, pagoRes).copy(
                        cargando = false,
                        saldoDisponibleCentimos = saldo ?: it.saldoDisponibleCentimos
                    )
                }
            }
            operacionEnCurso = false
        }
    }

    /** Genera el QR informativo (opcional). No confirma el pago ni mueve saldos. */
    fun mostrarQr() {
        if (pedidoId.isBlank() || operacionEnCurso) return
        if (_estado.value.confirmado) return
        operacionEnCurso = true
        viewModelScope.launch {
            _estado.update { it.copy(generandoQr = true, error = null, codigoError = null) }
            val resultado = try {
                pagos.generarQr(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoPagos.Exito -> _estado.update {
                    it.copy(
                        generandoQr = false,
                        mostrarQr = true,
                        pago = resultado.valor.pago,
                        qr = resultado.valor.qr,
                        confirmado = resultado.valor.pago.confirmado,
                        error = null,
                        codigoError = null
                    )
                }
                is ResultadoPagos.Error -> _estado.update {
                    aplicarError(it, resultado).copy(generandoQr = false)
                }
            }
            operacionEnCurso = false
        }
    }

    fun ocultarQr() {
        _estado.update { it.copy(mostrarQr = false) }
    }

    /** Acción principal: confirma el cobro en el servidor. */
    fun confirmar() {
        val actual = _estado.value
        val pago = actual.pago
        if (pago == null) {
            _estado.update {
                it.copy(error = MensajesPagoCliente.NO_ENCONTRADO)
            }
            cargar()
            return
        }
        if (operacionEnCurso || actual.confirmando || actual.cargando) return
        if (actual.confirmado || pago.confirmado) return
        operacionEnCurso = true
        val uuid = uuidConfirmacion ?: nuevoUuid().also { uuidConfirmacion = it }
        viewModelScope.launch {
            _estado.update { it.copy(confirmando = true, error = null, codigoError = null) }
            val resultado = try {
                pagos.confirmar(pago.id, uuid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoPagos.Exito -> {
                    _estado.update {
                        it.copy(
                            confirmando = false,
                            pago = resultado.valor.pago,
                            pedidoEstado = resultado.valor.pedidoEstado,
                            saldoClienteCentimos = resultado.valor.saldoClienteCentimos,
                            saldoDisponibleCentimos = resultado.valor.saldoClienteCentimos
                                ?: it.saldoDisponibleCentimos,
                            confirmado = true,
                            error = null,
                            codigoError = null
                        )
                    }
                    operacionEnCurso = false
                    sincronizarPedidoLocalTrasPago(resultado.valor.pedidoEstado)
                    refrescarEstadoInterno(silencioso = true)
                }
                is ResultadoPagos.Error -> {
                    _estado.update { aplicarError(it, resultado).copy(confirmando = false) }
                    operacionEnCurso = false
                    if (resultado.tipo == TipoErrorSustitucion.RED) iniciarSeguimiento()
                }
            }
        }
    }

    fun consultarEstado() {
        if (_estado.value.consultandoEstado) return
        viewModelScope.launch { refrescarEstadoInterno(silencioso = false) }
    }

    fun detenerSeguimiento() {
        seguimiento?.cancel()
        seguimiento = null
    }

    private fun iniciarSeguimiento() {
        if (seguimiento?.isActive == true) return
        seguimiento = viewModelScope.launch {
            var intentos = 0
            while (intentos < maxConsultas && !_estado.value.confirmado) {
                delay(intervaloConsultaMs)
                intentos++
                val r = refrescarEstadoInterno(silencioso = true)
                if (r is ResultadoPagos.Exito) break
            }
        }
    }

    private suspend fun refrescarEstadoInterno(silencioso: Boolean): ResultadoPagos<*> {
        if (pedidoId.isBlank()) return ResultadoPagos.Error(
            TipoErrorSustitucion.NO_ENCONTRADO,
            MensajesPagoCliente.PEDIDO_NO_DISPONIBLE
        )
        _estado.update { it.copy(consultandoEstado = true) }
        val resultado = try {
            pagos.obtenerPorPedido(pedidoId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
        }
        when (resultado) {
            is ResultadoPagos.Exito -> _estado.update {
                when {
                    it.confirmado && !resultado.valor.confirmado -> it.copy(consultandoEstado = false)
                    else -> it.copy(
                        consultandoEstado = false,
                        pago = resultado.valor,
                        confirmado = resultado.valor.confirmado,
                        error = if (resultado.valor.confirmado) null else it.error,
                        codigoError = if (resultado.valor.confirmado) null else it.codigoError
                    )
                }
            }
            is ResultadoPagos.Error -> _estado.update {
                if (silencioso && resultado.tipo == TipoErrorSustitucion.RED) {
                    it.copy(consultandoEstado = false)
                } else {
                    aplicarError(it, resultado).copy(consultandoEstado = false)
                }
            }
        }
        return resultado
    }

    private suspend fun sincronizarPedidoLocalTrasPago(estadoRemoto: String?) {
        val repo = pedidos ?: return
        try {
            repo.marcarPagadoPorRemoteId(pedidoId, estadoRemoto ?: "PAGADO")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // La confirmación API ya ocurrió; el listado se reconciliará al refrescar.
        }
    }

    private fun aplicarError(previo: PagoQrUiState, error: ResultadoPagos.Error): PagoQrUiState =
        previo.copy(
            error = error.mensaje,
            codigoError = error.codigo,
            sesionVencida = error.tipo == TipoErrorSustitucion.SESION ||
                error.tipo == TipoErrorSustitucion.CUENTA
        )

    override fun onCleared() {
        detenerSeguimiento()
        super.onCleared()
    }

    companion object {
        const val INTERVALO_CONSULTA_MS = 3_000L
        const val MAX_CONSULTAS = 5
    }
}
