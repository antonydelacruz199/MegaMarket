package com.megamarket.app.data.repository

import com.megamarket.modelo.CategoriasBootstrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoriaBootstrapTest {

    @Test
    fun haySeisCategoriasLocalesSinUuidInventado() {
        assertEquals(6, CategoriasBootstrap.NOMBRES.size)
        assertTrue(CategoriasBootstrap.NOMBRES.contains("Abarrotes"))
        assertTrue(CategoriasBootstrap.NOMBRES.contains("Cuidado personal"))
    }

    @Test
    fun repositorioInsertaSoloSiTablaVacia() {
        val archivo = File("src/main/java/com/megamarket/app/data/repository/CategoriaRepository.kt")
        assertTrue(archivo.exists())
        val fuente = archivo.readText(Charsets.UTF_8)
        assertTrue(fuente.contains("if (dao.contar() > 0) return@withContext"))
        assertTrue(fuente.contains("CategoriasBootstrap.NOMBRES"))
        assertTrue(!fuente.contains("UUID.randomUUID"))
    }
}
