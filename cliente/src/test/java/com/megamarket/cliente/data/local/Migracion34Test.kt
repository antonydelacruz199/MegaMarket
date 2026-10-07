package com.megamarket.cliente.data.local

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Migracion34Test {

    @Test
    fun migracion_3_4_creaOperacionesPendientes() {
        val archivo = File("src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 6"))
        assertTrue(fuente.contains("MIGRACION_3_4"))
        assertTrue(fuente.contains("operaciones_pendientes"))
        assertTrue(fuente.contains("crearTablaOperacionesPendientes"))
        assertTrue(
            fuente.contains(
                "addMigrations(MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6)"
            )
        )
        assertTrue(!fuente.contains("fallbackToDestructiveMigrationFrom(true, 3)"))
    }
}
