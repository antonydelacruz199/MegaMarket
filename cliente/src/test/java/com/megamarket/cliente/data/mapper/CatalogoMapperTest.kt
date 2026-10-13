package com.megamarket.cliente.data.mapper

import com.megamarket.cliente.data.local.Producto
import com.megamarket.cliente.data.local.ProductoProviderSnapshot
import org.junit.Assert.assertEquals
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
    fun toModel_exponeIdLocalNoProviderId() {
        val entidad = Producto(
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
}
