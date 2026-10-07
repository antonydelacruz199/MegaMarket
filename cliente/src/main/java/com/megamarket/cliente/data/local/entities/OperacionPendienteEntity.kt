package com.megamarket.cliente.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cola local de sincronización hacia la API REST (nunca hacia Neon/PostgreSQL directo).
 *
 * Decisión UUID: Neon usará UUID remotos; Android sigue con Long local ([entidadIdLocal]).
 * No se inventan UUID. Cuando exista el mapeo remoteId, el Worker lo usará en el PATCH.
 * Mientras tanto la operación permanece PENDIENTE y WorkManager reintenta.
 */
@Entity(
    tableName = "operaciones_pendientes",
    indices = [Index(value = ["estado"]), Index(value = ["tipoEntidad", "entidadIdLocal", "operacion"])]
)
data class OperacionPendienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipoEntidad: String,
    val entidadIdLocal: Long,
    val operacion: String,
    /** JSON con el valor final a sincronizar (p. ej. stock absoluto, no un delta). */
    val payload: String,
    val fechaCreacion: Long,
    val intentos: Int = 0,
    val ultimoError: String? = null,
    val estado: String = ESTADO_PENDIENTE
) {
    companion object {
        const val TIPO_PRODUCTO = "PRODUCTO"
        const val OPERACION_ACTUALIZAR_STOCK = "ACTUALIZAR_STOCK"
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_SINCRONIZANDO = "SINCRONIZANDO"
        const val ESTADO_ERROR = "ERROR"
    }
}
