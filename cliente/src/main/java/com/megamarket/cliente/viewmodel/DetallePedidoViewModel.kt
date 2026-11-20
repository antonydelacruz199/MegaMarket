package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.data.repository.MisPedidosRepository
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.ResultadoMisPedidos
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.data.repository.ResultadoSustitucion
import com.megamarket.cliente.data.repository.SustitucionesRepository
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.state.DetallePedidoUiState
import com.megamarket.cliente.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DetallePedidoViewModel(
    savedStateHandle: SavedStateHandle,
    private val misPedidos: MisPedidosRepository,
    private val sustituciones: SustitucionesRepository,
    private val pagos: PagosRepository? = null
) : ViewModel() {

    private val pedidoId: String =
        savedStateHandle.get<String>(Ruta.ARG_PEDIDO_REMOTO).orEmpty()

    private val _estado = MutableStateFlow(DetallePedidoUiState())
    val estado: StateFlow<DetallePedidoUiState> = _estado.asStateFlow()

    private var reloj: Job? = null
    private var polling: Job? = null

    init {
        cargar()
    }

    fun cargar() {
        if (pedidoId.isBlank()) {
            _estado.value = DetallePedidoUiState(
                cargando = false,
                error = "Pedido no encontrado"
            )
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(cargando = it.detalle == null, error = null) }
            val resultado = try {
                misPedidos.obtenerDetalle(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoMisPedidos.Error(TipoErrorSustitucion.RED, "Sin conexión")
            }
            _estado.update {
                when (resultado) {
                    is ResultadoMisPedidos.Exito -> it.copy(
                        cargando = false,
                        detalle = resultado.valor,
                        error = null,
                        codigoError = null
                    )
                    is ResultadoMisPedidos.Error -> it.copy(
                        cargando = false,
                        error = resultado.mensaje,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                            resultado.tipo == TipoErrorSustitucion.CUENTA
                    )
                }
            }
            ajustarSeguimiento()
        }
    }

    fun aceptar(sustitucionId: String) = decidir(sustitucionId, aceptar = true)

    fun rechazar(sustitucionId: String) = decidir(sustitucionId, aceptar = false)

    fun cancelarPedido() {
        val actual = _estado.value
        val detalle = actual.detalle ?: return
        val repo = pagos ?: return
        if (actual.cancelando || actual.cancelado || !detalle.puedeCancelar) return
        viewModelScope.launch {
            _estado.update { it.copy(cancelando = true, error = null, codigoError = null) }
            val resultado = try {
                repo.cancelarPedido(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoPagos.Error(TipoErrorSustitucion.RED, "Sin conexión")
            }
            when (resultado) {
                is ResultadoPagos.Exito -> {
                    _estado.update { it.copy(cancelando = false, cancelado = true) }
                    cargar()
                }
                is ResultadoPagos.Error -> _estado.update {
                    it.copy(
                        cancelando = false,
                        error = resultado.mensaje,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                            resultado.tipo == TipoErrorSustitucion.CUENTA
                    )
                }
            }
        }
    }

    fun iniciarSeguimiento() {
        ajustarSeguimiento()
    }

    fun detenerSeguimiento() {
        reloj?.cancel()
        polling?.cancel()
        reloj = null
        polling = null
    }

    private fun decidir(sustitucionId: String, aceptar: Boolean) {
        if (_estado.value.decidiendoId != null) return
        viewModelScope.launch {
            _estado.update { it.copy(decidiendoId = sustitucionId, error = null) }
            val resultado = try {
                if (aceptar) sustituciones.aceptar(sustitucionId)
                else sustituciones.rechazar(sustitucionId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoSustitucion.Error(TipoErrorSustitucion.RED, "Sin conexión")
            }
            when (resultado) {
                is ResultadoSustitucion.Exito -> {
                    _estado.update { it.copy(decidiendoId = null) }
                    cargar()
                }
                is ResultadoSustitucion.Error -> _estado.update {
                    it.copy(
                        decidiendoId = null,
                        error = resultado.mensaje,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                            resultado.tipo == TipoErrorSustitucion.CUENTA
                    )
                }
            }
        }
    }

    private fun ajustarSeguimiento() {
        val hayPendientes = _estado.value.propuestasPendientes.isNotEmpty()
        val estadoPedido = _estado.value.detalle?.estado?.uppercase().orEmpty()
        val activo = hayPendientes || estadoPedido in ESTADOS_ACTIVOS

        reloj?.cancel()
        polling?.cancel()
        if (!activo) return

        reloj = viewModelScope.launch {
            while (isActive) {
                _estado.update { it.copy(ahoraEpochMs = System.currentTimeMillis()) }
                delay(1_000)
            }
        }
        // Polling controlado solo con propuestas pendientes o pedido no terminal.
        polling = viewModelScope.launch {
            while (isActive) {
                delay(4_000)
                val r = try {
                    misPedidos.obtenerDetalle(pedidoId)
                } catch (_: Exception) {
                    null
                }
                if (r is ResultadoMisPedidos.Exito) {
                    _estado.update { it.copy(detalle = r.valor, cargando = false) }
                    val sigue =
                        r.valor.sustituciones.any { EstadoSustitucion.esPendiente(it.estado) } ||
                            r.valor.estado.uppercase() in ESTADOS_ACTIVOS
                    if (!sigue) {
                        detenerSeguimiento()
                        break
                    }
                }
            }
        }
    }

    override fun onCleared() {
        detenerSeguimiento()
        super.onCleared()
    }

    companion object {
        private val ESTADOS_ACTIVOS = setOf(
            "CONFIRMADO", "PAGADO", "EN_PICKING", "LISTO", "EN_CAMINO"
        )
    }
}
