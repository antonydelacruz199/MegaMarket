package com.megamarket.modelo

/**
 * Categoría de catálogo. [id] es la PK local Room (Long).
 * [remoteId] queda reservado para un identificador externo futuro.
 */
data class Categoria(
    val id: Long,
    val nombre: String,
    val remoteId: String? = null,
    val remoteVersion: Long? = null,
    val remoteUpdatedAt: String? = null,
    val remoteDeletedAt: String? = null
) {
    val eliminadaRemotamente: Boolean
        get() = !remoteDeletedAt.isNullOrBlank()
}

/** Semillas locales. remoteId queda null. */
object CategoriasBootstrap {
    val NOMBRES: List<String> = listOf(
        "Abarrotes",
        "Bebidas",
        "Lácteos",
        "Snacks",
        "Limpieza",
        "Cuidado personal"
    )
}
