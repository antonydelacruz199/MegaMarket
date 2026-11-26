package com.megamarket.app.data.remote

/**
 * Contrato Fase 7: abastecimiento (proveedores y órdenes de compra).
 * Todos los campos de respuesta son anulables: el servidor es la fuente de verdad y el
 * repositorio descarta lo que llegue incompleto.
 */

// ---------- Proveedores ----------

data class ProveedorDto(
    val id: String? = null,
    val ruc: String? = null,
    val razonSocial: String? = null,
    val telefono: String? = null,
    val correo: String? = null,
    val cuentaBancaria: String? = null,
    val activo: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ProveedorResponse(
    val proveedor: ProveedorDto? = null
)

data class ProveedoresResponse(
    val proveedores: List<ProveedorDto>? = null,
    val pagina: Int? = null,
    val limite: Int? = null,
    val total: Int? = null,
    val hayMas: Boolean? = null
)

data class CrearProveedorRequest(
    val ruc: String,
    val razonSocial: String,
    val telefono: String?,
    val correo: String?,
    val cuentaBancaria: String?,
    val activo: Boolean
)

/** PATCH: se envían todos los campos; una cadena vacía limpia el valor en el servidor. */
data class ActualizarProveedorRequest(
    val ruc: String,
    val razonSocial: String,
    val telefono: String,
    val correo: String,
    val cuentaBancaria: String,
    val activo: Boolean
)

// ---------- Órdenes de compra ----------

data class DetalleOrdenDto(
    val id: String? = null,
    val ordenCompraId: String? = null,
    val productoId: String? = null,
    val nombreProducto: String? = null,
    val cantidad: Int? = null,
    val precioUnitarioCentimos: Long? = null,
    val subtotalCentimos: Long? = null,
    val cantidadRecibida: Int? = null,
    val cantidadPendiente: Int? = null
)

data class TransferenciaDto(
    val id: String? = null,
    val uuidOperacion: String? = null,
    val ordenCompraId: String? = null,
    val montoCentimos: Long? = null,
    val cuentaDestino: String? = null,
    /** PENDIENTE | CONFIRMADA | RECHAZADA | DESCONOCIDA */
    val estado: String? = null,
    val referenciaBanco: String? = null,
    val numeroOperacionBanco: String? = null,
    val mensaje: String? = null,
    val simulacion: Boolean? = null,
    val adaptador: String? = null,
    val fechaSolicitud: String? = null,
    val fechaRespuesta: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class OrdenCompraDto(
    val id: String? = null,
    val proveedorId: String? = null,
    val administradorId: String? = null,
    val numeroOrden: String? = null,
    /** SUGERIDA | APROBADA | PAGADA | RECIBIDA | CANCELADA */
    val estado: String? = null,
    val totalCentimos: Long? = null,
    val numeroOperacionBanco: String? = null,
    val fechaAprobacion: String? = null,
    val fechaPago: String? = null,
    val fechaRecepcion: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val proveedor: ProveedorDto? = null,
    val detalles: List<DetalleOrdenDto>? = null,
    val transferencia: TransferenciaDto? = null
)

data class OrdenesCompraResponse(
    val ordenes: List<OrdenCompraDto>? = null,
    val pagina: Int? = null,
    val limite: Int? = null,
    val total: Int? = null,
    val hayMas: Boolean? = null
)

data class MovimientoRecepcionDto(
    val id: String? = null,
    val uuidOperacion: String? = null,
    val productoId: String? = null,
    val nombreProducto: String? = null,
    val tipo: String? = null,
    val cantidad: Int? = null,
    val ordenCompraDetalleId: String? = null,
    val stockAnterior: Int? = null,
    val stockFinal: Int? = null,
    val createdAt: String? = null
)

/**
 * Respuesta común de detalle / aprobar / cancelar / pago / recepción.
 * `error`, `transferencia`, `simulacion` y `aviso` aparecen sobre todo en el pago (también en 409).
 */
data class OrdenCompraResponse(
    val orden: OrdenCompraDto? = null,
    val transferencia: TransferenciaDto? = null,
    val movimientos: List<MovimientoRecepcionDto>? = null,
    val simulacion: Boolean? = null,
    val aviso: String? = null,
    val idempotente: Boolean? = null,
    val recepcionCompleta: Boolean? = null,
    val error: String? = null
)

data class RecepcionesResponse(
    val movimientos: List<MovimientoRecepcionDto>? = null
)

data class TransferenciaResponse(
    val transferencia: TransferenciaDto? = null,
    val simulacion: Boolean? = null,
    val aviso: String? = null
)

/** Cuerpo de errores 409 del pago/orden con datos extra de la transferencia abierta. */
data class ErrorPagoDto(
    val error: String? = null,
    val transferenciaId: String? = null,
    val uuidOperacionExistente: String? = null,
    val estadoTransferencia: String? = null,
    val simulacion: Boolean? = null
)

data class ItemOrdenRequest(
    val productoId: String,
    val cantidad: Int,
    val precioUnitarioCentimos: Long
)

data class CrearOrdenCompraRequest(
    val proveedorId: String,
    val items: List<ItemOrdenRequest>
)

data class AprobarOrdenRequest(
    val uuidOperacion: String? = null
)

data class PagarOrdenRequest(
    val uuidOperacion: String,
    val montoCentimos: Long? = null,
    val forzarResultado: String? = null
)

data class ItemRecepcionRequest(
    val detalleId: String,
    val cantidad: Int
)

data class RecepcionOrdenRequest(
    val uuidOperacion: String,
    val items: List<ItemRecepcionRequest>
)
