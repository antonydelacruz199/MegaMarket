package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.data.repository.MensajesSustitucionCliente
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente
import com.megamarket.cliente.data.repository.ResultadoSustitucion
import com.megamarket.cliente.data.repository.SustitucionesRepository
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.data.repository.formatearTiempoRestante
import com.megamarket.cliente.data.repository.milisRestantesHasta
import com.megamarket.cliente.state.DetalleSustitucionUiState
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

class DetalleSustitucionViewModel(
    savedStateHandle: SavedStateHandle,
    private val sustituciones: SustitucionesRepository,
    private val intervaloConsultaMs: Long = INTERVALO_CONSULTA_MS,
    private val intervaloRelojMs: Long = INTERVALO_RELOJ_MS
) : ViewModel() {

    private val sustitucionId: String =
        savedStateHandle.get<String>(Ruta.ARG_SUSTITUCION).orEmpty()

    private val _estado = MutableStateFlow(DetalleSustitucionUiState())
    val estado: StateFlow<DetalleSustitucionUiState> = _estado.asStateFlow()

    private var consultaJob: Job? = null
    private var relojJob: Job? = null
    private var operacionEnCurso = false

    init {
        if (sustitucionId.isBlank()) {
            _estado.value = DetalleSustitucionUiState(
                cargando = false,
                error = MensajesSustitucionCliente.NO_ENCONTRADA
            )
        } else {
            cargar()
        }
    }

    fun cargar() {
        if (sustitucionId.isBlank()) return
        viewModelScope.launch {
            _estado.update {
                it.copy(cargando = true, error = null, codigoError = null, sesionVencida = false)
            }
            aplicarResultado(consultar(), silencioso = false)
        }
    }

    fun aceptar() = decidir(aceptar = true)

    fun rechazar() = decidir(aceptar = false)

    fun iniciarSeguimiento() {
        val propuesta = _estado.value.propuesta ?: return
        if (!EstadoSustitucion.esPendiente(propuesta.estado)) return
        iniciarReloj(propuesta.fechaLimite)
        if (consultaJob?.isActive == true) return
        consultaJob = viewModelScope.launch {
            while (isActive) {
                delay(intervaloConsultaMs)
                if (!EstadoSustitucion.esPendiente(_estado.value.propuesta?.estado)) break
                aplicarResultado(consultar(), silencioso = true)
            }
        }
    }

    fun detenerSeguimiento() {
        consultaJob?.cancel()
        consultaJob = null
        relojJob?.cancel()
        relojJob = null
    }

    fun consultarEstado() {
        viewModelScope.launch {
            _estado.update { it.copy(consultandoEstado = true, error = null) }
            aplicarResultado(consultar(), silencioso = false)
            _estado.update { it.copy(consultandoEstado = false) }
        }
    }

    private fun decidir(aceptar: Boolean) {
        if (operacionEnCurso || _estado.value.enviando) return
        val actual = _estado.value.propuesta
        if (actual == null || !EstadoSustitucion.esPendiente(actual.estado)) {
            _estado.update {
                it.copy(error = MensajesSustitucionCliente.NO_PENDIENTE)
            }
            return
        }
        operacionEnCurso = true
        viewModelScope.launch {
            _estado.update { it.copy(enviando = true, error = null, codigoError = null) }
            val resultado = try {
                if (aceptar) sustituciones.aceptar(sustitucionId)
                else sustituciones.rechazar(sustitucionId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoSustitucion.Error(TipoErrorSustitucion.RED, MensajesSustitucionCliente.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoSustitucion.Exito -> {
                    aplicarPropuesta(resultado.valor)
                    _estado.update { it.copy(enviando = false, error = null, codigoError = null) }
                    if (EstadoSustitucion.esPendiente(resultado.valor.estado)) {
                        iniciarSeguimiento()
                    } else {
                        detenerSeguimiento()
                        alResolver(resultado.valor)
                    }
                }
                is ResultadoSustitucion.Error -> {
                    resultado.propuesta?.let { aplicarPropuesta(it) }
                    _estado.update {
                        it.copy(
                            enviando = false,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                                resultado.tipo == TipoErrorSustitucion.CUENTA
                        )
                    }
                    if (!EstadoSustitucion.esPendiente(_estado.value.propuesta?.estado)) {
                        detenerSeguimiento()
                        _estado.value.propuesta?.let { alResolver(it) }
                    }
                }
            }
            operacionEnCurso = false
        }
    }

    private suspend fun consultar(): ResultadoSustitucion<PropuestaSustitucionCliente> =
        try {
            sustituciones.obtener(sustitucionId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoSustitucion.Error(TipoErrorSustitucion.RED, MensajesSustitucionCliente.SIN_CONEXION)
        }

    private suspend fun aplicarResultado(
        resultado: ResultadoSustitucion<PropuestaSustitucionCliente>,
        silencioso: Boolean
    ) {
        when (resultado) {
            is ResultadoSustitucion.Exito -> {
                val previa = _estado.value.propuesta?.estado
                aplicarPropuesta(resultado.valor)
                _estado.update {
                    it.copy(
                        cargando = false,
                        error = if (silencioso) it.error else null,
                        codigoError = if (silencioso) it.codigoError else null
                    )
                }
                if (EstadoSustitucion.esPendiente(resultado.valor.estado)) {
                    iniciarReloj(resultado.valor.fechaLimite)
                } else {
                    detenerSeguimiento()
                    if (previa == null || EstadoSustitucion.esPendiente(previa)) {
                        alResolver(resultado.valor)
                    }
                }
            }
            is ResultadoSustitucion.Error -> {
                _estado.update {
                    it.copy(
                        cargando = false,
                        error = if (silencioso && resultado.tipo == TipoErrorSustitucion.RED) {
                            it.error
                        } else {
                            resultado.mensaje
                        },
                        codigoError = resultado.codigo,
                        sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                            resultado.tipo == TipoErrorSustitucion.CUENTA
                    )
                }
                if (resultado.tipo == TipoErrorSustitucion.SESION ||
                    resultado.tipo == TipoErrorSustitucion.CUENTA
                ) {
                    detenerSeguimiento()
                }
            }
        }
    }

    private fun aplicarPropuesta(propuesta: PropuestaSustitucionCliente) {
        val restante = milisRestantesHasta(propuesta.fechaLimite)
        _estado.update {
            it.copy(
                propuesta = propuesta,
                pedidoLocalId = propuesta.pedidoLocalId,
                tiempoRestanteMillis = restante,
                tiempoRestanteTexto = if (EstadoSustitucion.esPendiente(propuesta.estado)) {
                    formatearTiempoRestante(restante)
                } else {
                    null
                }
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

    private suspend fun alResolver(propuesta: PropuestaSustitucionCliente) {
        when (
            val refresco = try {
                sustituciones.refrescarPedido(propuesta.pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoSustitucion.Error(TipoErrorSustitucion.RED, MensajesSustitucionCliente.SIN_CONEXION)
            }
        ) {
            is ResultadoSustitucion.Exito -> {
                _estado.update {
                    it.copy(
                        detallePedidoActualizado = true,
                        pedidoLocalId = refresco.valor ?: it.pedidoLocalId
                    )
                }
            }
            is ResultadoSustitucion.Error -> {
                if (refresco.tipo == TipoErrorSustitucion.SESION ||
                    refresco.tipo == TipoErrorSustitucion.CUENTA
                ) {
                    _estado.update {
                        it.copy(
                            sesionVencida = true,
                            error = refresco.mensaje,
                            codigoError = refresco.codigo
                        )
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
        const val INTERVALO_CONSULTA_MS = 4_000L
        const val INTERVALO_RELOJ_MS = 1_000L
    }
}
