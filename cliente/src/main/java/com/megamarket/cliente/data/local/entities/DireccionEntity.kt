package com.megamarket.cliente.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "direcciones",
    foreignKeys = [
        ForeignKey(
            entity = PedidoEntity::class,
            parentColumns = ["id"],
            childColumns = ["pedidoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pedidoId"], unique = true)]
)
data class DireccionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pedidoId: Long,
    val departamento: String,
    val provincia: String,
    val distrito: String,
    val direccion: String,
    val telefono: String
)
