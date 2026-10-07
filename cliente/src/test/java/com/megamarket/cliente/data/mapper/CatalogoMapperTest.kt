package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.entities.CategoriaEntity
import com.megamarket.cliente.data.remote.dto.CategoriaDto
import com.megamarket.cliente.data.remote.dto.ProductoDto
import com.megamarket.modelo.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoMapperTest {

    @Test
    fun productoDto_mapeaRemoteIdSinInventarUuid() {
        val dto = ProductoDto(
            id = "550e8400-e29b-41d4-a716-446655440000",
            nombre = "Arroz Extra 5 kg",
            marca = "Costeño",
            descripcion = "Grano largo",
            categoriaId = "cat-uuid-1",
            precioCentimos = 2500,
            precioOfertaCentimos = 2000,
            stock = 12,
            imagenKey = "arroz.jpg",
            esOferta = true,
            activo = true,
            version = 4,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-02T00:00:00Z",
            deletedAt = null
        )
        val entidad = dto.aEntidad(categoriaIdLocal = 1L, idLocal = 15L)
        assertEquals(15L, entidad.id)
        assertEquals(dto.id, entidad.remoteId)
        assertEquals(1L, entidad.categoriaId)
        assertEquals(4L, entidad.remoteVersion)
        assertEquals("2026-01-02T00:00:00Z", entidad.remoteUpdatedAt)
        assertNull(entidad.remoteDeletedAt)
    }

    @Test
    fun categoriaDto_mapeaRemoteId() {
        val dto = CategoriaDto(
            id = "cat-uuid-abarrotes",
            nombre = "Abarrotes",
            version = 1,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        val entidad = dto.aEntidad(idLocal = 3L)
        assertEquals(3L, entidad.id)
        assertEquals("cat-uuid-abarrotes", entidad.remoteId)
        assertEquals("Abarrotes", entidad.nombre)
    }

    @Test
    fun categoriaRemotaUuid_seConvierteAIdLocal() {
        val locales = listOf(
            CategoriaEntity(id = 1, remoteId = null, nombre = "Abarrotes"),
            CategoriaEntity(id = 2, remoteId = "cat-uuid-bebidas", nombre = "Bebidas")
        )
        assertEquals(2L, resolverCategoriaIdLocal("cat-uuid-bebidas", locales))
        assertNull(resolverCategoriaIdLocal("desconocido", locales))
    }

    @Test
    fun productoModel_conservaRemoteId() {
        val producto = Producto(
            id = 9,
            nombre = "Gaseosa",
            marca = "Inca",
            descripcion = "",
            categoriaId = 2,
            precioCentimos = 300,
            stock = 8,
            imagenKey = "",
            esOferta = false,
            remoteId = "uuid-prod-9"
        )
        val entidad = producto.toEntity()
        assertEquals("uuid-prod-9", entidad.remoteId)
        assertEquals("uuid-prod-9", entidad.toModel().remoteId)
    }

    @Test
    fun softDelete_desactivaProducto() {
        val dto = ProductoDto(
            id = "u1",
            nombre = "X",
            marca = "Y",
            descripcion = "",
            categoriaId = "c1",
            precioCentimos = 1,
            stock = 0,
            imagenKey = "",
            esOferta = false,
            activo = true,
            version = 1,
            createdAt = "a",
            updatedAt = "b",
            deletedAt = "2026-02-01T00:00:00Z"
        )
        val entidad = dto.aEntidad(categoriaIdLocal = 1, idLocal = 1)
        assertTrue(!entidad.activo)
        assertEquals("2026-02-01T00:00:00Z", entidad.remoteDeletedAt)
    }
}
