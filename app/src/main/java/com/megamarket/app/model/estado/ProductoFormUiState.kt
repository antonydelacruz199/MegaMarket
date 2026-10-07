package com.megamarket.app.model.estado

data class ProductoFormUiState(
    val id: Long = 0,
    val nombre: String = "",
    val marca: String = "",
    val descripcion: String = "",
    val categoria: String = "",
    val precio: String = "",
    val precioOferta: String = "",
    val stock: String = "",
    val esOferta: Boolean = false,
    val activo: Boolean = true,

    val imagenActualKey: String = "",
    val imagenPendienteKey: String? = null,
    val imagenQuitada: Boolean = false,

    val cargando: Boolean = false,
    val procesandoImagen: Boolean = false,
    val guardando: Boolean = false,

    val error: String? = null,
    val guardadoCorrectamente: Boolean = false,
    val noDisponible: Boolean = false
) {
    val esEdicion: Boolean
        get() = id != 0L

    /** Clave que se mostrará en la vista previa y que se guardará con el producto. */
    val imagenVisibleKey: String
        get() = imagenPendienteKey?.takeIf { it.isNotBlank() }
            ?: if (imagenQuitada) "" else imagenActualKey

    val ocupado: Boolean
        get() = cargando || procesandoImagen || guardando
}
