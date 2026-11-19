package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.MensajesPagoCliente
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.state.PagoQrUiState
import com.megamarket.cliente.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pago ONLINE simulado. Mostrar el QR no confirma nada: solo la respuesta del servidor a
 * `POST /api/pagos/{id}/confirmar` (o un estado CONFIRMADO leído del servidor) marca el pago como pagado.
 */
class PagoQrViewModel(
    savedStateHandle: SavedStateHandle,
    private val pagos: PagosRepository,
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

    /** Genera (o regenera) el QR informativo. Si el pago ya estaba confirmado no hay QR. */
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
                pagos.generarQr(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoPagos.Exito -> _estado.update {
                    it.copy(
                        cargando = false,
                        pago = resultado.valor.pago,
                        qr = resultado.valor.qr,
                        confirmado = resultado.valor.pago.confirmado,
                        error = null,
                        codigoError = null,
                        sesionVencida = false
                    )
                }
                is ResultadoPagos.Error -> _estado.update { aplicarError(it, resultado).copy(cargando = false) }
            }
            operacionEnCurso = false
        }
    }

    /** Acción explícita del usuario: pide al servidor confirmar el pago simulado. */
    fun confirmar() {
        val actual = _estado.value
        val pago = actual.pago ?: return
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
                            confirmado = true,
                            error = null,
                            codigoError = null
                        )
                    }
                    operacionEnCurso = false
                    // Verificación posterior contra el servidor (fuente de verdad).
                    refrescarEstadoInterno(silencioso = true)
                }
                is ResultadoPagos.Error -> {
                    _estado.update { aplicarError(it, resultado).copy(confirmando = false) }
                    operacionEnCurso = false
                    // Resultado desconocido (red caída): consultar hasta saber si el servidor sí confirmó.
                    if (resultado.tipo == TipoErrorSustitucion.RED) iniciarSeguimiento()
                }
            }
        }
    }

    /** Consulta manual del estado del pago en el servidor. */
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
                    // Una lectura atrasada no revierte una confirmación ya aceptada por el servidor.
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
