package com.megamarket.picker.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.picker.data.repository.CODIGO_REQUIERE_PAGO
import com.megamarket.picker.data.repository.DetalleReparto
import com.megamarket.picker.data.repository.ESTADO_PAGO_CONFIRMADO
import com.megamarket.picker.data.repository.MensajesEntrega
import com.megamarket.picker.data.repository.MensajesSesion
import com.megamarket.picker.data.repository.PedidosRepartoRepository
import com.megamarket.picker.data.repository.ResultadoOperacion
import com.megamarket.picker.data.repository.TipoErrorPedido
import com.megamarket.picker.data.repository.gpsValido
import com.megamarket.picker.data.repository.pagoEstaPendiente
import com.megamarket.picker.state.DetalleEntregaUiState
import com.megamarket.picker.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class DetalleEntregaViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: PedidosRepartoRepository,
    private val nuevoUuid: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val pedidoId: String = savedStateHandle.get<String>(Ruta.ARG_PEDIDO).orEmpty()
    private val _estado = MutableStateFlow(DetalleEntregaUiState())
    val estado: StateFlow<DetalleEntregaUiState> = _estado.asStateFlow()
    private var carga: Job? = null
    /** Se reutiliza en reintentos de la misma confirmación para que el servidor sea idempotente. */
    private var uuidPago: String? = null
    private var pagoEnCurso = false
    private var foto: ByteArray? = null
    private var fotoNueva: Boolean = false

    init {
        cargar()
    }

    fun fotografia(): ByteArray? = foto

    fun cargar() {
        if (pedidoId.isBlank()) {
            _estado.value = DetalleEntregaUiState(
                cargando = false,
                error = "Pedido no encontrado",
                codigoError = "PEDIDO_NO_ENCONTRADO"
            )
            return
        }
        if (carga?.isActive == true) return
        carga = viewModelScope.launch {
            val hayDetalle = _estado.value.detalle != null
            _estado.update { it.copy(cargando = !hayDetalle, refrescando = hayDetalle, error = null) }
            aplicar(cargarDetalle())
        }
    }

    fun establecerFoto(bytes: ByteArray) {
        if (bytes.isEmpty() || _estado.value.entregado) return
        foto = bytes
        fotoNueva = true
        _estado.update {
            it.copy(
                fotoLista = true,
                revisionFoto = it.revisionFoto + 1,
                error = null,
                codigoError = null
            )
        }
    }

    fun fijarUbicacion(latitud: Double, longitud: Double) {
        if (!gpsValido(latitud, longitud)) {
            _estado.update {
                it.copy(error = MensajesEntrega.GPS, codigoError = "GPS_INVALIDO", latitud = null, longitud = null)
            }
            return
        }
        _estado.update {
            it.copy(latitud = latitud, longitud = longitud, error = null, codigoError = null)
        }
    }

    /** Genera el QR informativo (opcional). No confirma el cobro ni desbloquea la entrega. */
    fun generarQrPago() {
        if (pagoEnCurso || !_estado.value.requierePagoQr) return
        pagoEnCurso = true
        viewModelScope.launch {
            _estado.update { it.copy(generandoQr = true, errorPago = null) }
            val resultado = try {
                repositorio.generarQrPago(pedidoId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> {
                    if (resultado.valor.confirmado) {
                        _estado.update { it.copy(generandoQr = false, qrPago = null) }
                        aplicar(cargarDetalle())
                    } else {
                        _estado.update { it.copy(generandoQr = false, qrPago = resultado.valor) }
                    }
                }
                is ResultadoOperacion.Error -> _estado.update {
                    it.copy(
                        generandoQr = false,
                        errorPago = resultado.mensaje,
                        sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                            resultado.tipo == TipoErrorPedido.CUENTA
                    )
                }
            }
            pagoEnCurso = false
        }
    }

    fun ocultarQrPago() {
        _estado.update { it.copy(qrPago = null) }
    }

    /**
     * Cobro directo vía API (sin exigir QR). Solo la confirmación del servidor, seguida de una
     * recarga del detalle, levanta el bloqueo `pagoPendiente`.
     */
    fun confirmarPago() {
        val actual = _estado.value
        val pagoId = actual.qrPago?.pagoId?.takeIf { it.isNotBlank() }
            ?: actual.detalle?.pago?.id?.takeIf { it.isNotBlank() }
            ?: return
        if (pagoEnCurso || actual.confirmandoPago || !actual.requierePagoQr) return
        pagoEnCurso = true
        val uuid = uuidPago ?: nuevoUuid().also { uuidPago = it }
        viewModelScope.launch {
            _estado.update { it.copy(confirmandoPago = true, errorPago = null) }
            val resultado = try {
                repositorio.confirmarPago(pagoId, uuid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> {
                    uuidPago = null
                    _estado.update { it.copy(confirmandoPago = false, qrPago = null) }
                    recargarTrasPago()
                }
                is ResultadoOperacion.Error -> {
                    _estado.update {
                        it.copy(
                            confirmandoPago = false,
                            errorPago = resultado.mensaje,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                    // Resultado desconocido (sin red): comprobar en el servidor si el cobro sí se aplicó.
                    if (resultado.tipo == TipoErrorPedido.RED) verificarPagoRemoto()
                }
            }
            pagoEnCurso = false
        }
    }

    private suspend fun verificarPagoRemoto() {
        val lectura = try {
            repositorio.consultarPago(pedidoId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
        if (lectura is ResultadoOperacion.Exito &&
            lectura.valor.estado.equals(ESTADO_PAGO_CONFIRMADO, ignoreCase = true)
        ) {
            uuidPago = null
            _estado.update { it.copy(qrPago = null, errorPago = null) }
            recargarTrasPago()
        }
    }

    private suspend fun recargarTrasPago() {
        aplicar(cargarDetalle())
        _estado.update { it.copy(pagoConfirmadoReciente = !it.pagoPendiente && it.detalle != null) }
    }

    fun registrar() {
        val actual = _estado.value
        if (actual.registrando || actual.entregado) return
        val detalle = actual.detalle ?: return
        if (pagoEstaPendiente(detalle.pago?.estado) || actual.pagoPendiente) {
            _estado.update {
                it.copy(
                    pagoPendiente = true,
                    entregado = false,
                    error = MensajesEntrega.PAGO,
                    codigoError = CODIGO_REQUIERE_PAGO
                )
            }
            return
        }
        if (!detalle.puedeFinalizar) {
            _estado.update {
                it.copy(
                    entregado = false,
                    error = MensajesEntrega.SOLO_EN_CAMINO,
                    codigoError = "PEDIDO_NO_EN_CAMINO"
                )
            }
            return
        }
        val latitud = actual.latitud
        val longitud = actual.longitud
        if (latitud == null || longitud == null || !gpsValido(latitud, longitud)) {
            _estado.update { it.copy(error = MensajesEntrega.GPS, codigoError = "GPS_INVALIDO") }
            return
        }
        if (!actual.fotoLista && !repositorio.fotoPublicada(pedidoId)) {
            _estado.update { it.copy(error = MensajesEntrega.FOTO, codigoError = "FOTO_REQUERIDA") }
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(registrando = true, error = null, codigoError = null, conflicto = false) }
            val resultado = try {
                repositorio.registrar(
                    pedidoId = pedidoId,
                    foto = foto,
                    latitud = latitud,
                    longitud = longitud,
                    estadoPedido = detalle.pedido.estado,
                    estadoPago = detalle.pago?.estado,
                    nuevaFoto = fotoNueva
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoOperacion.Exito -> {
                    if (!resultado.valor.estadoPedido.equals("ENTREGADO", ignoreCase = true)) {
                        _estado.update {
                            it.copy(
                                registrando = false,
                                entregado = false,
                                error = MensajesEntrega.NO_CONFIRMADO,
                                codigoError = "ESTADO_NO_CONFIRMADO"
                            )
                        }
                        return@launch
                    }
                    fotoNueva = false
                    val previo = _estado.value.detalle
                    _estado.update {
                        it.copy(
                            registrando = false,
                            entregado = true,
                            confirmacionReciente = true,
                            idempotente = resultado.valor.idempotente,
                            pagoPendiente = false,
                            error = null,
                            codigoError = null,
                            conflicto = false,
                            detalle = previo?.copy(
                                pedido = previo.pedido.copy(estado = resultado.valor.estadoPedido),
                                puedeFinalizar = false
                            )
                        )
                    }
                }
                is ResultadoOperacion.Error -> {
                    if (resultado.fotoPublicada) fotoNueva = false
                    val requierePago = resultado.codigo == CODIGO_REQUIERE_PAGO
                    val asignacionAjena = resultado.codigo == "PEDIDO_NO_ASIGNADO" ||
                        (resultado.tipo == TipoErrorPedido.PROHIBIDO && !requierePago)
                    _estado.update {
                        it.copy(
                            registrando = false,
                            entregado = false,
                            pagoPendiente = it.pagoPendiente || requierePago,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            conflicto = asignacionAjena,
                            sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                                resultado.tipo == TipoErrorPedido.CUENTA
                        )
                    }
                    if (resultado.codigo == "ENTREGA_CERRADA" || resultado.codigo == "TRANSICION_INVALIDA") {
                        cargar()
                    }
                }
            }
        }
    }

    private suspend fun cargarDetalle(): ResultadoOperacion<DetalleReparto> =
        try {
            repositorio.obtenerDetalle(pedidoId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ResultadoOperacion.Error(TipoErrorPedido.RED, MensajesSesion.SIN_CONEXION)
        }

    private fun aplicar(resultado: ResultadoOperacion<DetalleReparto>) {
        when (resultado) {
            is ResultadoOperacion.Exito -> {
                val detalle = resultado.valor
                val entregado = detalle.pedido.estado.equals("ENTREGADO", ignoreCase = true) ||
                    detalle.entrega != null
                val fotoRemota = repositorio.fotoPublicada(pedidoId)
                val pagoPendiente = pagoEstaPendiente(detalle.pago?.estado)
                _estado.update {
                    it.copy(
                        cargando = false,
                        refrescando = false,
                        detalle = detalle,
                        entregado = entregado,
                        pagoPendiente = pagoPendiente,
                        qrPago = if (pagoPendiente) it.qrPago else null,
                        errorPago = if (pagoPendiente) it.errorPago else null,
                        fotoLista = it.fotoLista || fotoRemota,
                        error = null,
                        codigoError = null,
                        sesionVencida = false
                    )
                }
            }
            is ResultadoOperacion.Error -> _estado.update {
                it.copy(
                    cargando = false,
                    refrescando = false,
                    error = resultado.mensaje,
                    codigoError = resultado.codigo,
                    conflicto = resultado.codigo == "PEDIDO_NO_ASIGNADO" ||
                        resultado.tipo == TipoErrorPedido.PROHIBIDO,
                    sesionVencida = resultado.tipo == TipoErrorPedido.SESION ||
                        resultado.tipo == TipoErrorPedido.CUENTA
                )
            }
        }
    }
}
