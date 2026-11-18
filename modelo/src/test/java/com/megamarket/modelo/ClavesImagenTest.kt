package com.megamarket.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClavesImagenTest {
    private val producto = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
    private val archivo = "11111111-2222-4333-8444-555555555555"
    private val remota = "productos/$producto/$archivo.jpg"

    @Test
    fun distingueLocalDeRemota() {
        assertTrue(ClavesImagen.esLocal("1710000000000.jpg"))
        assertFalse(ClavesImagen.esRemotaProducto("1710000000000.jpg"))
        assertTrue(ClavesImagen.esRemotaProducto(remota))
        assertFalse(ClavesImagen.esLocal(remota))
        assertFalse(ClavesImagen.esRemotaProducto("productos/../$archivo.jpg"))
        assertFalse(ClavesImagen.esRemotaProducto("content://foto"))
        assertFalse(ClavesImagen.esRemotaProducto("/storage/foto.jpg"))
    }

    @Test
    fun laClaveTieneQuePertenecerAlProducto() {
        assertTrue(ClavesImagen.perteneceAProducto(remota, producto))
        assertFalse(ClavesImagen.perteneceAProducto(remota, "bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee"))
        assertFalse(ClavesImagen.perteneceAProducto(remota, null))
    }

    @Test
    fun laUrlNoLlevaTokenYLaCacheUsaLaClave() {
        val solicitud = MediosImagen.solicitudProducto(
            "http://10.0.2.2:8010",
            producto,
            remota
        )
        assertEquals(
            "http://10.0.2.2:8010/api/medios/productos/$producto?download=1",
            solicitud?.url
        )
        assertEquals(remota, solicitud?.cacheKey)
        assertFalse(solicitud?.url.orEmpty().contains("Bearer"))
        assertFalse(solicitud?.url.orEmpty().contains("token"))
        assertNull(MediosImagen.solicitudProducto("http://10.0.2.2:8010/", producto, "1.jpg"))
        assertNull(MediosImagen.solicitudProducto("http://10.0.2.2:8010/", producto, ""))
    }
}
