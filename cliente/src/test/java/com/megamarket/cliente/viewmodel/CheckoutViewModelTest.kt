package com.megamarket.cliente.viewmodel

import com.megamarket.cliente.model.ModalidadPagoCheckout
import com.megamarket.cliente.state.CheckoutUiState
import com.megamarket.cliente.state.PasoCheckout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de avance del checkout (sin Room). El ViewModel aplica las mismas validaciones.
 */
class CheckoutViewModelTest {

    @Test
    fun resumenExigeFranjaYModalidad() {
        val soloDireccion = CheckoutUiState(
            paso = PasoCheckout.DIRECCION,
            departamento = "Junín",
            provincia = "Huancayo",
            distrito = "El Tambo",
            direccion = "Calle 1",
            telefono = "999999999"
        )
        assertFalse(puedeIrAResumen(soloDireccion))

        val conFranja = soloDireccion.copy(
            franjaSeleccionadaId = "f1",
            franjas = listOf(
                com.megamarket.cliente.model.FranjaEntrega(
                    id = "f1",
                    fechaIso = "2026-11-18",
                    horaInicio = "10:00:00",
                    horaFin = "12:00:00",
                    capacidad = 20,
                    ocupados = 0,
                    cupoRestante = 20,
                    activa = true
                )
            )
        )
        assertFalse(puedeIrAResumen(conFranja))
        assertTrue(puedeIrAPago(conFranja))

        val completo = conFranja.copy(modalidadPago = ModalidadPagoCheckout.ONLINE)
        assertTrue(puedeIrAResumen(completo))

        val agotada = completo.copy(
            franjas = completo.franjas.map { it.copy(cupoRestante = 0, ocupados = 20) }
        )
        assertFalse(puedeIrAResumen(agotada))
        assertEquals(ModalidadPagoCheckout.ONLINE, completo.modalidadPago)
        assertEquals(ModalidadPagoCheckout.CONTRAENTREGA.valorApi, "CONTRAENTREGA")
    }

    private fun puedeIrAPago(estado: CheckoutUiState): Boolean =
        estado.franjaSeleccionada?.disponible == true

    private fun puedeIrAResumen(estado: CheckoutUiState): Boolean =
        puedeIrAPago(estado) && estado.modalidadPago != null
}
