package com.megamarket.cliente.data.local

/**
 * Representación de un producto leído del ContentProvider del administrador.
 * [providerId] es la PK admin — nunca debe usarse como PK Room del cliente.
 */
data class ProductoProviderSnapshot(
    val providerId: Long,
    val remoteId: String? = null,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    /** ID de categoría en Room admin (bootstrap alineado por nombre mientras dure la transición). */
    val categoriaProviderId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long? = null,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean,
    val remoteVersion: Long? = null,
    val remoteUpdatedAt: String? = null,
    val remoteDeletedAt: String? = null
)
