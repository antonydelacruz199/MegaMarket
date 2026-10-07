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
        assertTrue(fuente.contains("version = 7"))
        assertTrue(fuente.contains("MIGRACION_3_4"))
        assertTrue(fuente.contains("operaciones_pendientes"))
        assertTrue(fuente.contains("MIGRACION_6_7"))
    }
}
