package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.EstadoSustitucion
import com.megamarket.picker.data.repository.MensajesSustitucion
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.SustitucionPropuesta
import com.megamarket.picker.data.repository.SustitucionesRepository
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.data.repository.formatearTiempoRestante
import com.megamarket.picker.data.repository.milisRestantesHasta
import com.megamarket.picker.state.SustitucionUiState
import com.megamarket.picker.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SustitucionViewModel(
    savedStateHandle: SavedStateHandle,
    private val sustituciones: SustitucionesRepository,
    private val pedidos: PedidosPickerRepository,
    private val intervaloConsultaMs: Long = INTERVALO_CONSULTA_MS,
    private val intervaloRelojMs: Long = INTERVALO_RELOJ_MS
) : ViewModel() {

    private val pedidoId: String = savedStateHandle.get<String>(Ruta.ARG_PEDIDO).orEmpty()
    private val detalleId: String = savedStateHandle.get<String>(Ruta.ARG_DETALLE).orEmpty()

    private val _estado = MutableStateFlow(SustitucionUiState())
    val estado: StateFlow<SustitucionUiState> = _estado.asStateFlow()

    private var consultaJob: Job? = null
    private var relojJob: Job? = null
    private var observacionJob: Job? = null
    private var operacionEnCurso = false
    private var candidatosCargados = false
    private var cargaCandidatosEnCurso = false

    init {
        if (pedidoId.isBlank() || detalleId.isBlank()) {
            _estado.value = SustitucionUiState(
                cargando = false,
                error = MensajesSustitucion.NO_ENCONTRADA
            )
        } else {
            observarLinea()
        }
    }

    fun cargar() {
        candidatosCargados = false
        cargaCandidatosEnCurso = false
        _estado.update {
            it.copy(cargando = true, error = null, codigoError = null, sesionVencida = false)
        }
        observarLinea()
    }

    fun seleccionar(candidatoId: String) {
        if (_estado.value.enviando) return
        if (EstadoSustitucion.esPendiente(_estado.value.propuesta?.estado)) return
        _estado.update { it.copy(seleccionadoId = candidatoId, error = null, codigoError = null) }
    }

    fun proponer() {
        if (operacionEnCurso || _estado.value.enviando) return
        if (_estado.value.faltantePendienteLocal) {
            _estado.update {
                it.copy(
                    error = MensajesSustitucion.FALTANTE_PENDIENTE,
                    codigoError = "FALTANTE_PENDIENTE_LOCAL"
                )
            }
            return
        }
        val seleccionado = _estado.value.seleccionadoId
        if (seleccionado.isNullOrBlank()) {
            _estado.update { it.copy(error = MensajesSustitucion.SELECCIONA) }
            return
        }
        if (EstadoSustitucion.esPendiente(_estado.value.propuesta?.estado)) {
            _estado.update { it.copy(error = MensajesSustitucion.PROPUESTA_EN_CURSO) }
            return
        }

        operacionEnCurso = true
        viewModelScope.launch {
            _estado.update { it.copy(enviando = true, error = null, codigoError = null) }
            val resultado = try {
                sustituciones.proponer(
                    pedidoId = pedidoId,
                    detalleId = detalleId,
                    productoSustitutoId = seleccionado,
                    cantidad = _estado.value.cantidadLinea.takeIf { it > 0 }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSustitucion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> {
                    aplicarPropuesta(resultado.valor)
                    _estado.update { it.copy(enviando = false, error = null, codigoError = null) }
                    if (EstadoSustitucion.esPendiente(resultado.valor.estado)) {
                        iniciarSeguimiento()
                    } else {
                        alResolverPropuesta()
                    }
                }
                is ResultadoOperacion.Error -> {
                    _estado.update {
                        it.copy(
                            enviando = false,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                }
            }
            operacionEnCurso = false
        }
    }

    fun iniciarSeguimiento() {
        val propuesta = _estado.value.propuesta ?: return
        if (!EstadoSustitucion.esPendiente(propuesta.estado)) return
        iniciarReloj(propuesta.fechaLimite)
        if (consultaJob?.isActive == true) return
        consultaJob = viewModelScope.launch {
            while (isActive) {
                delay(intervaloConsultaMs)
                if (!EstadoSustitucion.esPendiente(_estado.value.propuesta?.estado)) break
                consultarEstadoInterno(silencioso = true)
            }
        }
    }

    fun detenerSeguimiento() {
        consultaJob?.cancel()
        consultaJob = null
        relojJob?.cancel()
        relojJob = null
    }

    /** Envía aviso SIN_STOCK a Sustituciones del cliente (no elimina la línea). */
    fun informarSinStock() {
        if (operacionEnCurso || _estado.value.resolviendo || _estado.value.enviando) return
        if (!_estado.value.sinStockAlmacen &&
            !EstadoSustitucion.esSinStock(_estado.value.propuesta?.estado)
        ) {
            _estado.update {
                it.copy(
                    error = "Solo se informa sin stock cuando no hay productos sustitutos",
                    codigoError = "SIN_CANDIDATOS"
                )
            }
            return
        }
        if (EstadoSustitucion.esSinStock(_estado.value.propuesta?.estado) ||
            _estado.value.avisoSinStockEnviado
        ) {
            _estado.update {
                it.copy(
                    avisoSinStockEnviado = true,
                    error = null,
                    codigoError = null
                )
            }
            return
        }
        operacionEnCurso = true
        viewModelScope.launch {
            _estado.update { it.copy(resolviendo = true, error = null, codigoError = null) }
            val resultado = try {
                sustituciones.avisarSinStock(pedidoId, detalleId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSustitucion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> {
                    aplicarPropuesta(resultado.valor)
                    _estado.update {
                        it.copy(
                            resolviendo = false,
                            avisoSinStockEnviado = true,
                            detalleActualizado = true,
                            error = null,
                            codigoError = null
                        )
                    }
                }
                is ResultadoOperacion.Error -> _estado.update {
                    it.copy(
                        resolviendo = false,
                        error = resultado.mensaje,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
            operacionEnCurso = false
        }
    }

    /** Tras rechazo del cliente: quitar línea del pedido. */
    fun resolverSinProducto() {
        if (operacionEnCurso || _estado.value.resolviendo || _estado.value.enviando) return
        if (!EstadoSustitucion.esRechazada(_estado.value.propuesta?.estado)) {
            _estado.update {
                it.copy(
                    error = "Solo se quita el producto después de un rechazo del cliente",
                    codigoError = "ELIMINADO_SIN_RESOLUCION"
                )
            }
            return
        }
        operacionEnCurso = true
        viewModelScope.launch {
            _estado.update { it.copy(resolviendo = true, error = null, codigoError = null) }
            val resultado = try {
                pedidos.resolverLineaSinProducto(pedidoId, detalleId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSustitucion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> _estado.update {
                    it.copy(
                        resolviendo = false,
                        lineaResuelta = true,
                        detalleActualizado = true,
                        error = null,
                        codigoError = null
                    )
                }
                is ResultadoOperacion.Error -> _estado.update {
                    it.copy(
                        resolviendo = false,
                        error = resultado.mensaje,
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
            operacionEnCurso = false
        }
    }

    fun consultarEstado() {
        viewModelScope.launch { consultarEstadoInterno(silencioso = false) }
    }

    private fun observarLinea() {
        if (pedidoId.isBlank() || detalleId.isBlank()) return
        observacionJob?.cancel()
        observacionJob = viewModelScope.launch {
            pedidos.observarPreparacion(pedidoId).collect { preparacion ->
                val linea = preparacion?.grupos
                    ?.flatMap { it.lineas }
                    ?.firstOrNull { it.id == detalleId }
                if (linea == null) {
                    _estado.update {
                        it.copy(
                            cargando = false,
                            error = it.error ?: MensajesSustitucion.NO_ENCONTRADA
                        )
                    }
                    return@collect
                }
                _estado.update {
                    it.copy(
                        nombreProductoOriginal = linea.nombre,
                        cantidadLinea = (linea.cantidad - linea.cantidadArmada).coerceAtLeast(0),
                        faltantePendienteLocal = linea.faltantePendiente
                    )
                }
                when {
                    linea.faltantePendiente -> {
                        candidatosCargados = false
                        _estado.update {
                            it.copy(
                                cargando = false,
                                candidatos = emptyList(),
                                error = MensajesSustitucion.FALTANTE_PENDIENTE,
                                codigoError = "FALTANTE_PENDIENTE_LOCAL"
                            )
                        }
                    }
                    linea.estadoPicking.equals("FALTANTE", ignoreCase = true) -> {
                        if (!candidatosCargados && !cargaCandidatosEnCurso) {
                            cargarCandidatos()
                        }
                    }
                    EstadoSustitucion.estaResuelta(_estado.value.propuesta?.estado) ||
                        linea.estadoPicking.equals("SUSTITUIDO", ignoreCase = true) -> {
                        _estado.update { it.copy(cargando = false) }
                    }
                    else -> {
                        _estado.update {
                            it.copy(
                                cargando = false,
                                error = MensajesSustitucion.SOLO_FALTANTE,
                                codigoError = "ESTADO_PICKING_INVALIDO"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun cargarCandidatos() {
        if (cargaCandidatosEnCurso) return
        cargaCandidatosEnCurso = true
        viewModelScope.launch {
            _estado.update {
                it.copy(cargando = true, error = null, codigoError = null, sesionVencida = false)
            }
            val resultado = try {
                sustituciones.listarCandidatos(pedidoId, detalleId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSustitucion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> {
                    candidatosCargados = true
                    val lista = resultado.valor
                    _estado.update {
                        it.copy(
                            cargando = false,
                            faltantePendienteLocal = false,
                            cantidadLinea = lista.cantidadLinea,
                            precioOriginalCentimos = lista.precioOriginalCentimos,
                            candidatos = lista.candidatos,
                            sinStockAlmacen = lista.candidatos.isEmpty(),
                            avisoSinStockEnviado = it.avisoSinStockEnviado ||
                                EstadoSustitucion.esSinStock(it.propuesta?.estado),
                            error = if (lista.candidatos.isEmpty()) {
                                MensajesSustitucion.SIN_CANDIDATOS
                            } else {
                                null
                            },
                            codigoError = null
                        )
                    }
                }
                is ResultadoOperacion.Error -> {
                    val faltanteLocal = resultado.codigo == "FALTANTE_PENDIENTE_LOCAL"
                    if (!faltanteLocal) candidatosCargados = true
                    _estado.update {
                        it.copy(
                            cargando = false,
                            faltantePendienteLocal = faltanteLocal,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                }
            }
            cargaCandidatosEnCurso = false
        }
    }

    private suspend fun consultarEstadoInterno(silencioso: Boolean) {
        val id = _estado.value.propuesta?.id ?: return
        if (!silencioso) {
            _estado.update { it.copy(consultandoEstado = true, error = null) }
        }
        val resultado = try {
            sustituciones.consultarSustitucion(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSustitucion.SIN_CONEXION)
        }
        when (resultado) {
            is ResultadoOperacion.Exito -> {
                val previa = _estado.value.propuesta?.estado
                aplicarPropuesta(resultado.valor)
                _estado.update {
                    it.copy(
                        consultandoEstado = false,
                        error = if (silencioso) it.error else null,
                        codigoError = if (silencioso) it.codigoError else null
                    )
                }
                if (EstadoSustitucion.esPendiente(resultado.valor.estado)) {
                    iniciarReloj(resultado.valor.fechaLimite)
                } else {
                    detenerSeguimiento()
                    if (previa == null || EstadoSustitucion.esPendiente(previa)) {
                        alResolverPropuesta()
                    }
                }
            }
            is ResultadoOperacion.Error -> {
                _estado.update {
                    it.copy(
                        consultandoEstado = false,
                        error = if (silencioso && resultado.tipo == TipoErrorPedido.RED) {
                            it.error
                        } else {
                            resultado.mensaje
                        },
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
                if (resultado.tipo == TipoErrorPedido.SESION ||
                    resultado.tipo == TipoErrorPedido.CUENTA
                ) {
                    detenerSeguimiento()
                }
            }
        }
    }

    private fun aplicarPropuesta(propuesta: SustitucionPropuesta) {
        val restante = milisRestantesHasta(propuesta.fechaLimite)
        _estado.update {
            it.copy(
                propuesta = propuesta,
                tiempoRestanteMillis = restante,
                tiempoRestanteTexto = if (EstadoSustitucion.esPendiente(propuesta.estado)) {
                    formatearTiempoRestante(restante)
                } else {
                    null
                },
                seleccionadoId = propuesta.productoSustitutoId ?: it.seleccionadoId
            )
        }
    }

    private fun iniciarReloj(fechaLimite: String?) {
        relojJob?.cancel()
        if (fechaLimite.isNullOrBlank()) return
        relojJob = viewModelScope.launch {
            while (isActive) {
                val restante = milisRestantesHasta(fechaLimite)
                _estado.update {
                    it.copy(
                        tiempoRestanteMillis = restante,
                        tiempoRestanteTexto = formatearTiempoRestante(restante)
                    )
                }
                if (restante <= 0L) break
                delay(intervaloRelojMs)
            }
        }
    }

    private suspend fun alResolverPropuesta() {
        when (
            val prep = try {
                sustituciones.refrescarPreparacion(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSustitucion.SIN_CONEXION)
            }
        ) {
            is ResultadoOperacion.Exito -> {
                _estado.update { it.copy(detalleActualizado = true) }
            }
            is ResultadoOperacion.Error -> {
                if (prep.tipo == TipoErrorPedido.SESION || prep.tipo == TipoErrorPedido.CUENTA) {
                    _estado.update {
                        it.copy(
                            sesionVencida = true,
                            error = prep.mensaje,
                            codigoError = prep.codigo
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        detenerSeguimiento()
        observacionJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val INTERVALO_CONSULTA_MS = 4_000L
        const val INTERVALO_RELOJ_MS = 1_000L
    }
}
