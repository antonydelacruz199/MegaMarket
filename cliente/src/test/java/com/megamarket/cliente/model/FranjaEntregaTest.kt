package com.megamarket.cliente.model

import com.megamarket.cliente.data.remote.FranjaApiDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FranjaEntregaTest {

    @Test
    fun dtoDisponibleSeMapeaSinExponerUuidComoEtiqueta() {
        val franja = FranjaEntrega.desdeDto(
            FranjaApiDto(
                id = "11111111-2222-3333-4444-555555555555",
                fecha = "2026-11-18",
                horaInicio = "10:00:00",
                horaFin = "12:00:00",
                capacidad = 20,
                ocupados = 3,
                cupoRestante = 17,
                activa = true
            )
        )
        assertNotNull(franja)
        assertTrue(franja!!.disponible)
        assertEquals("17 de 20 cupos", franja.etiquetaCupo)
        assertFalse(franja.etiquetaCompleta.contains("11111111"))
        assertEquals("10:00 a.m. – 12:00 p.m.", franja.etiquetaHorario)
    }

    @Test
    fun formateaHorasEnAmPm() {
        assertEquals("12:00 a.m.", FranjaEntrega.formatearHora("00:00:00"))
        assertEquals("10:00 a.m.", FranjaEntrega.formatearHora("10:00"))
        assertEquals("12:00 p.m.", FranjaEntrega.formatearHora("12:00:00"))
        assertEquals("2:00 p.m.", FranjaEntrega.formatearHora("14:00:00"))
    }

    @Test
    fun franjaAgotadaNoEstaDisponible() {
        val franja = FranjaEntrega.desdeDto(
            FranjaApiDto(
                id = "f1",
                fecha = "2026-11-19T00:00:00.000Z",
                horaInicio = "14:00",
                horaFin = "16:00",
                capacidad = 1,
                ocupados = 1,
                cupoRestante = 0,
                activa = true
            )
        )
        assertNotNull(franja)
        assertFalse(franja!!.disponible)
        assertEquals("Agotada", franja.etiquetaCupo)
    }

    @Test
    fun dtoSinIdSeDescarta() {
        assertNull(
            FranjaEntrega.desdeDto(
                FranjaApiDto(
                    id = " ",
                    fecha = "2026-11-18",
                    horaInicio = "10:00",
                    horaFin = "11:00",
                    capacidad = 5,
                    ocupados = 0,
                    cupoRestante = 5,
                    activa = true
                )
            )
        )
    }

    @Test
    fun etiquetaDesdeCamposNoInventaDatos() {
        assertNull(FranjaEntrega.etiquetaDesdeCampos(null, "10:00", "12:00"))
        assertNotNull(FranjaEntrega.etiquetaDesdeCampos("2026-11-18", "10:00:00", "12:00:00"))
    }
}
