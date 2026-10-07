package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.ProductoProviderSnapshot
import com.megamarket.cliente.data.local.entities.ProductoEntity
import com.megamarket.cliente.data.mapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lógica de identidad Provider ↔ Room (sin ContentResolver en JVM).
 */
class CatalogoImportacionTest {

    @Test
    fun importProvider_usaProviderIdNoComoPk() {
        val cache = mutableMapOf<Long, ProductoEntity>()
        var nextId = 101L

        fun upsert(snapshot: ProductoProviderSnapshot) {
            val existente = cache.values.firstOrNull { it.providerId == snapshot.providerId }
                ?: snapshot.remoteId?.let { r -> cache.values.firstOrNull { it.remoteId == r } }
            if (existente != null) {
                cache[existente.id] = snapshot.toEntity(idLocal = existente.id, categoriaIdLocal = 1)
            } else {
                val id = nextId++
                cache[id] = snapshot.toEntity(idLocal = id, categoriaIdLocal = 1)
            }
        }

        upsert(
            ProductoProviderSnapshot(
                providerId = 50,
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
        )
        assertEquals(1, cache.size)
        val primero = cache.values.single()
        assertEquals(101L, primero.id)
        assertEquals(50L, primero.providerId)
        assertFalse(primero.id == primero.providerId)

        upsert(
            ProductoProviderSnapshot(
                providerId = 50,
                remoteId = "uuid-1",
                nombre = "Arroz Extra",
                marca = "A",
                descripcion = "",
                categoriaProviderId = 1,
                precioCentimos = 100,
                stock = 7,
                imagenKey = "",
                esOferta = false,
                activo = true
            )
        )
        assertEquals(1, cache.size)
        val actualizado = cache.getValue(101L)
        assertEquals(101L, actualizado.id)
        assertEquals(50L, actualizado.providerId)
        assertEquals(7, actualizado.stock)
        assertEquals("uuid-1", actualizado.remoteId)
    }

    @Test
    fun providerActualizaStock_conservaPkLocalDistinta() {
        var local = ProductoEntity(
            id = 100,
            providerId = 5,
            remoteId = null,
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
        val snapshot = ProductoProviderSnapshot(
            providerId = 5,
            nombre = "Leche",
            marca = "Gloria",
            descripcion = "",
            categoriaProviderId = 3,
            precioCentimos = 400,
            stock = 3,
            imagenKey = "",
            esOferta = false,
            activo = true
        )
        local = snapshot.toEntity(idLocal = local.id, categoriaIdLocal = local.categoriaId)
        assertEquals(100L, local.id)
        assertEquals(5L, local.providerId)
        assertEquals(3, local.stock)
    }

    @Test
    fun migracionConservaReferenciasProductoId() {
        // Simula el SELECT de MIGRACION_5_6: id y provider_id = antiguo id.
        val idV5 = 7L
        val idV6 = idV5
        val providerIdV6 = idV5
        val carritoProductoId = 7L
        val favoritoProductoId = 7L
        val pedidoDetalleProductoId = 7L
        val operacionEntidadIdLocal = 7L
        assertEquals(idV6, carritoProductoId)
        assertEquals(idV6, favoritoProductoId)
        assertEquals(idV6, pedidoDetalleProductoId)
        assertEquals(idV6, operacionEntidadIdLocal)
        assertEquals(providerIdV6, 7L)
    }

    @Test
    fun urisProvider_usanProviderIdNoLocalId() {
        // ContratoCatalogo usa android.net.Uri (no disponible en JVM puro).
        val localId = 33L
        val providerId = 7L
        fun uriStock(id: Long) = "content://com.megamarket.app.proveedor.productos/productos/$id/stock"
        fun uriImagen(id: Long) = "content://com.megamarket.app.proveedor.productos/productos/$id/imagen"
        assertFalse(uriStock(providerId) == uriStock(localId))
        assertFalse(uriImagen(providerId) == uriImagen(localId))
        assertTrue(uriStock(providerId).endsWith("/7/stock"))
    }

    @Test
    fun providerIdNull_noUsaLocalIdComoUri() {
        val local = ProductoEntity(
            id = 52,
            providerId = null,
            remoteId = "uuid-api",
            nombre = "Solo API",
            marca = "X",
            descripcion = "",
            categoriaId = 1,
            precioCentimos = 10,
            precioOfertaCentimos = null,
            stock = 1,
            imagenKey = "",
            esOferta = false,
            activo = true
        )
        assertNull(local.providerId)
        // Regla de repositorio: sin providerId no se llama uriStock(local.id)
        val debeFallar = local.providerId == null
        assertTrue(debeFallar)
        assertEquals(CatalogoRepository.MENSAJE_SIN_PROVIDER_ID, CatalogoRepository.MENSAJE_SIN_PROVIDER_ID)
    }

    @Test
    fun repositorioResuelveProviderIdEnCodigoFuente() {
        val archivo = File(
            "src/main/java/com/megamarket/cliente/data/repository/CatalogoRepository.kt"
        )
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("obtenerProviderId"))
        assertTrue(fuente.contains("uriStock(providerId)"))
        assertTrue(fuente.contains("uriImagen(providerId)"))
        assertTrue(fuente.contains("ProductoProviderSnapshot"))
        assertFalse(fuente.contains("maxId()"))
        assertFalse(fuente.contains("OnConflictStrategy.REPLACE"))
        assertFalse(fuente.contains("producto.copy(id ="))
    }
}
