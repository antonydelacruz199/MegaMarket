package com.megamarket.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.app.data.repository.BilleteraComercioRepository
import com.megamarket.app.data.repository.ResultadoBilleteraComercio
import com.megamarket.app.state.BilleteraComercioUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BilleteraComercioViewModel(
    private val repositorio: BilleteraComercioRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(BilleteraComercioUiState())
    val estado: StateFlow<BilleteraComercioUiState> = _estado.asStateFlow()

    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.cargar()) {
                is ResultadoBilleteraComercio.Ok ->
                    _estado.update { it.copy(cargando = false, billetera = resultado.billetera, error = null) }
                // Se conserva el último saldo conocido, pero se avisa que no se pudo refrescar.
                is ResultadoBilleteraComercio.Error ->
                    _estado.update { it.copy(cargando = false, error = resultado.mensaje) }
            }
        }
    }
}
