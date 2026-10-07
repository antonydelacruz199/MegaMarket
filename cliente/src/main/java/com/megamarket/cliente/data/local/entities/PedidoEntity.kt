package com.megamarket.cliente.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pedidos",
    foreignKeys = [
        ForeignKey(
            entity = ClienteEntity::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["clienteId"])]
)
data class PedidoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    /** Momento de la confirmación en milisegundos desde epoch. */
    val fecha: Long,
    val totalCentimos: Long,
    val estado: String
)
