package com.megamarket.cliente.model

import com.megamarket.modelo.Producto

/** Lo que el cliente pidió según el carrito guardado. */
data class ProductoSolicitado(
    val productoId: Long,
    val cantidad: Int
)

/** El checkout no puede continuar; el mensaje se muestra tal cual al cliente. */
class CheckoutException(
    mensaje: String,
    /** Registros del carrito cuyo producto ya no existe o fue desactivado. */
    val productosNoDisponibles: List<Long> = emptyList()
) : Exception(mensaje)

/**
 * Valida el carrito contra el catálogo leído en este momento y arma el detalle del pedido
 * con nombre y precio vigentes. Nunca ajusta cantidades: si el stock cambió, falla.
 */
fun armarDetallesPedido(
    solicitados: List<ProductoSolicitado>,
    catalogo: Map<Long, Producto>
): List<PedidoDetalle> {
    if (solicitados.isEmpty()) throw CheckoutException("Tu carrito está vacío")

    val noDisponibles = solicitados
        .filter { catalogo[it.productoId]?.activo != true }
        .map { it.productoId }
    if (noDisponibles.isNotEmpty()) {
        throw CheckoutException(
            "Algunos productos ya no están disponibles y se quitaron de tu carrito. Revisa tu compra.",
            productosNoDisponibles = noDisponibles
        )
    }

    return solicitados.map { solicitado ->
        val producto = catalogo.getValue(solicitado.productoId)
        when {
            solicitado.cantidad < 1 ->
                throw CheckoutException("La cantidad de ${producto.nombre} no es válida")
            producto.agotado ->
                throw CheckoutException("${producto.nombre} se agotó. Quítalo del carrito para continuar.")
            solicitado.cantidad > producto.stock ->
                throw CheckoutException(
                    "El stock de ${producto.nombre} cambió. Disponible: ${textoUnidades(producto.stock)}."
                )
        }
        val precio = producto.precioVigenteCentimos
        PedidoDetalle(
            productoId = producto.id,
            nombreProducto = producto.nombre,
            precioUnitarioCentimos = precio,
            cantidad = solicitado.cantidad,
            subtotalCentimos = precio * solicitado.cantidad
        )
    }
}

private fun textoUnidades(cantidad: Int): String =
    if (cantidad == 1) "1 unidad" else "$cantidad unidades"
