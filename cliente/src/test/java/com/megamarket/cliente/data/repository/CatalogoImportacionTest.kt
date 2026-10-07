package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.mapper.toEntity
import com.megamarket.modelo.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lógica de upsert Provider→Room (sin Android ContentResolver en JVM).
 */
class CatalogoImportacionTest {

    @Test
    fun segundaImportacionActualizaYNoDuplica() {
        val cache = mutableMapOf<Long, ProductoEntity>()
        fun upsert(producto: Producto) {
            cache[producto.id] = producto.toEntity()
        }

        upsert(
            Producto(
                id = 1,
                nombre = "Arroz",
                marca = "A",
                descripcion = "",
                categoriaId = 1,
                precioCentimos = 100,
                stock = 10,
                imagenKey = "",
                esOferta = false,
                remoteId = null
            )
        )
        upsert(
            Producto(
                id = 1,
                nombre = "Arroz Extra",
                marca = "A",
                descripcion = "",
                categoriaId = 1,
                precioCentimos = 100,
                stock = 7,
                imagenKey = "",
                esOferta = false,
                remoteId = "uuid-1"
            )
        )

        assertEquals(1, cache.size)
        assertEquals(7, cache.getValue(1).stock)
        assertEquals("uuid-1", cache.getValue(1).remoteId)
        assertEquals("Arroz Extra", cache.getValue(1).nombre)
    }

    @Test
    fun cambioStockActualizaCacheYConservaRemoteId() {
        var local = ProductoEntity(
            id = 2,
            remoteId = "uuid-2",
            nombre = "Leche",
            marca = "Gloria",
            descripcion = "",
            categoriaId = 3,
            precioCentimos = 400,
            precioOfertaCentimos = null,
            stock = 10,
            imagenKey = "",
            esOferta = false,
            activo = true
        )
        local = local.copy(stock = 7)
        assertEquals(7, local.stock)
        assertEquals("uuid-2", local.remoteId)
    }

    @Test
    fun remoteIdInicialNullHastaApi() {
        val entity = Producto(
            id = 3,
            nombre = "Snack",
            marca = "X",
            descripcion = "",
            categoriaId = 4,
            precioCentimos = 200,
            stock = 5,
            imagenKey = "",
            esOferta = false
        ).toEntity()
        assertNull(entity.remoteId)
    }
}
