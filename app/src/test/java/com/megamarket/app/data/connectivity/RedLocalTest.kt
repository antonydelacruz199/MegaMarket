package com.megamarket.app.data.connectivity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedLocalTest {

    @Test
    fun reconoceHostsDeDesarrolloYLan() {
        assertTrue(esUrlRedLocal("http://10.0.2.2:8010/"))
        assertTrue(esUrlRedLocal("http://127.0.0.1:8010/"))
        assertTrue(esUrlRedLocal("http://localhost:8010/"))
        assertTrue(esUrlRedLocal("http://10.1.2.3:8010/"))
        assertTrue(esUrlRedLocal("http://192.168.1.20:8010/"))
        assertTrue(esUrlRedLocal("http://172.16.0.5:8010/"))
        assertTrue(esUrlRedLocal("http://172.31.255.1:8010/"))
    }

    @Test
    fun rechazaHostsPublicos() {
        assertFalse(esUrlRedLocal("https://api.megamarket.com/"))
        assertFalse(esUrlRedLocal("https://example.com/v1/"))
        assertFalse(esUrlRedLocal("http://172.15.0.1:8010/"))
        assertFalse(esUrlRedLocal("http://8.8.8.8/"))
    }
}
