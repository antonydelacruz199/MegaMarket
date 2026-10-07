package com.megamarket.cliente.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Copia del producto al momento de comprar. Nombre y precio no se vuelven a leer
 * del catálogo para que el pedido conserve lo que realmente se pagó.
 */
@Entity(
    tableName = "pedido_detalle",
    foreignKeys = [
        ForeignKey(
            entity = PedidoEntity::class,
            parentColumns = ["id"],
            childColumns = ["pedidoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pedidoId"])]
)
data class PedidoDetalleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pedidoId: Long,
    val productoId: Long,
    val nombreProducto: String,
    val precioUnitarioCentimos: Long,
    val cantidad: Int,
    val subtotalCentimos: Long
)
