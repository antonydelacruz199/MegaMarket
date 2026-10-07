package com.megamarket.cliente.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Copia local del catálogo (offline-first).
 *
 * Identidades: id (PK cliente), providerId (admin temporal), remoteId (UUID Neon).
 * Oferta: [descuentoPorcentaje] es fuente principal; precioOfertaCentimos compat.
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
        Index(value = ["provider_id"], unique = true),
        Index(value = ["remote_id"], unique = true)
    ]
)
data class ProductoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "provider_id") val providerId: Long? = null,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    val nombre: String,
    val marca: String,
    val descripcion: String,
    val categoriaId: Long,
    val precioCentimos: Long,
    val precioOfertaCentimos: Long?,
    @ColumnInfo(name = "descuento_porcentaje") val descuentoPorcentaje: Int = 0,
    val stock: Int,
    val imagenKey: String,
    val esOferta: Boolean,
    val activo: Boolean,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long? = null,
    @ColumnInfo(name = "remote_updated_at") val remoteUpdatedAt: String? = null,
    @ColumnInfo(name = "remote_deleted_at") val remoteDeletedAt: String? = null
)
