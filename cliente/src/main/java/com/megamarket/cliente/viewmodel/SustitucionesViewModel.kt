package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.data.repository.MensajesSustitucionCliente
import com.megamarket.cliente.data.repository.ResultadoSustitucion
import com.megamarket.cliente.data.repository.SustitucionesRepository
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.state.SustitucionesUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SustitucionesViewModel(
    private val sustituciones: SustitucionesRepository,
    private val intervaloConsultaMs: Long = INTERVALO_CONSULTA_MS
) : ViewModel() {

    private val _estado = MutableStateFlow(SustitucionesUiState())
    val estado: StateFlow<SustitucionesUiState> = _estado.asStateFlow()

    private var consultaJob: Job? = null
    private var cargaEnCurso = false

    init {
        cargar()
    }

    fun cargar() {
        if (cargaEnCurso) return
        cargaEnCurso = true
        viewModelScope.launch {
            _estado.update {
                it.copy(cargando = it.propuestas.isEmpty(), error = null, sesionVencida = false)
            }
            val resultado = try {
                sustituciones.listar()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoSustitucion.Error(TipoErrorSustitucion.RED, MensajesSustitucionCliente.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoSustitucion.Exito -> {
                    val lista = resultado.valor
                    val pendientes = lista.count { EstadoSustitucion.esPendiente(it.estado) }
                    _estado.update {
                        it.copy(
                            cargando = false,
                            actualizando = false,
                            propuestas = lista,
                            pendientes = pendientes,
                            error = null,
                            codigoError = null
                        )
                    }
                }
                is ResultadoSustitucion.Error -> {
                    _estado.update {
                        it.copy(
                            cargando = false,
                            actualizando = false,
                            error = resultado.mensaje,
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
            cargaEnCurso = false
        }
    }

    fun iniciarSeguimiento() {
        if (_estado.value.pendientes <= 0) return
        if (consultaJob?.isActive == true) return
        consultaJob = viewModelScope.launch {
            while (isActive) {
                delay(intervaloConsultaMs)
                if (_estado.value.pendientes <= 0) break
                refrescarSilencioso()
            }
        }
    }

    fun detenerSeguimiento() {
        consultaJob?.cancel()
        consultaJob = null
    }

    private suspend fun refrescarSilencioso() {
        _estado.update { it.copy(actualizando = true) }
        val resultado = try {
            sustituciones.listar()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoSustitucion.Error(TipoErrorSustitucion.RED, MensajesSustitucionCliente.SIN_CONEXION)
        }
        when (resultado) {
            is ResultadoSustitucion.Exito -> {
                val lista = resultado.valor
                val pendientes = lista.count { EstadoSustitucion.esPendiente(it.estado) }
                _estado.update {
                    it.copy(
                        actualizando = false,
                        propuestas = lista,
                        pendientes = pendientes,
                        error = if (resultado.valor.isEmpty()) it.error else null
                    )
                }
                if (pendientes <= 0) detenerSeguimiento()
            }
            is ResultadoSustitucion.Error -> {
                _estado.update {
                    it.copy(
                        actualizando = false,
                        error = if (resultado.tipo == TipoErrorSustitucion.RED) it.error else resultado.mensaje,
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

    override fun onCleared() {
        detenerSeguimiento()
        super.onCleared()
    }

    companion object {
        const val INTERVALO_CONSULTA_MS = 4_000L
    }
}
