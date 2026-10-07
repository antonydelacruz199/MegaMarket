package com.megamarket.cliente.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clientes",
    indices = [Index(value = ["usuario"], unique = true)]
)
data class ClienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val usuario: String,
    val correo: String,
    val claveHash: String
)
