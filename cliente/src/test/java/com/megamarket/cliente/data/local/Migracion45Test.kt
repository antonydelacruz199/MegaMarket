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
        assertTrue(fuente.contains("version = 7"))
        assertTrue(fuente.contains("MIGRACION_4_5"))
        assertTrue(fuente.contains("crearCatalogoLocal"))
        assertTrue(fuente.contains("MIGRACION_6_7"))
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 4)"))
    }
}
