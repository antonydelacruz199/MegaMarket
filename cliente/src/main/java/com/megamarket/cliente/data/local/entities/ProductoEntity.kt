package com.megamarket.cliente.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Copia local del catálogo para offline-first.
 * [id] suele coincidir con el id del administrador durante el bootstrap por Provider;
 * [remoteId] es el UUID de Neon cuando exista la API.
 */
@Entity(
    tableName = "productos",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoriaId"]),
        Index(value = ["remote_id"], unique = true)
    ]
)
data class ProductoEntity(
    @PrimaryKey(autoGenerate = false) val id: Long,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long?,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long? = null,
    @ColumnInfo(name = "remote_updated_at") val remoteUpdatedAt: String? = null,
    @ColumnInfo(name = "remote_deleted_at") val remoteDeletedAt: String? = null
)
