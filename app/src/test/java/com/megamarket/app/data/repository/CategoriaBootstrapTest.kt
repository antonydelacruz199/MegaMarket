package com.megamarket.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoriaBootstrapTest {

    @Test
    fun repositorioNoHaceBootstrapAutomatico() {
        val archivo = File("src/main/java/com/megamarket/app/data/repository/CategoriaRepository.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertFalse(fuente.contains("CategoriasBootstrap"))
        assertFalse(fuente.contains("asegurarBootstrap"))
        assertFalse(fuente.contains("UUID.randomUUID"))
    }
}
