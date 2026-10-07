package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.model.estado.ConfirmacionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConfirmacionViewModel(
    savedStateHandle: SavedStateHandle,
    private val pedidos: PedidoRepository
) : ViewModel() {

    private val pedidoId: Long = savedStateHandle.get<Long>(ARG_PEDIDO)
        ?: savedStateHandle.get<String>(ARG_PEDIDO)?.toLongOrNull()
        ?: 0L

    private val _estado = MutableStateFlow(ConfirmacionUiState())
    val estado: StateFlow<ConfirmacionUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _estado.value = ConfirmacionUiState(cargando = true)
            _estado.value = try {
                val pedido = pedidos.obtenerPedido(pedidoId)
                if (pedido == null) {
                    ConfirmacionUiState(cargando = false, error = "No se encontró el pedido")
                } else {
                    ConfirmacionUiState(cargando = false, pedido = pedido)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ConfirmacionUiState(cargando = false, error = "No se pudo cargar el pedido")
            }
        }
    }

    companion object {
        const val ARG_PEDIDO = "pedidoId"
    }
}
