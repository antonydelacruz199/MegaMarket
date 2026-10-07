package com.megamarket.cliente.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categorias",
    indices = [
        Index(value = ["nombre"], unique = true),
        Index(value = ["remote_id"], unique = true)
    ]
)
data class CategoriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    val nombre: String,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long? = null,
    @ColumnInfo(name = "remote_updated_at") val remoteUpdatedAt: String? = null,
    @ColumnInfo(name = "remote_deleted_at") val remoteDeletedAt: String? = null
)
