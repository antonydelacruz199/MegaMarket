package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.MODALIDAD_ONLINE
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.state.ConfirmacionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfirmacionViewModel(
    savedStateHandle: SavedStateHandle,
    private val pedidos: PedidoRepository,
    private val pagos: PagosRepository
) : ViewModel() {

    private val pedidoId: Long = savedStateHandle.get<Long>(ARG_PEDIDO)
        ?: savedStateHandle.get<String>(ARG_PEDIDO)?.toLongOrNull()
        ?: 0L

    private val _estado = MutableStateFlow(ConfirmacionUiState())
    val estado: StateFlow<ConfirmacionUiState> = _estado.asStateFlow()

    init {
        cargarPedido()
    }

    fun cargarPedido() {
        viewModelScope.launch {
            _estado.value = ConfirmacionUiState(cargando = true)
            _estado.value = try {
                val pedido = pedidos.obtenerPedido(pedidoId)
                if (pedido == null) {
                    ConfirmacionUiState(cargando = false, error = "No se encontró el pedido")
                } else {
                    ConfirmacionUiState(cargando = false, pedido = pedido, totalPropuesto=if(pedido.estado=="REQUIERE_REVISION") runCatching { pedidos.totalActual(pedido.id) }.getOrNull() else null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ConfirmacionUiState(cargando = false, error = "No se pudo cargar el pedido")
            }
            consultarPago()
        }
    }

    /** Vuelve a leer el estado del pago ONLINE (p. ej. al regresar de la pantalla del QR). */
    fun refrescarPago() {
        viewModelScope.launch { consultarPago() }
    }

    private suspend fun consultarPago() {
        val pedido = _estado.value.pedido ?: return
        val remoto = pedido.remoteId?.trim().orEmpty()
        if (remoto.isEmpty() || !pedido.modalidadPago.equals(MODALIDAD_ONLINE, ignoreCase = true)) return
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
        val total=_estado.value.totalPropuesto ?: return
        viewModelScope.launch {
            try { pedidos.aceptarPrecios(pedidoId,total); cargarPedido() }
            catch(e: Exception) { _estado.value=_estado.value.copy(error=e.message) }
        }
    }
    fun cancelarPedido() {
        viewModelScope.launch {
            try { pedidos.cancelarPendiente(pedidoId); cargarPedido() }
            catch(e: Exception) { _estado.value=_estado.value.copy(error=e.message) }
        }
    }

    companion object {
        const val ARG_PEDIDO = "pedidoId"
    }
}
