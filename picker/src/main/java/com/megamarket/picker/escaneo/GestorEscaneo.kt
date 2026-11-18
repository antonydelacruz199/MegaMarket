package com.megamarket.picker.escaneo

import java.util.UUID

const val CANTIDAD_ESCANEO = 1

fun textoCodigoBarras(valor: String): String = valor.trim()

data class SolicitudEscaneo(
    val uuidOperacion: String,
    val codigoBarras: String,
    val cantidad: Int
)

class GestorEscaneo(
    private val nuevoUuid: () -> String = { UUID.randomUUID().toString() }
) {
    private enum class Fase { ESCUCHANDO, ENVIANDO, REINTENTO, RESULTADO }

    private var fase = Fase.ESCUCHANDO
    private var vioHueco = true
    private var ultimoEnviado: String? = null
    private var uuid: String? = null
    private var codigo: String? = null

    fun frame(codigoLeido: String?): SolicitudEscaneo? = synchronized(this) {
        val texto = codigoLeido?.let(::textoCodigoBarras).orEmpty()
        if (texto.isEmpty()) {
            if (fase != Fase.ENVIANDO) vioHueco = true
            return null
        }
        if (fase != Fase.ESCUCHANDO) return null
        if (!vioHueco && texto == ultimoEnviado) return null
        iniciar(texto)
    }

    fun manual(codigoLeido: String): SolicitudEscaneo? = synchronized(this) {
        val texto = textoCodigoBarras(codigoLeido)
        if (texto.isEmpty()) return null
        if (fase == Fase.ENVIANDO) return null
        if (fase == Fase.REINTENTO && texto == codigo && uuid != null) {
            fase = Fase.ENVIANDO
            return SolicitudEscaneo(uuid!!, texto, CANTIDAD_ESCANEO)
        }
        if (fase != Fase.ESCUCHANDO) {
            uuid = null
            codigo = null
            fase = Fase.ESCUCHANDO
        }
        iniciar(texto)
    }

    fun reintentar(): SolicitudEscaneo? = synchronized(this) {
        if (fase != Fase.REINTENTO) return null
        val id = uuid ?: return null
        val texto = codigo ?: return null
        fase = Fase.ENVIANDO
        SolicitudEscaneo(id, texto, CANTIDAD_ESCANEO)
    }

    fun falloRecuperable() = synchronized(this) {
        if (fase == Fase.ENVIANDO) fase = Fase.REINTENTO
    }

    fun resultadoListo() = synchronized(this) {
        uuid = null
        codigo = null
        fase = Fase.RESULTADO
        vioHueco = false
    }

    fun continuar() = synchronized(this) {
        if (fase == Fase.ENVIANDO) return
        uuid = null
        codigo = null
        fase = Fase.ESCUCHANDO
    }

    private fun iniciar(texto: String): SolicitudEscaneo {
        val id = nuevoUuid()
        uuid = id
        codigo = texto
        ultimoEnviado = texto
        vioHueco = false
        fase = Fase.ENVIANDO
        return SolicitudEscaneo(id, texto, CANTIDAD_ESCANEO)
    }
}
