package com.megamarket.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.megamarket.app.data.local.SyncMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun observarMetadatos(): Flow<SyncMetadata?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun obtener(): SyncMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(entidad: SyncMetadata)
}
