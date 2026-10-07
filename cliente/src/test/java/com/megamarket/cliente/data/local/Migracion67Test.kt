package com.megamarket.cliente.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Migracion67Test {

    @Test
    fun migracion_6_7_movimientosOpsYMetadata() {
        val archivo = File("src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 7"))
        assertTrue(fuente.contains("MIGRACION_6_7"))
        assertTrue(fuente.contains("migrarAVersion7"))
        assertTrue(fuente.contains("movimientos_inventario"))
        assertTrue(fuente.contains("sync_metadata"))
        assertTrue(fuente.contains("descuento_porcentaje"))
        assertTrue(fuente.contains("uuidOperacion"))
        assertTrue(fuente.contains("clientUuid"))
        assertTrue(fuente.contains("ACTUALIZAR_STOCK incompatible"))
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 6)"))
    }
}
