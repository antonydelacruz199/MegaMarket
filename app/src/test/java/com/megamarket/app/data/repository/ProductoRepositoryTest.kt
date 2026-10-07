package com.megamarket.app.data.repository

import com.megamarket.modelo.TipoMovimientoInventario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductoRepositoryTest {

    @Test
    fun ajusteStock_10a15_esEntradaDe5() {
        val ajuste = ProductoInventarioLocal.calcularAjuste(10, 15)
        requireNotNull(ajuste)
        assertEquals(TipoMovimientoInventario.AJUSTE_ENTRADA, ajuste.tipo)
        assertEquals(5, ajuste.cantidad)
    }

    @Test
    fun stockNegativo_rechazado() {
        assertNull(ProductoInventarioLocal.calcularAjuste(10, -1))
    }
}
