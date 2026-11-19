package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.MensajesSesion
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.data.repository.listoParaSalida
import com.megamarket.picker.state.DetallePreparacionUiState
import com.megamarket.picker.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetallePreparacionViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: PedidosPickerRepository
) : ViewModel() {

    private val pedidoId: String = savedStateHandle.get<String>(Ruta.ARG_PEDIDO).orEmpty()
    private val _estado = MutableStateFlow(DetallePreparacionUiState())
    val estado: StateFlow<DetallePreparacionUiState> = _estado.asStateFlow()
    private var observacion: Job? = null

    init {
        cargar()
        observar()
    }

    fun cargar() {
        if (pedidoId.isBlank()) {
            _estado.value = DetallePreparacionUiState(
                cargando = false,
                error = "Pedido no encontrado"
            )
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(cargando = it.preparacion == null, error = null, codigoError = null) }
            val resultado = try {
                repositorio.obtenerPreparacion(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(
                    TipoErrorPedido.RED,
                    MensajesSesion.SIN_CONEXION
                )
            }
            _estado.update {
                when (resultado) {
                    is ResultadoOperacion.Exito -> it.copy(
                        cargando = false,
                        preparacion = resultado.valor,
                        error = null,
                        codigoError = null
                    )
                    is ResultadoOperacion.Error -> it.copy(
                        cargando = false,
                        error = if (it.preparacion == null) resultado.mensaje else it.error,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
        }
    }

    fun resolverSinProducto(detalleId: String) {
        if (detalleId.isBlank() || _estado.value.resolviendoDetalleId != null) return
        viewModelScope.launch {
            _estado.update {
                it.copy(resolviendoDetalleId = detalleId, error = null, codigoError = null)
            }
            val resultado = try {
                repositorio.resolverLineaSinProducto(pedidoId, detalleId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
            }
            _estado.update { actual ->
                when (resultado) {
                    is ResultadoOperacion.Exito -> actual.copy(
                        resolviendoDetalleId = null,
                        preparacion = resultado.valor,
                        error = null,
                        codigoError = null
                    )
                    is ResultadoOperacion.Error -> actual.copy(
                        resolviendoDetalleId = null,
                        error = resultado.mensaje,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
        }
    }

    fun marcarListo() {
        val prep = _estado.value.preparacion ?: return
        if (_estado.value.marcandoListo || _estado.value.listoOk) return
        if (!prep.listoParaSalida()) {
            _estado.update {
                it.copy(
                    error = "Aún hay productos pendientes, sync local o sustituciones sin resolver",
                    codigoError = "PICKING_INCOMPLETO"
                )
            }
            return
        }
        viewModelScope.launch {
            // Impide cierres concurrentes: solo un PATCH CONFIRMADO a la vez.
            var iniciar = false
            _estado.update { actual ->
                if (actual.marcandoListo || actual.listoOk) {
                    actual
                } else {
                    iniciar = true
                    actual.copy(marcandoListo = true, error = null, codigoError = null)
                }
            }
            if (!iniciar) return@launch

            val resultado = try {
                repositorio.marcarListo(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> _estado.update {
                    it.copy(
                        marcandoListo = false,
                        listoOk = true,
                        error = null,
                        codigoError = null
                    )
                }
                is ResultadoOperacion.Error -> {
                    _estado.update {
                        it.copy(
                            marcandoListo = false,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                    if (resultado.tipo == TipoErrorPedido.CONFLICTO ||
                        resultado.tipo == TipoErrorPedido.PROHIBIDO
                    ) {
                        cargar()
                    }
                }
            }
        }
    }

    private fun observar() {
        if (pedidoId.isBlank()) return
        observacion?.cancel()
        observacion = viewModelScope.launch {
            repositorio.observarPreparacion(pedidoId).collect { preparacion ->
                if (preparacion != null) {
                    _estado.update { it.copy(preparacion = preparacion, cargando = false) }
                }
            }
        }
    }
}
