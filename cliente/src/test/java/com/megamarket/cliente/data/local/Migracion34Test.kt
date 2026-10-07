package com.megamarket.cliente.data.local

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifica que la migración 3→4 está definida en el código fuente.
 * La ejecución Room real se valida al instalar/abrir la app sobre una BD v3.
 */
class Migracion34Test {

    @Test
    fun migracion_3_4_creaOperacionesPendientes() {
        val archivo = File("src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 4"))
        assertTrue(fuente.contains("MIGRACION_3_4"))
        assertTrue(fuente.contains("operaciones_pendientes"))
        assertTrue(fuente.contains("crearTablaOperacionesPendientes"))
        assertTrue(fuente.contains("addMigrations(MIGRACION_2_3, MIGRACION_3_4)"))
        assertTrue(!fuente.contains("fallbackToDestructiveMigrationFrom(true, 3)"))
    }
}
