package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.entities.DireccionEntity
import com.megamarket.cliente.data.local.entities.PedidoDetalleEntity
import com.megamarket.cliente.data.local.entities.PedidoEntity
import com.megamarket.cliente.model.Pedido
import com.megamarket.cliente.model.PedidoDetalle
import org.junit.Assert.assertEquals
import org.junit.Test

class PedidoMapperTest {

    @Test
    fun pedidoLeido_conservaElPrecioHistoricoAunqueElCatalogoCambie() {
        val detalleGuardado = PedidoDetalle(
            productoId = 1,
            nombreProducto = "Arroz Costeño",
            precioUnitarioCentimos = 480,
            cantidad = 2,
            subtotalCentimos = 960
        ).toEntity(pedidoId = 10)

        val pedido = PedidoEntity(
            id = 10,
            clienteId = 3,
            fecha = 1_700_000_000_000,
            totalCentimos = 960,
            estado = Pedido.ESTADO_CONFIRMADO
        ).toModel(
            detalles = listOf(detalleGuardado.copy(id = 1)),
            direccion = DireccionEntity(
                id = 1,
                pedidoId = 10,
                departamento = "Lima",
                provincia = "Lima",
                distrito = "Miraflores",
                direccion = "Av. Larco 123",
                telefono = "987654321"
            )
        )

        assertEquals(10L, detalleGuardado.pedidoId)
        assertEquals(480L, pedido.detalles.single().precioUnitarioCentimos)
        assertEquals("Arroz Costeño", pedido.detalles.single().nombreProducto)
        assertEquals(960L, pedido.totalCentimos)
        assertEquals(2, pedido.cantidadProductos)
        assertEquals("Miraflores", pedido.direccion.distrito)
    }

    @Test
    fun detalleEntity_seConvierteSinRecalcular() {
        val entidad = PedidoDetalleEntity(
            id = 5,
            pedidoId = 10,
            productoId = 2,
            nombreProducto = "Aceite",
            precioUnitarioCentimos = 990,
            cantidad = 3,
            subtotalCentimos = 2_970
        )
        val modelo = entidad.toModel()
        assertEquals(5L, modelo.id)
        assertEquals(990L, modelo.precioUnitarioCentimos)
        assertEquals(2_970L, modelo.subtotalCentimos)
    }
}
