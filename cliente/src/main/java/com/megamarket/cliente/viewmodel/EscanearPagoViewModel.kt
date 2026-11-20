package com.megamarket.cliente.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamarket.cliente.data.repository.MODALIDAD_CONTRAENTREGA
import com.megamarket.cliente.data.repository.MensajesPagoCliente
import com.megamarket.cliente.data.repository.PagosRepository
import com.megamarket.cliente.data.repository.ResultadoPagos
import com.megamarket.cliente.data.repository.TipoErrorSustitucion
import com.megamarket.cliente.data.repository.parsearQrPago
import com.megamarket.cliente.state.EscanearPagoUiState
import com.megamarket.cliente.ui.navigation.Ruta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Escanea el QR del repartidor y confirma el cobro CONTRAENTREGA con billetera del cliente.
 */
class EscanearPagoViewModel(
    savedStateHandle: SavedStateHandle,
    private val pagos: PagosRepository,
    private val nuevoUuid: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val pedidoEsperado: String =
        savedStateHandle.get<String>(Ruta.ARG_PEDIDO_REMOTO).orEmpty()

    private val _estado = MutableStateFlow(EscanearPagoUiState(pedidoId = pedidoEsperado))
    val estado: StateFlow<EscanearPagoUiState> = _estado.asStateFlow()

    private var uuidConfirmacion: String? = null
    private var procesando = false

    fun codigoDetectado(contenido: String?) {
        val texto = contenido?.trim().orEmpty()
        if (texto.isEmpty() || procesando || _estado.value.confirmado || _estado.value.confirmando) {
            return
        }
        if (texto == _estado.value.ultimoCodigo) return
        val qr = parsearQrPago(texto)
        if (qr == null) {
            _estado.update {
                it.copy(
                    ultimoCodigo = texto,
                    error = "El código no es un QR de pago de MegaMarket"
                )
            }
            return
        }
        if (!qr.pedidoId.equals(pedidoEsperado, ignoreCase = true)) {
            _estado.update {
                it.copy(
                    ultimoCodigo = texto,
                    error = "Este QR pertenece a otro pedido"
                )
            }
            return
        }
        if (qr.modalidad != null &&
            !qr.modalidad.equals(MODALIDAD_CONTRAENTREGA, ignoreCase = true)
        ) {
            _estado.update {
                it.copy(
                    ultimoCodigo = texto,
                    error = "Este QR no corresponde a un cobro contraentrega"
                )
            }
            return
        }
        confirmar(qr.pagoId, texto)
    }

    fun reintentarEscaneo() {
        if (_estado.value.confirmando || _estado.value.confirmado) return
        procesando = false
        uuidConfirmacion = null
        _estado.update {
            it.copy(
                escaneando = true,
                ultimoCodigo = null,
                error = null,
                codigoError = null
            )
        }
    }

    private fun confirmar(pagoId: String, codigo: String) {
        if (procesando) return
        procesando = true
        val uuid = uuidConfirmacion ?: nuevoUuid().also { uuidConfirmacion = it }
        viewModelScope.launch {
            _estado.update {
                it.copy(
                    escaneando = false,
                    confirmando = true,
                    ultimoCodigo = codigo,
                    error = null,
                    codigoError = null
                )
            }
            val resultado = try {
                pagos.confirmar(pagoId, uuid, formaCobro = "BILLETERA")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ResultadoPagos.Error(TipoErrorSustitucion.RED, MensajesPagoCliente.SIN_CONEXION)
            }
            when (resultado) {
                is ResultadoPagos.Exito -> _estado.update {
                    it.copy(
                        confirmando = false,
                        confirmado = true,
                        escaneando = false,
                        pedidoEstado = resultado.valor.pedidoEstado,
                        saldoClienteCentimos = resultado.valor.saldoClienteCentimos,
                        error = null,
                        codigoError = null
                    )
                }
                is ResultadoPagos.Error -> {
                    procesando = false
                    if (resultado.codigo == "QR_EXPIRADO") {
                        uuidConfirmacion = null
                    }
                    _estado.update {
                        it.copy(
                            confirmando = false,
                            escaneando = true,
                            error = resultado.mensaje,
                            codigoError = resultado.codigo,
                            sesionVencida = resultado.tipo == TipoErrorSustitucion.SESION ||
                                resultado.tipo == TipoErrorSustitucion.CUENTA
                        )
                    }
                }
            }
        }
    }
}
