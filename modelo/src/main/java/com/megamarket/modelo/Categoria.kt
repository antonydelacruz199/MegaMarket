package com.megamarket.modelo

/**
 * Categoría de catálogo. [id] es la PK local Room (Long).
 * [remoteId] es el UUID de Neon cuando exista sincronización vía API REST.
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

/** Semillas locales (sin UUID inventados). remoteId queda null hasta GET /api/categorias. */
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
