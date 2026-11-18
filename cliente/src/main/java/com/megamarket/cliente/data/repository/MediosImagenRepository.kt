package com.megamarket.cliente.data.repository

import com.megamarket.modelo.MediosImagen
import com.megamarket.modelo.SolicitudImagenProducto

/** Arma la URL autenticada. Coil y el cliente HTTP descargan los bytes. */
class MediosImagenRepository(
    private val baseUrl: String
) {
    fun solicitud(remoteId: String?, imagenKey: String?): SolicitudImagenProducto? =
        MediosImagen.solicitudProducto(baseUrl, remoteId, imagenKey)

    /** Fallback para sustituciones cuando el catálogo local no tiene imagenKey remota. */
    fun solicitudPorId(remoteId: String?): SolicitudImagenProducto? {
        val id = remoteId?.trim().orEmpty()
        if (id.isEmpty()) return null
        val raiz = baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
        if (!raiz.startsWith("http://") && !raiz.startsWith("https://")) return null
        return SolicitudImagenProducto(
            url = "${raiz}api/medios/productos/$id?download=1",
            cacheKey = "producto/$id"
        )
    }
}
