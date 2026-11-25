package com.megamarket.app.state

import com.megamarket.app.data.repository.ErroresProveedor
import com.megamarket.app.data.repository.OrdenCompra
import com.megamarket.app.data.repository.Proveedor

enum class FiltroProveedor(val etiqueta: String, val activo: Boolean?) {
    TODOS("Todos", null),
    ACTIVOS("Activos", true),
    INACTIVOS("Inactivos", false)
}

/** Estado del formulario de alta/edición de proveedor. */
data class FormularioProveedorUiState(
    val proveedorId: String? = null,
    val ruc: String = "",
    val razonSocial: String = "",
    val telefono: String = "",
    val correo: String = "",
    val cuentaBancaria: String = "",
    val activo: Boolean = true,
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val guardado: Boolean = false,
    val noEncontrado: Boolean = false,
    val errores: ErroresProveedor = ErroresProveedor(),
    val error: String? = null
) {
    val esEdicion: Boolean get() = proveedorId != null
}

data class ProveedoresUiState(
    // Lista
    val cargando: Boolean = false,
    val cargandoMas: Boolean = false,
    val proveedores: List<Proveedor> = emptyList(),
    val consulta: String = "",
    val filtro: FiltroProveedor = FiltroProveedor.TODOS,
    val pagina: Int = 1,
    val hayMas: Boolean = false,
    val total: Int = 0,
    val error: String? = null,
    // Detalle
    val cargandoDetalle: Boolean = false,
    val detalle: Proveedor? = null,
    val ordenesProveedor: List<OrdenCompra> = emptyList(),
    val errorDetalle: String? = null,
    val errorOrdenesProveedor: String? = null,
    // Formulario
    val formulario: FormularioProveedorUiState = FormularioProveedorUiState()
)
