package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.ProductoProviderSnapshot
import com.megamarket.cliente.data.local.entities.CategoriaEntity
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.remote.dto.CategoriaDto
import com.megamarket.cliente.data.remote.dto.ProductoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoMapperTest {

    @Test
    fun snapshotProvider_nuncaUsaProviderIdComoPk() {
        val snapshot = ProductoProviderSnapshot(
            providerId = 50,
            remoteId = "uuid-x",
            nombre = "Arroz",
            marca = "A",
            descripcion = "",
            categoriaProviderId = 1,
            precioCentimos = 100,
            stock = 10,
            imagenKey = "",
            esOferta = false,
            activo = true
        )
        val nuevo = snapshot.toEntity(idLocal = 0, categoriaIdLocal = 1)
        assertEquals(0L, nuevo.id)
        assertEquals(50L, nuevo.providerId)
        assertEquals("uuid-x", nuevo.remoteId)

        val update = snapshot.toEntity(idLocal = 100, categoriaIdLocal = 1)
        assertEquals(100L, update.id)
        assertEquals(50L, update.providerId)
    }

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
        val entidad = dto.aEntidad(categoriaIdLocal = 1L, idLocal = 15L, providerIdExistente = 8L)
        assertEquals(15L, entidad.id)
        assertEquals(8L, entidad.providerId)
        assertEquals(dto.id, entidad.remoteId)
        assertNull(dto.aEntidad(categoriaIdLocal = 1L).providerId)
    }

    @Test
    fun apiNuevoProducto_idCeroParaAutogenerate() {
        val dto = ProductoDto(
            id = "uuid-nuevo",
            nombre = "Aceite",
            marca = "Primor",
            descripcion = "",
            categoriaId = "c1",
            precioCentimos = 900,
            stock = 3,
            imagenKey = "",
            esOferta = false,
            activo = true,
            version = 1,
            createdAt = "a",
            updatedAt = "b"
        )
        val entidad = dto.aEntidad(categoriaIdLocal = 2)
        assertEquals(0L, entidad.id)
        assertNull(entidad.providerId)
        assertEquals("uuid-nuevo", entidad.remoteId)
    }

    @Test
    fun remoteSync_conservaIdYProviderId() {
        val existente = ProductoEntity(
            id = 40,
            providerId = 8,
            remoteId = "UUID-X",
            nombre = "Viejo",
            marca = "M",
            descripcion = "",
            categoriaId = 1,
            precioCentimos = 100,
            precioOfertaCentimos = null,
            stock = 5,
            imagenKey = "",
            esOferta = false,
            activo = true
        )
        val dto = ProductoDto(
            id = "UUID-X",
            nombre = "Nuevo",
            marca = "M",
            descripcion = "act",
            categoriaId = "c1",
            precioCentimos = 120,
            stock = 9,
            imagenKey = "k",
            esOferta = false,
            activo = true,
            version = 2,
            createdAt = "a",
            updatedAt = "b"
        )
        val actualizado = dto.aEntidad(
            categoriaIdLocal = 1,
            idLocal = existente.id,
            providerIdExistente = existente.providerId
        )
        assertEquals(40L, actualizado.id)
        assertEquals(8L, actualizado.providerId)
        assertEquals("Nuevo", actualizado.nombre)
        assertEquals(9, actualizado.stock)
    }

    @Test
    fun toModel_exponeIdLocalNoProviderId() {
        val entidad = ProductoEntity(
            id = 100,
            providerId = 5,
            remoteId = "r1",
            nombre = "Leche",
            marca = "G",
            descripcion = "",
            categoriaId = 3,
            precioCentimos = 400,
            precioOfertaCentimos = null,
            stock = 7,
            imagenKey = "",
            esOferta = false,
            activo = true
        )
        val modelo = entidad.toModel()
        assertEquals(100L, modelo.id)
        assertEquals("r1", modelo.remoteId)
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
