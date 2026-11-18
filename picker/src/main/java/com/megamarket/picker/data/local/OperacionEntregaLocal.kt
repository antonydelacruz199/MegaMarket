package com.megamarket.picker.data.local

import android.content.Context

internal const val NOMBRE_PREFERENCIAS_ENTREGA = "megamarket_entrega_ops"

class OperacionEntregaLocal(contexto: Context) : AlmacenOperacionEntrega {

    private val prefs = contexto.applicationContext
        .getSharedPreferences(NOMBRE_PREFERENCIAS_ENTREGA, Context.MODE_PRIVATE)

    override fun uuidDe(pedidoId: String): String? =
        prefs.getString(claveUuid(pedidoId), null)?.takeIf { it.isNotBlank() }

    override fun guardarUuid(pedidoId: String, uuid: String) {
        prefs.edit().putString(claveUuid(pedidoId), uuid).commit()
    }

    override fun fotoDe(pedidoId: String): String? =
        prefs.getString(claveFoto(pedidoId), null)?.takeIf { it.isNotBlank() }

    override fun guardarFoto(pedidoId: String, fotoReferencia: String) {
        prefs.edit().putString(claveFoto(pedidoId), fotoReferencia).commit()
    }

    override fun limpiar(pedidoId: String) {
        prefs.edit()
            .remove(claveUuid(pedidoId))
            .remove(claveFoto(pedidoId))
            .commit()
    }

    private fun claveUuid(pedidoId: String) = "uuid_$pedidoId"

    private fun claveFoto(pedidoId: String) = "foto_$pedidoId"
}
