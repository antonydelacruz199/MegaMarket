package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.MODALIDAD_ONLINE
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.data.repository.ResultadoSincronizacion
import com.megamarket.cliente.data.repository.SincronizacionRepository
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.state.ConfirmacionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfirmacionViewModel(
    savedStateHandle: SavedStateHandle,
    private val pedidos: PedidoRepository,
    private val pagos: PagosRepository,
    private val sincronizacion: SincronizacionRepository
) : ViewModel() {

    private val pedidoId: Long = savedStateHandle.get<Long>(ARG_PEDIDO)
        ?: savedStateHandle.get<String>(ARG_PEDIDO)?.toLongOrNull()
        ?: 0L

    private val _estado = MutableStateFlow(ConfirmacionUiState())
    val estado: StateFlow<ConfirmacionUiState> = _estado.asStateFlow()

    init {
        cargarPedido(forzarSincronizacion = true)
    }

    fun cargarPedido(forzarSincronizacion: Boolean = false) {
        viewModelScope.launch {
            _estado.update {
                it.copy(
                    cargando = true,
                    sincronizando = forzarSincronizacion,
                    error = null,
                    cancelando = false
                )
            }
            if (forzarSincronizacion) {
                try {
                    sincronizacion.sincronizarPendientes()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Se reintenta al pulsar «Actualizar»; no bloqueamos la UI.
                }
            }
            val pedido = try {
                pedidos.obtenerPedido(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _estado.value = ConfirmacionUiState(cargando = false, error = "No se pudo cargar el pedido")
                return@launch
            }
            if (pedido == null) {
                _estado.value = ConfirmacionUiState(cargando = false, error = "No se encontró el pedido")
                return@launch
            }
            _estado.update {
                it.copy(
                    cargando = false,
                    sincronizando = false,
                    pedido = pedido,
                    totalPropuesto = if (pedido.estado == "REQUIERE_REVISION") {
                        runCatching { pedidos.totalActual(pedido.id) }.getOrNull()
                    } else {
                        null
                    },
                    error = null
                )
            }
            consultarPago()
            // Pedido recién creado: WorkManager puede ir detrás; reintento corto tras el sync forzado.
            if (pedido.remoteId.isNullOrBlank() &&
                pedido.estado == "PENDIENTE_CONFIRMACION" &&
                forzarSincronizacion
            ) {
                delay(700)
                sincronizarYRecargar()
            }
        }
    }

    fun sincronizarYRecargar() {
        viewModelScope.launch {
            _estado.update { it.copy(sincronizando = true, error = null) }
            val resultado = try {
                sincronizacion.sincronizarPendientes()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoSincronizacion.Reintentar("Sin conexión")
            }
            val pedido = try {
                pedidos.obtenerPedido(pedidoId)
            } catch (_: Exception) {
                null
            }
            if (pedido != null) {
                _estado.update {
                    it.copy(
                        sincronizando = false,
                        pedido = pedido,
                        error = when (resultado) {
                            is ResultadoSincronizacion.Reintentar ->
                                resultado.motivo.takeIf { msg ->
                                    pedido.remoteId.isNullOrBlank()
                                }
                            else -> null
                        }
                    )
                }
                consultarPago()
            } else {
                _estado.update {
                    it.copy(sincronizando = false, error = "No se pudo actualizar el pedido")
                }
            }
        }
    }

    fun refrescarPago() {
        viewModelScope.launch {
            val pedido = try {
                pedidos.obtenerPedido(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (pedido != null) {
                _estado.update { it.copy(pedido = pedido) }
            }
            consultarPago()
        }
    }

    private suspend fun consultarPago() {
        val pedido = _estado.value.pedido ?: return
        val remoto = pedido.remoteId?.trim().orEmpty()
        if (remoto.isEmpty() || !pedido.modalidadPago.equals(MODALIDAD_ONLINE, ignoreCase = true)) {
            return
        }
        val resultado = try {
            pagos.obtenerPorPedido(remoto)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
        if (resultado is ResultadoPagos.Exito) {
            _estado.update { it.copy(pagoConfirmado = resultado.valor.confirmado) }
        }
    }

    fun aceptarPrecios() {
        val total = _estado.value.totalPropuesto ?: return
        viewModelScope.launch {
            try {
                pedidos.aceptarPrecios(pedidoId, total)
                cargarPedido(forzarSincronizacion = true)
            } catch (e: Exception) {
                _estado.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Cancela según el estado:
     * - REQUIERE_REVISION / PENDIENTE sin remoteId → local
     * - CONFIRMADO con pago pendiente → API
     */
    fun cancelarPedido() {
        val pedido = _estado.value.pedido ?: return
        if (_estado.value.cancelando) return
        viewModelScope.launch {
            _estado.update { it.copy(cancelando = true, error = null) }
            try {
                when {
                    pedido.estado == "REQUIERE_REVISION" -> {
                        pedidos.cancelarPendiente(pedidoId)
                    }
                    pedido.remoteId.isNullOrBlank() &&
                        pedido.estado in setOf("PENDIENTE_CONFIRMACION", "REQUIERE_REVISION") -> {
                        pedidos.abortarLocalSinEnviar(pedidoId)
                    }
                    pedido.estado == Pedido.ESTADO_CONFIRMADO &&
                        _estado.value.pagoConfirmado != true &&
                        !pedido.remoteId.isNullOrBlank() -> {
                        when (val r = pagos.cancelarPedido(pedido.remoteId!!)) {
                            is ResultadoPagos.Exito -> pedidos.marcarCanceladoLocal(pedidoId)
                            is ResultadoPagos.Error -> {
                                _estado.update {
                                    it.copy(cancelando = false, error = r.mensaje)
                                }
                                return@launch
                            }
                        }
                    }
                    else -> {
                        _estado.update {
                            it.copy(
                                cancelando = false,
                                error = "Este pedido no se puede cancelar en su estado actual"
                            )
                        }
                        return@launch
                    }
                }
                cargarPedido(forzarSincronizacion = false)
            } catch (e: Exception) {
                _estado.update { it.copy(cancelando = false, error = e.message ?: "No se pudo cancelar") }
            }
        }
    }

    companion object {
        const val ARG_PEDIDO = "pedidoId"
    }
}
