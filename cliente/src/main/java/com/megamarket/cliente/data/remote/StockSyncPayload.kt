package com.megamarket.cliente.data.remote

/** LEGACY — stock absoluto. Flujo activo usa MovimientoInventarioRequest (delta). */
@Deprecated("Usar MovimientoInventarioRequest / CREAR_MOVIMIENTO_INVENTARIO")
data class StockSyncPayload(
    val productoIdLocal: Long,
    val stock: Int
) {
    fun aJson(): String =
        """{"productoIdLocal":$productoIdLocal,"stock":$stock}"""

    companion object {
        private val PATRON = Regex("""\{"productoIdLocal":(\d+),"stock":(\d+)\}""")

        fun desdeJson(json: String): StockSyncPayload {
            val coincide = PATRON.matchEntire(json.trim())
                ?: throw IllegalArgumentException("payload de stock inválido")
            return StockSyncPayload(
                productoIdLocal = coincide.groupValues[1].toLong(),
                stock = coincide.groupValues[2].toInt()
            )
        }
    }
}
