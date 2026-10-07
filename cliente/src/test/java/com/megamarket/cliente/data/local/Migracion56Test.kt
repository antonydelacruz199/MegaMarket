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
        assertTrue(fuente.contains("version = 6"))
        assertTrue(fuente.contains("MIGRACION_5_6"))
        assertTrue(fuente.contains("migrarProductosProviderId"))
        assertTrue(fuente.contains("provider_id"))
        assertTrue(fuente.contains("productos_nueva"))
        assertTrue(fuente.contains("SELECT `id`, `id`, `remote_id`"))
        assertTrue(fuente.contains("index_productos_provider_id"))
        assertTrue(
            fuente.contains(
                "addMigrations(MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6)"
            )
        )
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 5)"))
        assertFalse(fuente.contains("maxId()"))
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
        assertTrue(fuente.contains("providerId"))
        assertTrue(fuente.contains("""Index(value = ["provider_id"], unique = true)"""))
        assertTrue(fuente.contains("""Index(value = ["remote_id"], unique = true)"""))
    }
}
