package com.megamarket.modelo

/**
 * Claves de foto de producto.
 * Local: vista previa en el administrador (`{timestamp}.jpg`), nunca es la referencia de Neon.
 * Remota: `productos/{productoId}/{uuid}.ext`, la guarda el API.
 */
object ClavesImagen {
    private val REMOTA = Regex(
        "^productos/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpe?g|png|webp)$",
        RegexOption.IGNORE_CASE
    )
    private val LOCAL = Regex("^\\d+\\.jpg$", RegexOption.IGNORE_CASE)

    fun esLocal(clave: String?): Boolean = LOCAL.matches(clave?.trim().orEmpty())

    fun esRemotaProducto(clave: String?): Boolean {
        val valor = clave?.trim().orEmpty()
        return valor.isNotEmpty() &&
            !valor.contains("..") &&
            !valor.contains('\\') &&
            !valor.contains("://") &&
            REMOTA.matches(valor)
    }

    fun perteneceAProducto(clave: String?, productoId: String?): Boolean {
        val id = productoId?.trim().orEmpty()
        val valor = clave?.trim().orEmpty()
        if (id.isEmpty() || id.contains('/') || id.contains('\\') || id.contains("..")) return false
        return esRemotaProducto(valor) && valor.startsWith("productos/$id/")
    }
}

data class SolicitudImagenProducto(
    val url: String,
    val cacheKey: String
)

object MediosImagen {
    fun solicitudProducto(
        baseUrl: String,
        remoteId: String?,
        imagenKey: String?
    ): SolicitudImagenProducto? {
        if (!ClavesImagen.perteneceAProducto(imagenKey, remoteId)) return null
        val raiz = baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
        if (!raiz.startsWith("http://") && !raiz.startsWith("https://")) return null
        val id = checkNotNull(remoteId).trim()
        val clave = checkNotNull(imagenKey).trim()
        return SolicitudImagenProducto(
            url = "${raiz}api/medios/productos/$id?download=1",
            cacheKey = clave
        )
    }
}
