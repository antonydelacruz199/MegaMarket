package com.megamarket.modelo

/**
 * Producto de catálogo. [id] es la PK local Room (Long).
 * [remoteId] es el UUID de Neon; null hasta sincronizar con la API REST.
 * Android nunca genera UUID locales para mapear productos remotos existentes.
 */
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
    val activo: Boolean = true,
    val remoteId: String? = null,
    val remoteVersion: Long? = null,
    val remoteUpdatedAt: String? = null,
    val remoteDeletedAt: String? = null
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

    val stockBajo: Boolean
        get() = stock in 1..STOCK_BAJO_MAXIMO

    val eliminadoRemotamente: Boolean
        get() = !remoteDeletedAt.isNullOrBlank()

    companion object {
        const val STOCK_BAJO_MAXIMO = 5
    }
}
