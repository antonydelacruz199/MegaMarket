package com.megamarket.cliente.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.EstadoSustitucion
import com.megamarket.cliente.data.repository.MensajesSustitucionCliente
import com.megamarket.cliente.data.repository.PropuestaSustitucionCliente
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
            aplicarPagina(listarPagina(1), anexar = false, silencioso = false)
            cargaEnCurso = false
            if (_estado.value.pendientes > 0) iniciarSeguimiento()
        }
    }

    fun cargarMas() {
        val actual = _estado.value
        if (!actual.hayMas || actual.cargandoMas || cargaEnCurso) return
        viewModelScope.launch {
            _estado.update { it.copy(cargandoMas = true, error = null) }
            aplicarPagina(listarPagina(actual.pagina + 1), anexar = true, silencioso = false)
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
        // Refresco silencioso: solo la primera página para detectar pendientes.
        aplicarPagina(listarPagina(1), anexar = false, silencioso = true)
        if (_estado.value.pendientes <= 0) detenerSeguimiento()
    }

    private suspend fun listarPagina(
        page: Int
    ): ResultadoSustitucion<SustitucionesRepository.PaginaSustituciones> =
        try {
            sustituciones.listar(page = page)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoSustitucion.Error(TipoErrorSustitucion.RED, MensajesSustitucionCliente.SIN_CONEXION)
        }

    private fun aplicarPagina(
        resultado: ResultadoSustitucion<SustitucionesRepository.PaginaSustituciones>,
        anexar: Boolean,
        silencioso: Boolean
    ) {
        when (resultado) {
            is ResultadoSustitucion.Exito -> {
                val lista = resultado.valor.propuestas
                val base = if (anexar) {
                    (_estado.value.propuestas + lista).distinctBy { it.id }
                } else {
                    lista
                }
                val ordenada = base.sortedWith(
                    compareByDescending<PropuestaSustitucionCliente> {
                        EstadoSustitucion.esRequiereAtencion(it.estado)
                    }.thenByDescending { it.fechaPropuesta.orEmpty() }
                )
                val atencion = ordenada.count { EstadoSustitucion.esRequiereAtencion(it.estado) }
                _estado.update {
                    it.copy(
                        cargando = false,
                        cargandoMas = false,
                        actualizando = false,
                        propuestas = ordenada,
                        pagina = resultado.valor.pagina,
                        hayMas = resultado.valor.hayMas,
                        pendientes = atencion,
                        error = if (silencioso && ordenada.isEmpty()) it.error else null,
                        codigoError = null
                    )
                }
            }
            is ResultadoSustitucion.Error -> {
                _estado.update {
                    it.copy(
                        cargando = false,
                        cargandoMas = false,
                        actualizando = false,
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

    override fun onCleared() {
        detenerSeguimiento()
        super.onCleared()
    }

    companion object {
        const val INTERVALO_CONSULTA_MS = 4_000L
    }
}
