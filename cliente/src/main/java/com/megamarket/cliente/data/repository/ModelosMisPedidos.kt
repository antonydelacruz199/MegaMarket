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
            // Solo ONLINE explícito pendiente de cobro. Sin modalidad no se asume.
            PENDIENTES_PAGO -> e == "CONFIRMADO" &&
                modalidadPago.equals("ONLINE", ignoreCase = true)
            EN_PREPARACION -> e == "PAGADO" || e == "EN_PICKING"
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

    /** ONLINE con pago aún no CONFIRMADO en el servidor: el cliente puede pagar con el QR simulado. */
    val puedePagarConQr: Boolean
        get() = pago?.modalidad.equals(MODALIDAD_ONLINE, ignoreCase = true) &&
            !pago?.estado.equals(ESTADO_PAGO_CONFIRMADO, ignoreCase = true) &&
            !estado.trim().uppercase().let { it == "CANCELADO" || it == "ENTREGADO" }

    val franjaVisible: String
        get() = franjaEtiqueta
            ?: if (!franjaEntregaId.isNullOrBlank()) "Horario por confirmar" else "Sin franja"

    fun etapasProgreso(): List<EtapaProgresoPedido> {
        val contraentrega = pago?.modalidad.equals("CONTRAENTREGA", ignoreCase = true)
        val secuencia = if (contraentrega) {
            listOf("CONFIRMADO", "EN_PICKING", "LISTO", "EN_CAMINO", "ENTREGADO")
        } else {
            listOf("CONFIRMADO", "PAGADO", "EN_PICKING", "LISTO", "EN_CAMINO", "ENTREGADO")
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
