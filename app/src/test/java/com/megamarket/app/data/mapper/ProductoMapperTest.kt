package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.entities.ProductoEntity
import com.megamarket.modelo.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductoMapperTest {

    @Test
    fun mapper_conservaRemoteIdYMetadata() {
        val modelo = Producto(
            id = 15,
            nombre = "Arroz",
            marca = "Costeño",
            descripcion = "Extra",
            categoriaId = 1,
            precioCentimos = 1000,
            precioOfertaCentimos = null,
            stock = 10,
            imagenKey = "img1",
            esOferta = false,
            activo = true,
            remoteId = "550e8400-e29b-41d4-a716-446655440000",
            remoteVersion = 3,
            remoteUpdatedAt = "2026-01-01T00:00:00Z",
            remoteDeletedAt = null
        )
        val entidad = modelo.toEntity()
        assertEquals(modelo.remoteId, entidad.remoteId)
        assertEquals(modelo.remoteVersion, entidad.remoteVersion)
        assertEquals(modelo.remoteUpdatedAt, entidad.remoteUpdatedAt)

        val vuelta = entidad.toModel()
        assertEquals(modelo.remoteId, vuelta.remoteId)
        assertEquals(modelo.id, vuelta.id)
    }

    @Test
    fun remoteId_inicialmenteNull() {
        val entidad = ProductoEntity(
            id = 1,
            remoteId = null,
            nombre = "Leche",
            marca = "Gloria",
            descripcion = "",
            categoriaId = 1,
            precioCentimos = 500,
            precioOfertaCentimos = null,
            stock = 5,
            imagenKey = "",
            esOferta = false,
            activo = true
        )
        assertNull(entidad.toModel().remoteId)
    }
}
