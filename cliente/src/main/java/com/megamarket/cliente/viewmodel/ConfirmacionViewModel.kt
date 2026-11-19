package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.MisPedidosRepository
import com.megamarket.cliente.data.repository.MODALIDAD_ONLINE
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.PedidoRepository
import com.megamarket.cliente.data.repository.ResultadoMisPedidos
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.data.repository.ResultadoSincronizacion
import com.megamarket.cliente.data.repository.SincronizacionRepository
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.state.ConfirmacionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfirmacionViewModel(
    savedStateHandle: SavedStateHandle,
    private val pedidos: PedidoRepository,
    private val pagos: PagosRepository,
    private val sincronizacion: SincronizacionRepository,
    private val misPedidos: MisPedidosRepository? = null
) : ViewModel() {

    private val pedidoId: Long = savedStateHandle.get<Long>(ARG_PEDIDO)
        ?: savedStateHandle.get<String>(ARG_PEDIDO)?.toLongOrNull()
        ?: 0L

    private val _estado = MutableStateFlow(ConfirmacionUiState())
    val estado: StateFlow<ConfirmacionUiState> = _estado.asStateFlow()

    init {
        cargarPedido(forzarSincronizacion = true)
    }

    fun cargarPedido(forzarSincronizacion: Boolean = false) {
        viewModelScope.launch {
            _estado.update {
                it.copy(
                    cargando = true,
                    sincronizando = forzarSincronizacion,
                    error = null,
                    cancelando = false
                )
            }
            if (forzarSincronizacion) {
                sincronizarConReintentos()
            }
            val pedido = try {
                pedidos.obtenerPedido(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _estado.value = ConfirmacionUiState(cargando = false, error = "No se pudo cargar el pedido")
                return@launch
            }
            if (pedido == null) {
                _estado.value = ConfirmacionUiState(cargando = false, error = "No se encontró el pedido")
                return@launch
            }
            val actualizado = refrescarEstadoRemoto(pedido) ?: pedido
            _estado.update {
                it.copy(
                    cargando = false,
                    sincronizando = false,
                    pedido = actualizado,
                    totalPropuesto = if (actualizado.estado == "REQUIERE_REVISION") {
                        runCatching { pedidos.totalActual(actualizado.id) }.getOrNull()
                    } else {
                        null
                    },
                    error = null
                )
            }
            consultarPago()
            // Asegurar remoteId tras crear el pedido.
            if (necesitaSyncParaPagar(_estado.value.pedido) && forzarSincronizacion) {
                reintentarHastaListoParaPagar()
            }
        }
    }

    fun reintentarEnvio() {
        viewModelScope.launch {
            try {
                pedidos.reintentarEnvio(pedidoId)
                cargarPedido(forzarSincronizacion = true)
            } catch (e: Exception) {
                _estado.update { it.copy(error = e.message) }
            }
        }
    }

    fun sincronizarYRecargar() {
        viewModelScope.launch {
            _estado.update { it.copy(sincronizando = true, error = null) }
            val motivo = sincronizarConReintentos()
            val pedido = try {
                pedidos.obtenerPedido(pedidoId)
            } catch (_: Exception) {
                null
            }
            if (pedido != null) {
                val actualizado = refrescarEstadoRemoto(pedido) ?: pedido
                _estado.update {
                    it.copy(
                        sincronizando = false,
                        pedido = actualizado,
                        error = motivo?.takeIf {
                            necesitaSyncParaPagar(actualizado)
                        }
                    )
                }
                consultarPago()
            } else {
                _estado.update {
                    it.copy(sincronizando = false, error = "No se pudo actualizar el pedido")
                }
            }
        }
    }

    private suspend fun refrescarEstadoRemoto(pedido: Pedido): Pedido? {
        val remoto = pedido.remoteId?.trim().orEmpty()
        val repo = misPedidos ?: return null
        if (remoto.isEmpty()) return null
        return try {
            when (repo.obtenerDetalle(remoto)) {
                is ResultadoMisPedidos.Exito -> pedidos.obtenerPedido(pedidoId)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun refrescarPago() {
        viewModelScope.launch {
            val pedido = try {
                pedidos.obtenerPedido(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (pedido != null) {
                _estado.update { it.copy(pedido = pedido) }
            }
            consultarPago()
        }
    }

    private suspend fun reintentarHastaListoParaPagar() {
        repeat(MAX_REINTENTOS_PAGO) { intento ->
            if (!necesitaSyncParaPagar(_estado.value.pedido)) return
            _estado.update { it.copy(sincronizando = true) }
            delay(INTERVAL_REINTENTO_MS + intento * 250L)
            val motivo = sincronizarConReintentos(maxIntentos = 2)
            val pedido = try {
                pedidos.obtenerPedido(pedidoId)
            } catch (_: Exception) {
                null
            } ?: return@repeat
            _estado.update {
                it.copy(
                    pedido = pedido,
                    sincronizando = false,
                    error = if (necesitaSyncParaPagar(pedido)) motivo else null
                )
            }
            consultarPago()
            if (!necesitaSyncParaPagar(pedido)) return
        }
        _estado.update { it.copy(sincronizando = false) }
    }

    /**
     * @return último motivo de reintento si el pedido ONLINE aún no tiene remoteId.
     */
    private suspend fun sincronizarConReintentos(maxIntentos: Int = 3): String? {
        var ultimoMotivo: String? = null
        repeat(maxIntentos) { intento ->
            val resultado = try {
                sincronizacion.sincronizarPendientes()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoSincronizacion.Reintentar("Sin conexión")
            }
            when (resultado) {
                is ResultadoSincronizacion.Exito,
                is ResultadoSincronizacion.SinTrabajo -> {
                    val pedido = runCatching { pedidos.obtenerPedido(pedidoId) }.getOrNull()
                    if (!necesitaSyncParaPagar(pedido)) return null
                }
                is ResultadoSincronizacion.Reintentar -> {
                    ultimoMotivo = resultado.motivo
                    if (intento < maxIntentos - 1) delay(400L * (intento + 1))
                }
            }
        }
        return ultimoMotivo
    }

    private fun necesitaSyncParaPagar(pedido: Pedido?): Boolean {
        if (pedido == null) return false
        if (pedido.estado == "CANCELADO" || pedido.estado == "PAGADO" || pedido.estado == "LISTO") {
            return false
        }
        // Cualquier modalidad: hace falta remoteId tras crear el pedido.
        if (pedido.estado == "PENDIENTE_CONFIRMACION") return true
        return pedido.remoteId.isNullOrBlank()
    }

    private suspend fun consultarPago() {
        val pedido = _estado.value.pedido ?: return
        val remoto = pedido.remoteId?.trim().orEmpty()
        if (remoto.isEmpty() || pedido.modalidadPago.isNullOrBlank()) {
            return
        }
        val resultado = try {
            pagos.obtenerPorPedido(remoto)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
        if (resultado is ResultadoPagos.Exito) {
            _estado.update { it.copy(pagoConfirmado = resultado.valor.confirmado) }
        }
    }

    fun aceptarPrecios() {
        val total = _estado.value.totalPropuesto ?: return
        viewModelScope.launch {
            try {
                pedidos.aceptarPrecios(pedidoId, total)
                cargarPedido(forzarSincronizacion = true)
            } catch (e: Exception) {
                _estado.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Cancela según el estado:
     * - REQUIERE_REVISION / PENDIENTE sin remoteId → local
     * - CONFIRMADO con pago pendiente → API
     */
    fun cancelarPedido() {
        val pedido = _estado.value.pedido ?: return
        if (_estado.value.cancelando) return
        viewModelScope.launch {
            _estado.update { it.copy(cancelando = true, error = null) }
            try {
                when {
                    pedido.estado == "REQUIERE_REVISION" -> {
                        pedidos.cancelarPendiente(pedidoId)
                    }
                    pedido.remoteId.isNullOrBlank() &&
                        pedido.estado in setOf("PENDIENTE_CONFIRMACION", "REQUIERE_REVISION") -> {
                        pedidos.abortarLocalSinEnviar(pedidoId)
                    }
                    pedido.estado in setOf(
                        "RECIBIDO",
                        "EN_PICKING",
                        Pedido.ESTADO_CONFIRMADO
                    ) &&
                        _estado.value.pagoConfirmado != true &&
                        !pedido.remoteId.isNullOrBlank() -> {
                        when (val r = pagos.cancelarPedido(pedido.remoteId!!)) {
                            is ResultadoPagos.Exito -> pedidos.marcarCanceladoLocal(pedidoId)
                            is ResultadoPagos.Error -> {
                                _estado.update {
                                    it.copy(cancelando = false, error = r.mensaje)
                                }
                                return@launch
                            }
                        }
                    }
                    else -> {
                        _estado.update {
                            it.copy(
                                cancelando = false,
                                error = "Este pedido no se puede cancelar en su estado actual"
                            )
                        }
                        return@launch
                    }
                }
                cargarPedido(forzarSincronizacion = false)
            } catch (e: Exception) {
                _estado.update { it.copy(cancelando = false, error = e.message ?: "No se pudo cancelar") }
            }
        }
    }

    companion object {
        const val ARG_PEDIDO = "pedidoId"
        private const val MAX_REINTENTOS_PAGO = 4
        private const val INTERVAL_REINTENTO_MS = 600L
    }
}
