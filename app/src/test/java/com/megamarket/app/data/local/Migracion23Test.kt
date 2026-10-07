package com.megamarket.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifica que la migración admin 2→3 está definida sin destructive fallback en 2→3.
 */
class Migracion23Test {

    @Test
    fun migracion_2_3_conservaProductosYAgregaRemoteId() {
        val archivo = File("src/main/java/com/megamarket/app/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 3"))
        assertTrue(fuente.contains("MIGRACION_2_3"))
        assertTrue(fuente.contains("migrarProductosYCategorias"))
        assertTrue(fuente.contains("productos_nueva"))
        assertTrue(fuente.contains("remote_id"))
        assertTrue(fuente.contains("categorias"))
        assertTrue(fuente.contains("addMigrations(MIGRACION_2_3)"))
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 2)"))
    }
}
