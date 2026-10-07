package com.megamarket.cliente.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Migracion45Test {

    @Test
    fun migracion_4_5_creaCatalogoLocalSinDestructive() {
        val archivo = File("src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 6"))
        assertTrue(fuente.contains("MIGRACION_4_5"))
        assertTrue(fuente.contains("crearCatalogoLocal"))
        assertTrue(fuente.contains("categorias"))
        assertTrue(fuente.contains("productos"))
        assertTrue(fuente.contains("operaciones_pendientes"))
        assertTrue(
            fuente.contains(
                "addMigrations(MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6)"
            )
        )
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 4)"))
    }
}
