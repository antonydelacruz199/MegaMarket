package com.megamarket.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = FILA_UNICA,
    val ultimaSincronizacionExitosa: Long? = null,
    val ultimoIntento: Long? = null,
    val ultimoErrorGeneral: String? = null
) {
    companion object {
        const val FILA_UNICA = 1
    }
}
