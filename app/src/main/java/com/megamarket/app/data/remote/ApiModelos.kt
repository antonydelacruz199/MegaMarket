package com.megamarket.app.data.remote

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val usuario: String,
    val clave: String,
    val deviceName: String
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: String?,
    val usuario: UsuarioApiDto
)

data class UsuarioApiDto(
    val id: String,
    val nombre: String,
    val usuario: String,
    val correo: String,
    val rol: String,
    val version: Long?
)

data class ErrorApiDto(
    val error: String?
)

data class CategoriasResponse(
    val categorias: List<CategoriaApiDto>
)

data class CategoriaApiDto(
    val id: String,
    val nombre: String,
    val version: Long?,
    val createdAt: String?,
    val updatedAt: String?
)

data class ProductosResponse(
    val productos: List<ProductoApiDto>
)

data class ProductoResponse(
    val producto: ProductoApiDto
)

data class ProductoApiDto(
    val id: String,
    val nombre: String,
    val marca: String,
    val descripcion: String?,
    val categoriaId: String,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long?,
    val stock: Int,
    val imagenKey: String?,
    val esOferta: Boolean,
    val activo: Boolean,
    val version: Long?,
    val createdAt: String?,
    val updatedAt: String?
)

data class CrearProductoRequest(
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: String,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long?,
    val stock: Int,
    val imagenKey: String?,
    val esOferta: Boolean,
    val activo: Boolean,
    val uuidOperacion: String
)

data class ActualizarProductoRequest(
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: String,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long?,
    val esOferta: Boolean,
    val activo: Boolean,
    val imagenKey: String?,
    val version: Long,
    val uuidOperacion: String
)

data class EliminarProductoRequest(
    val version: Long?,
    val uuidOperacion: String
)

data class EliminarProductoResponse(
    val ok: Boolean?,
    val id: String?
)

data class MovimientoInventarioRequest(
    val uuidOperacion: String,
    val productoId: String,
    val tipo: String,
    val cantidad: Int,
    val pedidoId: String? = null
)

data class MovimientoInventarioResponse(
    val idempotente: Boolean?,
    @SerializedName("movimiento") val movimiento: MovimientoApiDto?,
    @SerializedName("producto") val producto: ProductoStockApiDto?
)

data class MovimientoApiDto(
    val id: String?,
    @SerializedName("uuid_operacion") val uuidOperacion: String?,
    @SerializedName("producto_id") val productoId: String?,
    val tipo: String?,
    val cantidad: Int?,
    val version: Long?
)

data class ProductoStockApiDto(
    val id: String?,
    val stock: Int?,
    val version: Long?
)
