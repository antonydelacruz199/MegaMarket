package com.megamarket.app.model.estado

import com.megamarket.modelo.Categoria

data class ProductoFormUiState(
    val id: Long = 0,
    val nombre: String = "",
    val marca: String = "",
    val descripcion: String = "",
    val categoriaId: Long = 0,
    val categorias: List<Categoria> = emptyList(),
    val precio: String = "",
    val precioOferta: String = "",
    val stock: String = "",
    val esOferta: Boolean = false,
    val activo: Boolean = true,

    val imagenActualKey: String = "",
    val imagenPendienteKey: String? = null,
    val imagenQuitada: Boolean = false,

    /** Metadatos remotos conservados al editar; no se inventan UUID. */
    val remoteId: String? = null,
    val remoteVersion: Long? = null,
    val remoteUpdatedAt: String? = null,
    val remoteDeletedAt: String? = null,

    val cargando: Boolean = false,
    val procesandoImagen: Boolean = false,
    val guardando: Boolean = false,

    val error: String? = null,
    val guardadoCorrectamente: Boolean = false,
    val noDisponible: Boolean = false
) {
    val esEdicion: Boolean
        get() = id != 0L

    val imagenVisibleKey: String
        get() = imagenPendienteKey?.takeIf { it.isNotBlank() }
            ?: if (imagenQuitada) "" else imagenActualKey

    val ocupado: Boolean
        get() = cargando || procesandoImagen || guardando

    val nombreCategoriaSeleccionada: String
        get() = categorias.firstOrNull { it.id == categoriaId }?.nombre.orEmpty()
}
