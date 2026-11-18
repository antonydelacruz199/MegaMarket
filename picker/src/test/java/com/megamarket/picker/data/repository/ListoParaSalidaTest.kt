package com.megamarket.picker.data.repository

import com.megamarket.picker.data.remote.LineaPickingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListoParaSalidaTest {

    @Test
    fun todasArmadasEstanListas() {
        val prep = PreparacionPedido(
            id = "p1",
            totalCentimos = 100,
            fecha = null,
            modalidadPago = null,
            estadoPago = null,
            grupos = listOf(
                GrupoPasillo(
                    pasillo = "A",
                    lineas = listOf(
                        linea("1", "ARMADO"),
                        linea("2", "SUSTITUIDO", armada = 1, sustitucionId = "s1")
                    )
                )
            )
        )
        assertTrue(prep.listoParaSalida())
    }

    @Test
    fun faltanteOPendienteNoEstaLista() {
        assertFalse(
            PreparacionPedido(
                id = "p1",
                totalCentimos = 100,
                fecha = null,
                modalidadPago = null,
                estadoPago = null,
                grupos = listOf(
                    GrupoPasillo("A", listOf(linea("1", "ARMADO"), linea("2", "FALTANTE")))
                )
            ).listoParaSalida()
        )
        assertFalse(
            PreparacionPedido(
                id = "p1",
                totalCentimos = 100,
                fecha = null,
                modalidadPago = null,
                estadoPago = null,
                grupos = listOf(
                    GrupoPasillo("A", listOf(linea("1", "ARMADO", pendiente = 1)))
                )
            ).listoParaSalida()
        )
    }

    @Test
    fun sustitutoAceptadoPendienteDeEscaneoBloqueaCierre() {
        val linea = linea(
            id = "1",
            estado = "FALTANTE",
            armada = 0,
            sustitucionId = "s-ok",
            nombreSustituto = "Leche light"
        )
        assertTrue(linea.requiereEscaneoSustituto())
        assertEquals("Leche light", linea.nombreVigente())
        assertFalse(
            PreparacionPedido(
                id = "p1",
                totalCentimos = 100,
                fecha = null,
                modalidadPago = null,
                estadoPago = null,
                grupos = listOf(GrupoPasillo("B", listOf(linea)))
            ).listoParaSalida()
        )
    }

    @Test
    fun eliminadoConRechazoCuentaParaCierre() {
        val linea = linea("1", "ELIMINADO", armada = 0)
        assertFalse(linea.requiereEscaneoSustituto())
        assertTrue(
            PreparacionPedido(
                id = "p1",
                totalCentimos = 100,
                fecha = null,
                modalidadPago = null,
                estadoPago = null,
                grupos = listOf(GrupoPasillo("A", listOf(linea)))
            ).listoParaSalida()
        )
    }

    @Test
    fun dtoConservaMarcaDeResolucion() {
        val linea = LineaPickingDto(
            id = "d1",
            pedidoId = "p1",
            productoId = "prod",
            nombreProducto = "Leche",
            cantidad = 1,
            cantidadArmada = 0,
            estadoPicking = "FALTANTE",
            precioUnitarioCentimos = 100,
            subtotalCentimos = 100,
            pasillo = "A",
            codigoBarras = "770",
            fechaEscaneo = null,
            escaneadoPor = null,
            resolubleSinProducto = true
        ).aLinea("http://10.0.2.2:8000/")
        assertTrue(linea!!.resolubleSinProducto)
        assertEquals("FALTANTE", linea.estadoPicking)
    }

    @Test
    fun cantidadIncompletaBloqueaAunqueEstadoSeaArmado() {
        assertFalse(
            PreparacionPedido(
                id = "p1",
                totalCentimos = 100,
                fecha = null,
                modalidadPago = null,
                estadoPago = null,
                grupos = listOf(
                    GrupoPasillo("A", listOf(linea("1", "ARMADO", armada = 1, cantidad = 2)))
                )
            ).listoParaSalida()
        )
    }

    private fun linea(
        id: String,
        estado: String,
        pendiente: Int = 0,
        armada: Int? = null,
        cantidad: Int = 1,
        sustitucionId: String? = null,
        nombreSustituto: String? = null
    ) = LineaPreparacion(
        id = id,
        productoId = "prod-$id",
        nombre = "P$id",
        cantidad = cantidad,
        cantidadArmada = armada ?: when (estado) {
            "ARMADO", "SUSTITUIDO" -> cantidad
            else -> 0
        },
        estadoPicking = estado,
        pasillo = "A",
        imagenUrl = null,
        cantidadPendienteLocal = pendiente,
        sustitucionAceptadaId = sustitucionId,
        productoSustitutoId = sustitucionId?.let { "sust-$id" },
        nombreSustituto = nombreSustituto
    )
}
