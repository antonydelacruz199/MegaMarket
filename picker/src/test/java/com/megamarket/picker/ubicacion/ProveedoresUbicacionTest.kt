package com.megamarket.picker.ubicacion

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProveedoresUbicacionTest {

    @Test
    fun permisoAproximadoNoConsultaElGps() {
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER),
            proveedoresLectura(permisoFino = false, gpsActivo = true, redActiva = true)
        )
    }

    @Test
    fun permisoFinoPrefiereGpsYConservaLaRedComoRespaldo() {
        assertEquals(
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            proveedoresLectura(permisoFino = true, gpsActivo = true, redActiva = true)
        )
    }

    @Test
    fun sinProveedorPermitidoNoHayLectura() {
        assertTrue(proveedoresLectura(permisoFino = false, gpsActivo = true, redActiva = false).isEmpty())
    }
}
