package com.megamarket.cliente.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val id: Int = FILA_UNICA,
    val ultimaSincronizacionExitosa: Long? = null,
    val ultimoIntento: Long? = null,
    val ultimoErrorGeneral: String? = null
) {
    companion object {
        const val FILA_UNICA = 1
    }
}
