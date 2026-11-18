package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.LineaPreparacion
import com.megamarket.picker.data.repository.MensajesSesion
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.ResultadoEscaneo
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.data.repository.TipoResultadoEscaneo
import com.megamarket.picker.escaneo.GestorEscaneo
import com.megamarket.picker.escaneo.SolicitudEscaneo
import com.megamarket.picker.state.EscanerUiState
import com.megamarket.picker.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class EscanerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: PedidosPickerRepository,
    private val gestor: GestorEscaneo = GestorEscaneo()
) : ViewModel() {

    private val pedidoId: String = savedStateHandle.get<String>(Ruta.ARG_PEDIDO).orEmpty()
    private val detalleId: String = savedStateHandle.get<String>(Ruta.ARG_DETALLE).orEmpty()
    private val _estado = MutableStateFlow(EscanerUiState())
    val estado: StateFlow<EscanerUiState> = _estado.asStateFlow()
    private var lineaConfirmada = false
    private var observacion: Job? = null
    private var uuidFaltante: String? = null

    init {
        if (pedidoId.isBlank() || detalleId.isBlank()) {
            _estado.value = EscanerUiState(cargando = false, mensaje = "No se encontró el producto del pedido")
        } else {
            cargarLinea()
            observarOperaciones()
        }
    }

    fun codigoDetectado(codigo: String?) {
        val solicitud = gestor.frame(codigo) ?: return
        enviar(solicitud)
    }

    fun enviarManual(codigo: String) {
        val solicitud = gestor.manual(codigo) ?: return
        enviar(solicitud)
    }

    fun reintentar() {
        val solicitud = gestor.reintentar() ?: return
        enviar(solicitud)
    }

    fun marcarFaltante() {
        val uuid = uuidFaltante ?: UUID.randomUUID().toString().also { uuidFaltante = it }
        viewModelScope.launch {
            _estado.update { it.copy(enviando = true, mensaje = null, puedeReintentar = false) }
            val resultado = try {
                repositorio.marcarFaltante(pedidoId, detalleId, uuid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoEscaneo.PendienteLocal(
                    mensaje = MensajesSesion.SIN_CONEXION,
                    cantidadArmada = _estado.value.cantidadArmada,
                    cantidadPendienteLocal = _estado.value.cantidadPendienteLocal,
                    estadoLinea = _estado.value.estadoLinea
                )
            }
            aplicarResultado(resultado)
            if (resultado is ResultadoEscaneo.Confirmado &&
                resultado.tipo != TipoResultadoEscaneo.SESION_VENCIDA &&
                resultado.tipo != TipoResultadoEscaneo.PENDIENTE_LOCAL
            ) {
                uuidFaltante = null
            }
        }
    }

    fun continuar() {
        gestor.continuar()
        _estado.update {
            it.copy(
                resultado = null,
                mensaje = null,
                vibrar = false,
                puedeReintentar = false
            )
        }
    }

    private fun observarOperaciones() {
        observacion?.cancel()
        observacion = viewModelScope.launch {
            repositorio.observarOperacionesDetalle(detalleId).collect { ops ->
                _estado.update { actual ->
                    val pendiente = ops.filter {
                        it.operacion == "ESCANEAR" &&
                            it.estado in setOf("PENDIENTE", "ENVIANDO")
                    }.sumOf { it.cantidad }
                    val faltante = ops.any {
                        it.operacion == "MARCAR_FALTANTE" &&
                            it.estado in setOf("PENDIENTE", "ENVIANDO")
                    }
                    actual.copy(
                        operaciones = ops,
                        cantidadPendienteLocal = pendiente,
                        faltantePendiente = faltante
                    )
                }
            }
        }
    }

    private fun cargarLinea() {
        viewModelScope.launch {
            val resultado = try {
                repositorio.obtenerPreparacion(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
            }
            _estado.update { actual ->
                when (resultado) {
                    is ResultadoOperacion.Exito -> {
                        val linea = resultado.valor.grupos
                            .flatMap { it.lineas }
                            .firstOrNull { it.id == detalleId }
                        if (linea == null || lineaConfirmada) {
                            actual.copy(cargando = false)
                        } else {
                            actual.copy(
                                cargando = false,
                                nombreProducto = linea.nombre,
                                cantidadSolicitada = linea.cantidad,
                                cantidadArmada = linea.cantidadArmada,
                                cantidadPendienteLocal = linea.cantidadPendienteLocal,
                                estadoLinea = linea.estadoPicking,
                                faltantePendiente = linea.faltantePendiente,
                                imagenUrl = linea.imagenUrl,
                                operaciones = linea.operacionesLocales
                            )
                        }
                    }
                    is ResultadoOperacion.Error -> actual.copy(
                        cargando = false,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
        }
    }

    private fun enviar(solicitud: SolicitudEscaneo) {
        viewModelScope.launch {
            _estado.update {
                it.copy(enviando = true, mensaje = null, puedeReintentar = false, vibrar = false)
            }
            val resultado = try {
                repositorio.registrarEscaneo(
                    pedidoId = pedidoId,
                    detalleId = detalleId,
                    uuidOperacion = solicitud.uuidOperacion,
                    codigoBarras = solicitud.codigoBarras
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoEscaneo.PendienteLocal(
                    mensaje = MensajesSesion.SIN_CONEXION,
                    cantidadArmada = _estado.value.cantidadArmada,
                    cantidadPendienteLocal = _estado.value.cantidadPendienteLocal + 1,
                    estadoLinea = _estado.value.estadoLinea
                )
            }
            aplicarResultado(resultado)
        }
    }

    private fun aplicarResultado(resultado: ResultadoEscaneo) {
        when (resultado) {
            is ResultadoEscaneo.Confirmado -> aplicarConfirmado(resultado)
            is ResultadoEscaneo.PendienteLocal -> {
                gestor.resultadoListo()
                _estado.update {
                    it.copy(
                        enviando = false,
                        resultado = TipoResultadoEscaneo.PENDIENTE_LOCAL,
                        mensaje = resultado.mensaje,
                        cantidadArmada = resultado.cantidadArmada,
                        cantidadPendienteLocal = resultado.cantidadPendienteLocal,
                        estadoLinea = resultado.estadoLinea,
                        puedeReintentar = false,
                        vibrar = false
                    )
                }
            }
            is ResultadoEscaneo.Fallo -> aplicarFallo(resultado)
        }
    }

    private fun aplicarConfirmado(resultado: ResultadoEscaneo.Confirmado) {
        gestor.resultadoListo()
        val linea = resultado.linea
        if (linea != null && resultado.tipo == TipoResultadoEscaneo.CORRECTO) {
            lineaConfirmada = true
        }
        _estado.update { actual ->
            actual.conLinea(linea).copy(
                enviando = false,
                resultado = resultado.tipo,
                mensaje = resultado.mensaje,
                vibrar = resultado.vibrar,
                idVibracion = if (resultado.vibrar) actual.idVibracion + 1 else actual.idVibracion,
                puedeReintentar = false,
                cantidadPendienteLocal = resultado.cantidadPendienteLocal,
                sesionVencida = resultado.tipo == TipoResultadoEscaneo.SESION_VENCIDA
            )
        }
    }

    private fun aplicarFallo(resultado: ResultadoEscaneo.Fallo) {
        if (resultado.conservarOperacion) gestor.falloRecuperable() else gestor.resultadoListo()
        _estado.update {
            it.copy(
                enviando = false,
                resultado = null,
                mensaje = resultado.mensaje,
                vibrar = false,
                puedeReintentar = resultado.conservarOperacion,
                sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                    resultado.tipo == TipoErrorPedido.CUENTA
            )
        }
    }
}

private fun EscanerUiState.conLinea(linea: LineaPreparacion?): EscanerUiState {
    if (linea == null) return this
    return copy(
        nombreProducto = linea.nombre.ifBlank { nombreProducto },
        cantidadSolicitada = linea.cantidad,
        cantidadArmada = linea.cantidadArmada,
        cantidadPendienteLocal = linea.cantidadPendienteLocal,
        estadoLinea = linea.estadoPicking,
        faltantePendiente = linea.faltantePendiente,
        imagenUrl = linea.imagenUrl ?: imagenUrl,
        operaciones = linea.operacionesLocales.ifEmpty { operaciones }
    )
}
