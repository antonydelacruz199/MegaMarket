package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.SesionRepository
import com.megamarket.cliente.state.PerfilUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PerfilViewModel(
    sesionRepository: SesionRepository,
    pedidos: com.megamarket.cliente.data.repository.PedidoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilUiState())
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch { pedidos.observarPedidos().collect { lista -> _estado.value=_estado.value.copy(pedidos=lista) } }
        viewModelScope.launch {
            sesionRepository.usuario.collect { usuario ->
                _estado.value = PerfilUiState(
                    nombre = usuario?.nombre.orEmpty(),
                    correo = usuario?.correo.orEmpty(),
                    rol = "Cliente",
                    pedidos = _estado.value.pedidos.filter { it.clienteId==usuario?.id }
                )
            }
        }
    }
}
