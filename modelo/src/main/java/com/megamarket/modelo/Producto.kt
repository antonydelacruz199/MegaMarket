package com.megamarket.modelo

data class Producto(
    val id: Long,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long? = null,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean = true
) {
    val ofertaValida: Boolean
        get() {
            val oferta = precioOfertaCentimos
            return esOferta && oferta != null && oferta < precioCentimos
        }

    val precioVigenteCentimos: Long
        get() {
            val oferta = precioOfertaCentimos
            return if (esOferta && oferta != null && oferta < precioCentimos) {
                oferta
            } else {
                precioCentimos
            }
        }

    val agotado: Boolean
        get() = stock <= 0
}
