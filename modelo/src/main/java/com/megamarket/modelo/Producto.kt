package com.megamarket.modelo

/**
 * Producto de catálogo. [id] es la PK local Room (Long).
 * [remoteId] es el UUID de Neon; null hasta sincronizar con la API REST.
 *
 * Oferta (RF09): [descuentoPorcentaje] es la fuente principal del precio vigente.
 * [precioOfertaCentimos] se conserva por compatibilidad con Neon y datos legacy.
 */
data class Producto(
    val id: Long,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long? = null,
    val descuentoPorcentaje: Int = 0,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean = true,
    val remoteId: String? = null,
    val remoteVersion: Long? = null,
    val remoteUpdatedAt: String? = null,
    val remoteDeletedAt: String? = null
) {
    val ofertaValida: Boolean
        get() {
            val descuento = descuentoEfectivo
            return PrecioDescuento.esOfertaActiva(esOferta, descuento) &&
                PrecioDescuento.precioConDescuentoCentimos(precioCentimos, descuento) < precioCentimos &&
                PrecioDescuento.precioConDescuentoCentimos(precioCentimos, descuento) > 0L
        }

    /** % efectivo: campo explícito, o derivado de precioOferta legacy. */
    val descuentoEfectivo: Int
        get() = when {
            descuentoPorcentaje in 1..100 -> descuentoPorcentaje
            esOferta -> PrecioDescuento.descuentoDesdePrecioOferta(precioCentimos, precioOfertaCentimos)
            else -> 0
        }

    val precioVigenteCentimos: Long
        get() {
            if (!ofertaValida) return precioCentimos
            // Fuente principal: descuentoPorcentaje explícito.
            if (descuentoPorcentaje in 1..99) {
                return PrecioDescuento.precioConDescuentoCentimos(precioCentimos, descuentoPorcentaje)
            }
            // Legacy: conservar precioOfertaCentimos exacto si el % solo se derivó.
            val oferta = precioOfertaCentimos
            if (oferta != null && oferta > 0L && oferta < precioCentimos) return oferta
            return PrecioDescuento.precioConDescuentoCentimos(precioCentimos, descuentoEfectivo)
        }

    val agotado: Boolean
        get() = stock <= 0

    val stockBajo: Boolean
        get() = stock in 1..STOCK_BAJO_MAXIMO

    val eliminadoRemotamente: Boolean
        get() = !remoteDeletedAt.isNullOrBlank()

    companion object {
        const val STOCK_BAJO_MAXIMO = 5
    }
}
