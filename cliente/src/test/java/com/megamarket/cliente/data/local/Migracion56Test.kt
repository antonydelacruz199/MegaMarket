package com.megamarket.cliente.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Migracion56Test {

    @Test
    fun migracion_5_6_desacoplaPkYCopiaProviderId() {
        val archivo = File("src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 7"))
        assertTrue(fuente.contains("MIGRACION_5_6"))
        assertTrue(fuente.contains("migrarProductosProviderId"))
        assertTrue(fuente.contains("provider_id"))
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 5)"))
    }

    @Test
    fun productoEntity_usaAutoGenerateYProviderId() {
        val archivo = File(
            "src/main/java/com/megamarket/cliente/data/local/entities/ProductoEntity.kt"
        )
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("autoGenerate = true"))
        assertTrue(fuente.contains("provider_id"))
        assertTrue(fuente.contains("descuento_porcentaje"))
    }
}
