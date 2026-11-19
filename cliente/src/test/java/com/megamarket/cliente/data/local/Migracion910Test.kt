package com.megamarket.cliente.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Migracion910Test {

    @Test
    fun migracion_9_10_agregaFranjaYModalidadSinRomperV2() {
        val archivo = File("src/main/java/com/megamarket/cliente/data/local/AppDatabase.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("version = 10"))
        assertTrue(fuente.contains("MIGRACION_9_10"))
        assertTrue(fuente.contains("`contrato_version`"))
        assertTrue(fuente.contains("`franja_entrega_id`"))
        assertTrue(fuente.contains("`modalidad_pago`"))
        assertTrue(fuente.contains("`franja_fecha`"))
        assertTrue(fuente.contains("`franja_hora_inicio`"))
        assertTrue(fuente.contains("`franja_hora_fin`"))
        assertTrue(fuente.contains("DEFAULT 2"))
        assertTrue(fuente.contains("MIGRACION_9_10"))
        assertFalse(fuente.contains("fallbackToDestructiveMigrationFrom(true, 9)"))
    }
}
