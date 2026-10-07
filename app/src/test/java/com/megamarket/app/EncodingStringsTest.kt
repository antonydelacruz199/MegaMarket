package com.megamarket.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EncodingStringsTest {

    @Test
    fun stringsAdmin_tienenCaracteresEspanoles() {
        val archivo = File("src/main/res/values/strings.xml")
        assertTrue("No se encontró strings.xml del administrador", archivo.exists())
        val texto = archivo.readText(Charsets.UTF_8)
        assertTrue(texto.contains("Contraseña"))
        assertTrue(texto.contains("Dirección"))
        assertTrue(texto.contains("Catálogo"))
        assertTrue(texto.contains("Confirmación"))
        assertTrue(texto.contains("Administración"))
        assertTrue(!texto.contains("ContraseÃ±a"))
        assertTrue(!texto.contains("Ã"))
    }
}
