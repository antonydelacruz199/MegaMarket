package com.megamarket.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Migracion34Test {

    @Test
    fun migracion_3_4_offlineFirstSinDestructive() {
        val archivo = File("src/main/java/com/megamarket/app/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 4"))
        assertTrue(fuente.contains("MIGRACION_3_4"))
        assertTrue(fuente.contains("migrarAVersion4"))
        assertTrue(fuente.contains("operaciones_pendientes"))
        assertTrue(fuente.contains("movimientos_inventario"))
        assertTrue(fuente.contains("sync_metadata"))
        assertTrue(fuente.contains("descuento_porcentaje"))
        assertTrue(fuente.contains("addMigrations(MIGRACION_2_3, MIGRACION_3_4)"))
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 3)"))
    }
}
