package com.megamarket.cliente.data.remote

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

data class CrearPedidoRequest(
    val clientUuid: String,
    val direccion: DireccionPedidoRequest,
    val items: List<ItemPedidoRequest>
)

data class DireccionPedidoRequest(
    val departamento: String,
    val provincia: String,
    val distrito: String,
    val direccion: String,
    val telefono: String
)

data class ItemPedidoRequest(
    val productoId: String,
    val cantidad: Int
)

data class CrearPedidoResponse(
    val idempotente: Boolean?,
    val pedido: PedidoApiDto?
)

data class PedidoApiDto(
    val id: String?,
    @SerializedName("client_uuid") val clientUuid: String?,
    @SerializedName("cliente_id") val clienteId: String?,
    @SerializedName("total_centimos") val totalCentimos: Long?,
    val estado: String?,
    val version: Long?,
    val fecha: String?
)
