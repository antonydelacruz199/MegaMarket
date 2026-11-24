package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.LineaPreparacion
import com.megamarket.picker.data.repository.MensajesSesion
import com.megamarket.picker.data.repository.PedidosPickerRepository
import com.megamarket.picker.data.repository.PreparacionPedido
import com.megamarket.picker.data.repository.ResultadoEscaneo
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.data.repository.TipoResultadoEscaneo
import com.megamarket.picker.data.repository.listoParaSalida
import com.megamarket.picker.escaneo.GestorEscaneo
import com.megamarket.picker.escaneo.SolicitudEscaneo
import com.megamarket.picker.state.EscanerUiState
import com.megamarket.picker.state.ResumenProgresoLinea
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
    private var esperandoNavegarSustitucion = false

    init {
        if (pedidoId.isBlank() || detalleId.isBlank()) {
            _estado.value = EscanerUiState(cargando = false, mensaje = "No se encontró el producto del pedido")
        } else {
            cargarLinea()
            observarOperaciones()
        }
    }

    fun codigoDetectado(codigo: String?) {
        if (_estado.value.lineaCompleta || _estado.value.pedidoConfirmado) return
        val solicitud = gestor.frame(codigo) ?: return
        enviar(solicitud)
    }

    fun enviarManual(codigo: String) {
        if (_estado.value.lineaCompleta || _estado.value.pedidoConfirmado) return
        val solicitud = gestor.manual(codigo) ?: return
        enviar(solicitud)
    }

    fun reintentar() {
        val solicitud = gestor.reintentar() ?: return
        enviar(solicitud)
    }

    fun marcarFaltante() {
        if (_estado.value.lineaCompleta &&
            !_estado.value.estadoLinea.equals("FALTANTE", true)
        ) {
            return
        }
        if (_estado.value.enviando || _estado.value.faltantePendiente) return
        esperandoNavegarSustitucion = true
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
            intentarNavegarSustitucion()
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

    fun confirmarPedido() {
        if (!_estado.value.pedidoListoParaConfirmar ||
            _estado.value.confirmandoPedido ||
            _estado.value.pedidoConfirmado
        ) {
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(confirmandoPedido = true, mensaje = null) }
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
                        confirmandoPedido = false,
                        pedidoConfirmado = true,
                        mensaje = null
                    )
                }
                is ResultadoOperacion.Error -> _estado.update {
                    it.copy(
                        confirmandoPedido = false,
                        mensaje = resultado.mensaje,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
        }
    }

    fun consumirNavegacionSustitucion() {
        _estado.update { it.copy(navegarASustitucion = false) }
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
                    val actualizado = actual.copy(
                        operaciones = ops,
                        cantidadPendienteLocal = pendiente,
                        faltantePendiente = faltante,
                        lineaCompleta = actual.copy(
                            cantidadPendienteLocal = pendiente,
                            faltantePendiente = faltante
                        ).recalcularLineaCompleta()
                    )
                    actualizado
                }
                intentarNavegarSustitucion()
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
                        val prep = resultado.valor
                        val linea = prep.grupos
                            .flatMap { it.lineas }
                            .firstOrNull { it.id == detalleId }
                        val progreso = progresoDesde(prep)
                        if (linea == null || lineaConfirmada) {
                            actual.copy(
                                cargando = false,
                                pedidoListoParaConfirmar = prep.listoParaSalida(),
                                lineaCompleta = actual.recalcularLineaCompleta() ||
                                    (linea != null && lineaCompletaDesde(linea)),
                                lineasEscaneadas = progreso.first,
                                lineasPendientes = progreso.second
                            )
                        } else {
                            val base = actual.copy(
                                cargando = false,
                                nombreProducto = linea.nombre,
                                cantidadSolicitada = linea.cantidad,
                                cantidadArmada = linea.cantidadArmada,
                                cantidadPendienteLocal = linea.cantidadPendienteLocal,
                                estadoLinea = linea.estadoPicking,
                                faltantePendiente = linea.faltantePendiente,
                                imagenUrl = linea.imagenUrl,
                                operaciones = linea.operacionesLocales,
                                pedidoListoParaConfirmar = prep.listoParaSalida(),
                                lineasEscaneadas = progreso.first,
                                lineasPendientes = progreso.second
                            )
                            base.copy(lineaCompleta = base.recalcularLineaCompleta())
                        }
                    }
                    is ResultadoOperacion.Error -> actual.copy(
                        cargando = false,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
            intentarNavegarSustitucion()
        }
    }

    private fun refrescarEstadoPedido() {
        viewModelScope.launch {
            val resultado = try {
                repositorio.obtenerPreparacion(pedidoId)
            } catch (_: Exception) {
                return@launch
            }
            if (resultado !is ResultadoOperacion.Exito) return@launch
            val prep = resultado.valor
            val linea = prep.grupos.flatMap { it.lineas }.firstOrNull { it.id == detalleId }
            val progreso = progresoDesde(prep)
            _estado.update { actual ->
                val conLinea = if (linea != null) actual.conLinea(linea) else actual
                conLinea.copy(
                    pedidoListoParaConfirmar = prep.listoParaSalida(),
                    lineaCompleta = conLinea.recalcularLineaCompleta(),
                    lineasEscaneadas = progreso.first,
                    lineasPendientes = progreso.second
                )
            }
            intentarNavegarSustitucion()
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
                    val actualizado = it.copy(
                        enviando = false,
                        resultado = TipoResultadoEscaneo.PENDIENTE_LOCAL,
                        mensaje = resultado.mensaje,
                        cantidadArmada = resultado.cantidadArmada,
                        cantidadPendienteLocal = resultado.cantidadPendienteLocal,
                        estadoLinea = resultado.estadoLinea,
                        puedeReintentar = false,
                        vibrar = false
                    )
                    actualizado.copy(lineaCompleta = actualizado.recalcularLineaCompleta())
                }
                autoContinuarSiAplica()
                refrescarEstadoPedido()
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
            val base = actual.conLinea(linea).copy(
                enviando = false,
                resultado = resultado.tipo,
                mensaje = resultado.mensaje,
                vibrar = resultado.vibrar,
                idVibracion = if (resultado.vibrar) actual.idVibracion + 1 else actual.idVibracion,
                puedeReintentar = false,
                cantidadPendienteLocal = resultado.cantidadPendienteLocal,
                sesionVencida = resultado.tipo == TipoResultadoEscaneo.SESION_VENCIDA
            )
            base.copy(lineaCompleta = base.recalcularLineaCompleta())
        }
        if (resultado.tipo == TipoResultadoEscaneo.CORRECTO ||
            resultado.tipo == TipoResultadoEscaneo.PENDIENTE_LOCAL
        ) {
            autoContinuarSiAplica()
        }
        refrescarEstadoPedido()
        intentarNavegarSustitucion()
    }

    private fun aplicarFallo(resultado: ResultadoEscaneo.Fallo) {
        if (resultado.conservarOperacion) gestor.falloRecuperable() else gestor.resultadoListo()
        esperandoNavegarSustitucion = false
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

    private fun autoContinuarSiAplica() {
        val actual = _estado.value
        if (actual.lineaCompleta || actual.pedidoListoParaConfirmar) return
        val tipo = actual.resultado
        if (tipo != TipoResultadoEscaneo.CORRECTO &&
            tipo != TipoResultadoEscaneo.PENDIENTE_LOCAL
        ) {
            return
        }
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

    private fun intentarNavegarSustitucion() {
        if (!esperandoNavegarSustitucion) return
        val actual = _estado.value
        if (actual.faltantePendiente || actual.enviando) return
        if (!actual.estadoLinea.equals("FALTANTE", ignoreCase = true)) return
        esperandoNavegarSustitucion = false
        _estado.update { it.copy(navegarASustitucion = true) }
    }
}

private fun lineaCompletaDesde(linea: LineaPreparacion): Boolean {
    val estado = linea.estadoPicking.trim().uppercase()
    if (estado == "FALTANTE" || estado == "ELIMINADO") return true
    return linea.cantidadArmada + linea.cantidadPendienteLocal >= linea.cantidad
}

private fun progresoDesde(
    prep: PreparacionPedido
): Pair<List<ResumenProgresoLinea>, List<ResumenProgresoLinea>> {
    val escaneadas = mutableListOf<ResumenProgresoLinea>()
    val pendientes = mutableListOf<ResumenProgresoLinea>()
    val vistos = mutableSetOf<String>()
    prep.grupos.flatMap { it.lineas }.forEach { linea ->
        if (!vistos.add(linea.id)) return@forEach
        val resumen = ResumenProgresoLinea(
            id = linea.id,
            nombre = linea.nombreVigente(),
            cantidadSolicitada = linea.cantidad,
            cantidadArmada = linea.cantidadArmada + linea.cantidadPendienteLocal,
            estadoLinea = linea.estadoPicking
        )
        val estado = linea.estadoPicking.trim().uppercase()
        val completa = estado == "ARMADO" ||
            estado == "SUSTITUIDO" ||
            estado == "ELIMINADO" ||
            (estado != "FALTANTE" &&
                linea.cantidad > 0 &&
                linea.cantidadArmada + linea.cantidadPendienteLocal >= linea.cantidad)
        if (completa) escaneadas += resumen else pendientes += resumen
    }
    return escaneadas to pendientes
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
