package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.entities.DireccionEntity
import com.megamarket.cliente.data.local.entities.PedidoDetalleEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity
import com.megamarket.cliente.model.Direccion
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.model.PedidoDetalle

fun PedidoEntity.toModel(
    detalles: List<PedidoDetalleEntity>,
    direccion: DireccionEntity
): Pedido = Pedido(
    id = id,
    clienteId = clienteId,
    fecha = fecha,
    totalCentimos = totalCentimos,
    estado = estado,
    detalles = detalles.map { it.toModel() },
    direccion = direccion.toModel()
)

fun PedidoDetalleEntity.toModel(): PedidoDetalle = PedidoDetalle(
    id = id,
    productoId = productoId,
    nombreProducto = nombreProducto,
    precioUnitarioCentimos = precioUnitarioCentimos,
    cantidad = cantidad,
    subtotalCentimos = subtotalCentimos
)

fun PedidoDetalle.toEntity(pedidoId: Long): PedidoDetalleEntity = PedidoDetalleEntity(
    pedidoId = pedidoId,
    productoId = productoId,
    nombreProducto = nombreProducto,
    precioUnitarioCentimos = precioUnitarioCentimos,
    cantidad = cantidad,
    subtotalCentimos = subtotalCentimos
)

fun DireccionEntity.toModel(): Direccion = Direccion(
    departamento = departamento,
    provincia = provincia,
    distrito = distrito,
    direccion = direccion,
    telefono = telefono
)

fun Direccion.toEntity(pedidoId: Long): DireccionEntity = DireccionEntity(
    pedidoId = pedidoId,
    departamento = departamento,
    provincia = provincia,
    distrito = distrito,
    direccion = direccion,
    telefono = telefono
)
