package com.megamarket.picker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.MensajesSesion
import com.megamarket.picker.data.repository.PedidosRepartoRepository
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.TableroReparto
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.state.ListaRepartidorUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListaRepartidorViewModel(
    private val repositorio: PedidosRepartoRepository
) : ViewModel() {

    private val _estado = MutableStateFlow(ListaRepartidorUiState())
    val estado: StateFlow<ListaRepartidorUiState> = _estado.asStateFlow()
    private var carga: Job? = null

    init {
        cargar()
    }

    fun cargar() {
        if (carga?.isActive == true) return
        carga = viewModelScope.launch {
            val hayDatos = _estado.value.disponibles.isNotEmpty() ||
                _estado.value.enCamino.isNotEmpty() ||
                _estado.value.entregados.isNotEmpty()
            _estado.update { it.copy(cargando = !hayDatos, refrescando = hayDatos) }
            aplicar(cargarTablero())
        }
    }

    fun consumirPedidoTomado() {
        _estado.update { it.copy(pedidoTomadoId = null) }
    }

    fun tomar(pedidoId: String) {
        if (_estado.value.tomandoId != null) return
        viewModelScope.launch {
            _estado.update { it.copy(tomandoId = pedidoId, error = null, codigoError = null) }
            when (val resultado = repositorio.tomar(pedidoId)) {
                is ResultadoOperacion.Exito -> {
                    val tablero = cargarTablero()
                    aplicar(tablero)
                    _estado.update { actual ->
                        actual.copy(
                            tomandoId = null,
                            pedidoTomadoId = if (actual.sesionVencida) null else pedidoId
                        )
                    }
                }
                is ResultadoOperacion.Error -> {
                    _estado.update {
                        it.copy(
                            tomandoId = null,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                    if (resultado.tipo == TipoErrorPedido.CONFLICTO ||
                        resultado.tipo == TipoErrorPedido.PROHIBIDO
                    ) {
                        aplicar(cargarTablero())
                    }
                }
            }
        }
    }

    private suspend fun cargarTablero(): ResultadoOperacion<TableroReparto> =
        try {
            repositorio.cargarTablero()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
        }

    private fun aplicar(resultado: ResultadoOperacion<TableroReparto>) {
        when (resultado) {
            is ResultadoOperacion.Exito -> _estado.update {
                it.copy(
                    cargando = false,
                    refrescando = false,
                    disponibles = resultado.valor.disponibles,
                    enCamino = resultado.valor.enCamino,
                    entregados = resultado.valor.entregados,
                    error = null,
                    codigoError = null,
                    sesionVencida = false
                )
            }
            is ResultadoOperacion.Error -> _estado.update {
                it.copy(
                    cargando = false,
                    refrescando = false,
                    error = resultado.mensaje,
                    codigoError = resultado.codigo,
                    sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                        resultado.tipo == TipoErrorPedido.CUENTA
                )
            }
        }
    }
}
