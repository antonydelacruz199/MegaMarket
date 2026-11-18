package com.megamarket.picker.escaneo

import org.junit.Assert.assertEquals
import org.junit.Test

class FiltroFotogramasTest {

    @Test
    fun ignoraElMismoCodigoEnFotogramasSeguidos() {
        val filtro = FiltroFotogramas()
        assertEquals(CambioLectura.Codigo("770001"), filtro.observar("770001"))
        assertEquals(CambioLectura.Igual, filtro.observar("770001"))
        assertEquals(CambioLectura.Igual, filtro.observar("770001"))
        assertEquals(CambioLectura.Vacio, filtro.observar(null))
        assertEquals(CambioLectura.Igual, filtro.observar(""))
        assertEquals(CambioLectura.Codigo("770001"), filtro.observar("770001"))
    }

    @Test
    fun conservaCerosAlInformarElCodigo() {
        val filtro = FiltroFotogramas()
        assertEquals(CambioLectura.Codigo("00770001"), filtro.observar("00770001"))
    }
}
