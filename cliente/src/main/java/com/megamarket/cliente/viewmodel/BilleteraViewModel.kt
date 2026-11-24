package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.BilleteraRepository
import com.megamarket.cliente.data.repository.MensajesPagoCliente
import com.megamarket.cliente.data.repository.PaginaBilletera
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
            aplicar(cargarPagina(1), anexar = false)
        }
    }

    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargandoMas || actual.cargando) return
        viewModelScope.launch {
            _estado.update { it.copy(cargandoMas = true, error = null) }
            aplicar(cargarPagina(actual.pagina + 1), anexar = true)
        }
    }

    private suspend fun cargarPagina(page: Int): ResultadoPagos<PaginaBilletera> =
        try {
            billetera.cargar(page = page)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
        }

    private fun aplicar(resultado: ResultadoPagos<PaginaBilletera>, anexar: Boolean) {
        when (resultado) {
            is ResultadoPagos.Exito -> _estado.update {
                val movimientos = if (anexar) {
                    (it.movimientos + resultado.valor.billetera.movimientos).distinctBy { m -> m.id }
                } else {
                    resultado.valor.billetera.movimientos
                }
                it.copy(
                    cargando = false,
                    cargandoMas = false,
                    cuenta = resultado.valor.billetera.cuenta,
                    saldoInicialCentimos = resultado.valor.billetera.saldoInicialCentimos
                        ?: it.saldoInicialCentimos,
                    movimientos = movimientos,
                    pagina = resultado.valor.pagina,
                    hayMas = resultado.valor.hayMas,
                    error = null,
                    sesionVencida = false
                )
            }
            is ResultadoPagos.Error -> _estado.update {
                it.copy(
                    cargando = false,
                    cargandoMas = false,
                    error = resultado.mensaje,
                    sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                        resultado.tipo == TipoErrorSustitucion.CUENTA
                )
            }
        }
    }
}
