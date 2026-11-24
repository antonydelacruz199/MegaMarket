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
            when (val resultado = repositorio.cargar(page = 1)) {
                is ResultadoBilleteraComercio.Ok ->
                    _estado.update {
                        it.copy(
                            cargando = false,
                            billetera = resultado.billetera,
                            movimientos = resultado.billetera.movimientos,
                            pagina = resultado.billetera.pagina,
                            hayMas = resultado.billetera.hayMas,
                            error = null
                        )
                    }
                is ResultadoBilleteraComercio.Error ->
                    _estado.update { it.copy(cargando = false, error = resultado.mensaje) }
            }
        }
    }

    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargandoMas || actual.cargando) return
        _estado.update { it.copy(cargandoMas = true, error = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.cargar(page = actual.pagina + 1)) {
                is ResultadoBilleteraComercio.Ok ->
                    _estado.update {
                        it.copy(
                            cargandoMas = false,
                            billetera = resultado.billetera.copy(
                                movimientos = (it.movimientos + resultado.billetera.movimientos)
                                    .distinctBy { m -> m.id }
                            ),
                            movimientos = (it.movimientos + resultado.billetera.movimientos)
                                .distinctBy { m -> m.id },
                            pagina = resultado.billetera.pagina,
                            hayMas = resultado.billetera.hayMas
                        )
                    }
                is ResultadoBilleteraComercio.Error ->
                    _estado.update { it.copy(cargandoMas = false, error = resultado.mensaje) }
            }
        }
    }
}
