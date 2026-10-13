package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.Direccion as DireccionRoom
import com.megamarket.cliente.data.local.PedidoDetalle as PedidoDetalleRoom
import com.megamarket.cliente.data.local.Pedido as PedidoRoom
import com.megamarket.cliente.model.Direccion
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.model.PedidoDetalle

fun PedidoRoom.toModel(
    detalles: List<PedidoDetalleRoom>,
    direccion: DireccionRoom
): Pedido = Pedido(
    id = id,
    clienteId = clienteId,
    fecha = fecha,
    totalCentimos = totalCentimos,
    estado = estado,
    detalles = detalles.map { it.toModel() },
    direccion = direccion.toModel()
)

fun PedidoDetalleRoom.toModel(): PedidoDetalle = PedidoDetalle(
    id = id,
    productoId = productoId,
    nombreProducto = nombreProducto,
    precioUnitarioCentimos = precioUnitarioCentimos,
    cantidad = cantidad,
    subtotalCentimos = subtotalCentimos
)

fun PedidoDetalle.toEntity(pedidoId: Long): PedidoDetalleRoom = PedidoDetalleRoom(
    pedidoId = pedidoId,
    productoId = productoId,
    nombreProducto = nombreProducto,
    precioUnitarioCentimos = precioUnitarioCentimos,
    cantidad = cantidad,
    subtotalCentimos = subtotalCentimos
)

fun DireccionRoom.toModel(): Direccion = Direccion(
    departamento = departamento,
    provincia = provincia,
    distrito = distrito,
    direccion = direccion,
    telefono = telefono
)

fun Direccion.toEntity(pedidoId: Long): DireccionRoom = DireccionRoom(
    pedidoId = pedidoId,
    departamento = departamento,
    provincia = provincia,
    distrito = distrito,
    direccion = direccion,
    telefono = telefono
)
