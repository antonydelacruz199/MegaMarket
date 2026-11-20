package com.megamarket.cliente.data.repository

import com.megamarket.cliente.model.FranjaEntrega
import com.megamarket.cliente.model.ModalidadPagoCheckout

/**
 * Filtros de Mis Pedidos. Se mapean a `pedidos.estado` reales (sin estados Neon nuevos).
 */
enum class FiltroMisPedidos(val etiqueta: String) {
    TODOS("Todos"),
    PENDIENTES_PAGO("Pendientes de pago"),
    EN_PREPARACION("En preparación"),
    LISTOS("Listos"),
    EN_CAMINO("En camino"),
    ENTREGADOS("Entregados"),
    CANCELADOS("Cancelados");

    fun coincide(estado: String, modalidadPago: String?): Boolean {
        val e = estado.trim().uppercase()
        return when (this) {
            TODOS -> true
            // ONLINE tras CONFIRMADO; CONTRAENTREGA en entrega.
            PENDIENTES_PAGO ->
                (e == "CONFIRMADO" && modalidadPago.equals("ONLINE", ignoreCase = true)) ||
                    ((e == "LISTO" || e == "EN_CAMINO") &&
                        modalidadPago.equals("CONTRAENTREGA", ignoreCase = true))
            EN_PREPARACION -> e == "RECIBIDO" || e == "EN_PICKING" || e == "PAGADO"
            LISTOS -> e == "LISTO"
            EN_CAMINO -> e == "EN_CAMINO"
            ENTREGADOS -> e == "ENTREGADO"
            CANCELADOS -> e == "CANCELADO"
        }
    }
}

data class PedidoResumenCliente(
    val id: String,
    val fecha: String?,
    val totalCentimos: Long,
    val estado: String,
    val franjaEntregaId: String?,
    val franjaEtiqueta: String?,
    val modalidadPago: String?,
    val modalidadEtiqueta: String?,
    val tienePropuestaPendiente: Boolean,
    val version: Long?
) {
    val modalidadVisible: String
        get() = modalidadEtiqueta
            ?: ModalidadPagoCheckout.desdeApi(modalidadPago)?.titulo
            ?: "Sin modalidad"
}

data class LineaPedidoCliente(
    val id: String,
    val productoId: String?,
    val nombre: String,
    val cantidad: Int,
    val precioUnitarioCentimos: Long,
    val subtotalCentimos: Long,
    val estadoPicking: String?,
    val cantidadArmada: Int?
)

data class DireccionPedidoCliente(
    val departamento: String?,
    val provincia: String?,
    val distrito: String?,
    val direccion: String?,
    val telefono: String?
)

data class PagoPedidoCliente(
    val modalidad: String?,
    val estado: String?,
    val montoCentimos: Long?,
    val medioPago: String?,
    val numeroOperacionBanco: String?,
    val fechaConfirmacion: String?,
    val id: String? = null
)

data class PedidoDetalleCliente(
    val id: String,
    val fecha: String?,
    val totalCentimos: Long,
    val estado: String,
    val franjaEntregaId: String?,
    val franjaEtiqueta: String?,
    val version: Long?,
    val lineas: List<LineaPedidoCliente>,
    val direccion: DireccionPedidoCliente?,
    val pago: PagoPedidoCliente?,
    val sustituciones: List<PropuestaSustitucionCliente>
) {
    val modalidadVisible: String
        get() = ModalidadPagoCheckout.desdeApi(pago?.modalidad)?.titulo
            ?: pago?.modalidad
            ?: "Sin modalidad"

    /**
     * ONLINE tras CONFIRMADO, o CONTRAENTREGA en entrega (LISTO/EN_CAMINO) con billetera.
     */
    val puedePagarConQr: Boolean
        get() {
            if (pago?.estado.equals(ESTADO_PAGO_CONFIRMADO, ignoreCase = true) == true) {
                return false
            }
            val e = estado.trim().uppercase()
            val modalidad = pago?.modalidad
            if (modalidad.equals(MODALIDAD_ONLINE, ignoreCase = true)) {
                return e == "CONFIRMADO"
            }
            if (modalidad.equals(MODALIDAD_CONTRAENTREGA, ignoreCase = true)) {
                return e == "LISTO" || e == "EN_CAMINO"
            }
            return false
        }

    /**
     * Cancelación del cliente: RECIBIDO, EN_PICKING, CONFIRMADO o PAGADO
     * (antes de despacho). Con cobro confirmado el servidor aplica devolución.
     */
    val puedeCancelar: Boolean
        get() {
            val e = estado.trim().uppercase()
            return e == "RECIBIDO" || e == "EN_PICKING" || e == "CONFIRMADO" || e == "PAGADO"
        }

    /**
     * Escanea el QR del repartidor para pagar contraentrega con billetera.
     */
    val puedeEscanearQrEntrega: Boolean
        get() = pago?.modalidad.equals(MODALIDAD_CONTRAENTREGA, ignoreCase = true) &&
            !pago?.estado.equals(ESTADO_PAGO_CONFIRMADO, ignoreCase = true) &&
            estado.trim().uppercase().let { it == "LISTO" || it == "EN_CAMINO" }

    val franjaVisible: String
        get() = franjaEtiqueta
            ?: if (!franjaEntregaId.isNullOrBlank()) "Horario por confirmar" else "Sin franja"

    fun etapasProgreso(): List<EtapaProgresoPedido> {
        val contraentrega = pago?.modalidad.equals("CONTRAENTREGA", ignoreCase = true)
        val secuencia = if (contraentrega) {
            listOf("RECIBIDO", "EN_PICKING", "CONFIRMADO", "LISTO", "EN_CAMINO", "ENTREGADO")
        } else {
            listOf("RECIBIDO", "EN_PICKING", "CONFIRMADO", "PAGADO", "LISTO", "EN_CAMINO", "ENTREGADO")
        }
        val actual = estado.trim().uppercase()
        if (actual == "CANCELADO") {
            return secuencia.map { EtapaProgresoPedido(it, completada = false, actual = false) } +
                EtapaProgresoPedido("CANCELADO", completada = true, actual = true)
        }
        val idxActual = secuencia.indexOf(actual)
        return secuencia.mapIndexed { i, nombre ->
            EtapaProgresoPedido(
                nombre = nombre,
                completada = idxActual >= 0 && i < idxActual,
                actual = i == idxActual
            )
        }
    }
}

data class EtapaProgresoPedido(
    val nombre: String,
    val completada: Boolean,
    val actual: Boolean
)

sealed class ResultadoMisPedidos<out T> {
    data class Exito<T>(val valor: T) : ResultadoMisPedidos<T>()
    data class Error(
        val tipo: TipoErrorSustitucion,
        val mensaje: String,
        val codigo: String? = null
    ) : ResultadoMisPedidos<Nothing>()
}
