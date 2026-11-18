package com.megamarket.picker.data.local

/**
 * Conserva uuidOperacion y la clave remota de la foto por pedido.
 * No guarda bytes de imagen.
 */
interface AlmacenOperacionEntrega {
    fun uuidDe(pedidoId: String): String?
    fun guardarUuid(pedidoId: String, uuid: String)
    fun fotoDe(pedidoId: String): String?
    fun guardarFoto(pedidoId: String, fotoReferencia: String)
    fun limpiar(pedidoId: String)
}

class MemoriaOperacionEntrega : AlmacenOperacionEntrega {
    private val uuids = mutableMapOf<String, String>()
    private val fotos = mutableMapOf<String, String>()

    override fun uuidDe(pedidoId: String): String? = uuids[pedidoId]

    override fun guardarUuid(pedidoId: String, uuid: String) {
        uuids[pedidoId] = uuid
    }

    override fun fotoDe(pedidoId: String): String? = fotos[pedidoId]

    override fun guardarFoto(pedidoId: String, fotoReferencia: String) {
        fotos[pedidoId] = fotoReferencia
    }

    override fun limpiar(pedidoId: String) {
        uuids.remove(pedidoId)
        fotos.remove(pedidoId)
    }
}
