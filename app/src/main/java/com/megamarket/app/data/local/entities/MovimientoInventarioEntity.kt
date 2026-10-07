package com.megamarket.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movimientos_inventario",
    foreignKeys = [
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuidOperacion"], unique = true),
        Index(value = ["productoId"])
    ]
)
data class MovimientoInventarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuidOperacion: String,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    val productoId: Long,
    val pedidoId: Long? = null,
    val tipo: String,
    val cantidad: Int,
    val fechaCreacion: Long,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long? = null
)
