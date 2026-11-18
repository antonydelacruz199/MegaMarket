package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.FiltroMisPedidos
import com.megamarket.cliente.data.repository.MisPedidosRepository
import com.megamarket.cliente.data.repository.ResultadoMisPedidos
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.state.MisPedidosUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MisPedidosViewModel(
    private val repositorio: MisPedidosRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(MisPedidosUiState())
    val estado: StateFlow<MisPedidosUiState> = _estado.asStateFlow()
    private var carga: Job? = null

    init {
        cargar()
    }

    fun seleccionarFiltro(filtro: FiltroMisPedidos) {
        if (_estado.value.filtro == filtro) return
        _estado.update { it.copy(filtro = filtro) }
        cargar()
    }

    fun cargar() {
        if (carga?.isActive == true) return
        carga = viewModelScope.launch {
            val hayDatos = _estado.value.pedidos.isNotEmpty()
            _estado.update {
                it.copy(
                    cargando = !hayDatos,
                    refrescando = hayDatos,
                    error = null,
                    codigoError = null
                )
            }
            val resultado = try {
                repositorio.listar(_estado.value.filtro)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoMisPedidos.Error(TipoErrorSustitucion.RED, "Sin conexión")
            }
            _estado.update {
                when (resultado) {
                    is ResultadoMisPedidos.Exito -> it.copy(
                        cargando = false,
                        refrescando = false,
                        pedidos = resultado.valor,
                        error = null,
                        codigoError = null
                    )
                    is ResultadoMisPedidos.Error -> it.copy(
                        cargando = false,
                        refrescando = false,
                        error = resultado.mensaje,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                            resultado.tipo == TipoErrorSustitucion.CUENTA
                    )
                }
            }
        }
    }
}
