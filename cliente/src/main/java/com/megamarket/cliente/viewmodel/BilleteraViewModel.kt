package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.BilleteraRepository
import com.megamarket.cliente.data.repository.MensajesPagoCliente
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.state.BilleteraUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BilleteraViewModel(
    private val billetera: BilleteraRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(BilleteraUiState())
    val estado: StateFlow<BilleteraUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val resultado = try {
                billetera.cargar()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoPagos.Exito -> _estado.update {
                    it.copy(
                        cargando = false,
                        cuenta = resultado.valor.cuenta,
                        saldoInicialCentimos = resultado.valor.saldoInicialCentimos,
                        movimientos = resultado.valor.movimientos,
                        error = null,
                        sesionVencida = false
                    )
                }
                is ResultadoPagos.Error -> _estado.update {
                    it.copy(
                        cargando = false,
                        error = resultado.mensaje,
                        sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                            resultado.tipo == TipoErrorSustitucion.CUENTA
                    )
                }
            }
        }
    }
}
