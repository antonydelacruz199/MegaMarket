package com.megamarket.app.data.repository

import com.megamarket.app.data.remote.DetalleOrdenDto
import com.megamarket.app.data.remote.MovimientoRecepcionDto
import com.megamarket.app.data.remote.OrdenCompraDto
import com.megamarket.app.data.remote.ProveedorDto
import com.megamarket.app.data.remote.TransferenciaDto

/** Resultado genérico de las llamadas de abastecimiento (proveedores y compras). */
sealed class ResultadoAbastecimiento<out T> {
    data class Ok<T>(val valor: T) : ResultadoAbastecimiento<T>()
    data class Error(
        val mensaje: String,
        val codigoHttp: Int? = null,
        /** Si el servidor informa una transferencia abierta (PENDIENTE/DESCONOCIDA) con otro uuid. */
        val uuidOperacionExistente: String? = null
    ) : ResultadoAbastecimiento<Nothing>()
}

data class Pagina<T>(
    val items: List<T>,
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val total: Int = 0
)

data class Proveedor(
    val id: String,
    val ruc: String,
    val razonSocial: String,
    val telefono: String?,
    val correo: String?,
    val cuentaBancaria: String?,
    val activo: Boolean
)

/** Datos editables del proveedor tal como los captura el formulario. */
data class DatosProveedor(
    val ruc: String,
    val razonSocial: String,
    val telefono: String,
    val correo: String,
    val cuentaBancaria: String,
    val activo: Boolean
)

enum class EstadoOrden(val etiqueta: String) {
    SUGERIDA("Sugerida"),
    APROBADA("Aprobada"),
    PAGADA("Pagada"),
    RECIBIDA("Recibida"),
    CANCELADA("Cancelada"),
    DESCONOCIDO("Desconocido");

    companion object {
        fun desdeApi(valor: String?): EstadoOrden =
            entries.firstOrNull { it != DESCONOCIDO && it.name.equals(valor?.trim(), ignoreCase = true) }
                ?: DESCONOCIDO
    }
}

enum class EstadoTransferencia(val etiqueta: String) {
    PENDIENTE("Pendiente"),
    CONFIRMADA("Confirmada"),
    RECHAZADA("Rechazada"),
    DESCONOCIDA("Resultado desconocido");

    /** Con estos estados NO se debe emitir otra transferencia con un uuid nuevo. */
    val esAbierta: Boolean get() = this == PENDIENTE || this == DESCONOCIDA

    companion object {
        /** Cualquier valor no reconocido se trata como DESCONOCIDA: nunca se asume éxito. */
        fun desdeApi(valor: String?): EstadoTransferencia =
            entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) } ?: DESCONOCIDA
    }
}

data class DetalleOrden(
    val id: String,
    val productoId: String,
    val nombreProducto: String,
    val cantidad: Int,
    val precioUnitarioCentimos: Long,
    val subtotalCentimos: Long,
    val cantidadRecibida: Int,
    val cantidadPendiente: Int
)

data class TransferenciaProveedor(
    val id: String?,
    val uuidOperacion: String,
    val montoCentimos: Long?,
    val cuentaDestino: String?,
    val estado: EstadoTransferencia,
    val referenciaBanco: String?,
    val numeroOperacionBanco: String?,
    val mensaje: String?,
    val simulacion: Boolean,
    val fechaRespuesta: String?
)

data class OrdenCompra(
    val id: String,
    val proveedorId: String,
    val numeroOrden: String,
    val estado: EstadoOrden,
    val totalCentimos: Long,
    val numeroOperacionBanco: String?,
    val fechaAprobacion: String?,
    val fechaPago: String?,
    val fechaRecepcion: String?,
    val createdAt: String?,
    val proveedor: Proveedor?,
    val detalles: List<DetalleOrden>,
    val transferencia: TransferenciaProveedor?
) {
    val puedeAprobar: Boolean get() = estado == EstadoOrden.SUGERIDA
    val puedeCancelar: Boolean get() = estado == EstadoOrden.SUGERIDA || estado == EstadoOrden.APROBADA
    val puedePagar: Boolean get() = estado == EstadoOrden.APROBADA
    val puedeRecibir: Boolean get() = estado == EstadoOrden.PAGADA && detalles.any { it.cantidadPendiente > 0 }
}

data class MovimientoRecepcion(
    val id: String,
    val productoId: String?,
    val nombreProducto: String?,
    val cantidad: Int,
    val stockAnterior: Int?,
    val stockFinal: Int?,
    val createdAt: String?
)

/** Resultado de un intento de pago. `confirmada` solo es verdadero con transferencia CONFIRMADA. */
data class RespuestaPago(
    val orden: OrdenCompra?,
    val transferencia: TransferenciaProveedor?,
    val simulacion: Boolean,
    val aviso: String?,
    val idempotente: Boolean,
    val mensajeServidor: String?
) {
    val confirmada: Boolean get() = transferencia?.estado == EstadoTransferencia.CONFIRMADA
}

data class RespuestaRecepcion(
    val orden: OrdenCompra,
    val recepcionCompleta: Boolean,
    val idempotente: Boolean,
    val movimientos: List<MovimientoRecepcion>
)

/** Producto del catálogo remoto elegible como línea de una orden. */
data class ProductoOpcion(
    val id: String,
    val nombre: String,
    val marca: String,
    val precioVentaCentimos: Long,
    val stock: Int
)

data class LineaOrdenNueva(
    val productoId: String,
    val cantidad: Int,
    val precioUnitarioCentimos: Long
) {
    val subtotalCentimos: Long get() = cantidad.toLong() * precioUnitarioCentimos
}

data class ItemRecepcion(val detalleId: String, val cantidad: Int)

// ---------- Mapeo DTO -> dominio (descarta datos incompletos) ----------

internal fun ProveedorDto.aDominio(): Proveedor? {
    val idLimpio = id?.takeIf { it.isNotBlank() } ?: return null
    return Proveedor(
        id = idLimpio,
        ruc = ruc.orEmpty(),
        razonSocial = razonSocial.orEmpty(),
        telefono = telefono?.takeIf { it.isNotBlank() },
        correo = correo?.takeIf { it.isNotBlank() },
        cuentaBancaria = cuentaBancaria?.takeIf { it.isNotBlank() },
        activo = activo != false
    )
}

internal fun DetalleOrdenDto.aDominio(): DetalleOrden? {
    val idLimpio = id?.takeIf { it.isNotBlank() } ?: return null
    val cantidadTotal = cantidad ?: return null
    val recibida = cantidadRecibida ?: 0
    return DetalleOrden(
        id = idLimpio,
        productoId = productoId.orEmpty(),
        nombreProducto = nombreProducto?.takeIf { it.isNotBlank() } ?: "Producto",
        cantidad = cantidadTotal,
        precioUnitarioCentimos = precioUnitarioCentimos ?: 0L,
        subtotalCentimos = subtotalCentimos ?: ((precioUnitarioCentimos ?: 0L) * cantidadTotal),
        cantidadRecibida = recibida,
        cantidadPendiente = cantidadPendiente ?: (cantidadTotal - recibida).coerceAtLeast(0)
    )
}

internal fun TransferenciaDto.aDominio(): TransferenciaProveedor? {
    val uuid = uuidOperacion?.takeIf { it.isNotBlank() } ?: return null
    return TransferenciaProveedor(
        id = id,
        uuidOperacion = uuid,
        montoCentimos = montoCentimos,
        cuentaDestino = cuentaDestino,
        estado = EstadoTransferencia.desdeApi(estado),
        referenciaBanco = referenciaBanco,
        numeroOperacionBanco = numeroOperacionBanco,
        mensaje = mensaje,
        simulacion = simulacion != false,
        fechaRespuesta = fechaRespuesta
    )
}

internal fun OrdenCompraDto.aDominio(): OrdenCompra? {
    val idLimpio = id?.takeIf { it.isNotBlank() } ?: return null
    val total = totalCentimos ?: return null
    return OrdenCompra(
        id = idLimpio,
        proveedorId = proveedorId.orEmpty(),
        numeroOrden = numeroOrden?.takeIf { it.isNotBlank() } ?: idLimpio.take(8),
        estado = EstadoOrden.desdeApi(estado),
        totalCentimos = total,
        numeroOperacionBanco = numeroOperacionBanco,
        fechaAprobacion = fechaAprobacion,
        fechaPago = fechaPago,
        fechaRecepcion = fechaRecepcion,
        createdAt = createdAt,
        proveedor = proveedor?.aDominio(),
        detalles = detalles.orEmpty().mapNotNull { it.aDominio() },
        transferencia = transferencia?.aDominio()
    )
}

internal fun MovimientoRecepcionDto.aDominio(): MovimientoRecepcion? {
    val idLimpio = id?.takeIf { it.isNotBlank() } ?: return null
    val cantidadRecibida = cantidad ?: return null
    return MovimientoRecepcion(
        id = idLimpio,
        productoId = productoId,
        nombreProducto = nombreProducto,
        cantidad = cantidadRecibida,
        stockAnterior = stockAnterior,
        stockFinal = stockFinal,
        createdAt = createdAt
    )
}
