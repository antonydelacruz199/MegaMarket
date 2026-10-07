package com.megamarket.cliente.data.local

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifica que la migración 3→4 sigue definida junto con la cadena hasta v5.
 */
class Migracion34Test {

    @Test
    fun migracion_3_4_creaOperacionesPendientes() {
        val archivo = File("src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 5"))
        assertTrue(fuente.contains("MIGRACION_3_4"))
        assertTrue(fuente.contains("operaciones_pendientes"))
        assertTrue(fuente.contains("crearTablaOperacionesPendientes"))
        assertTrue(fuente.contains("addMigrations(MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5)"))
        assertTrue(!fuente.contains("fallbackToDestructiveMigrationFrom(true, 3)"))
    }
}
