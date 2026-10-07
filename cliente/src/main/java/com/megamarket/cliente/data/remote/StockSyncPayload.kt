package com.megamarket.cliente.data.remote

/** Payload idempotente: guarda el stock FINAL, nunca un delta a restar. */
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
