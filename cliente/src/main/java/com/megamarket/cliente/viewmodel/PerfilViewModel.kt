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
    sesionRepository: SesionRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilUiState())
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            sesionRepository.usuario.collect { usuario ->
                _estado.value = PerfilUiState(
                    nombre = usuario?.nombre.orEmpty(),
                    correo = usuario?.correo.orEmpty(),
                    rol = "Cliente"
                )
            }
        }
    }
}
