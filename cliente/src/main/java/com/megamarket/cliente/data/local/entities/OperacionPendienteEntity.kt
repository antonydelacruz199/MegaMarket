package com.megamarket.cliente.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoOperacionPendiente

/**
 * Cola persistente de escrituras autorizadas (RF12–RF15).
 * [uuidOperacion] se genera una sola vez y se reutiliza en retries.
 * No se elimina al sincronizar: pasa a SINCRONIZADO.
 */
@Entity(
    tableName = "operaciones_pendientes",
    indices = [
        Index(value = ["uuidOperacion"], unique = true),
        Index(value = ["estado"]),
        Index(value = ["tipoEntidad", "entidadIdLocal", "operacion"])
    ]
)
data class OperacionPendienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuidOperacion: String,
    val tipoEntidad: String,
    val entidadIdLocal: Long,
    val operacion: String,
    val payload: String,
    val estado: String = EstadoSincronizacion.PENDIENTE,
    val intentos: Int = 0,
    val ultimoError: String? = null,
    val fechaCreacion: Long,
    val fechaActualizacion: Long = fechaCreacion,
    val sincronizadoEn: Long? = null
) {
    companion object {
        const val TIPO_PRODUCTO = TipoOperacionPendiente.PRODUCTO
        const val TIPO_PEDIDO = TipoOperacionPendiente.PEDIDO
        const val TIPO_MOVIMIENTO = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO
        const val OPERACION_CREAR_PEDIDO = TipoOperacionPendiente.CREAR_PEDIDO
        const val OPERACION_CREAR_MOVIMIENTO = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO
        @Deprecated("Legacy stock absoluto; marcar ERROR, no enviar")
        const val OPERACION_ACTUALIZAR_STOCK = TipoOperacionPendiente.ACTUALIZAR_STOCK
        const val ESTADO_PENDIENTE = EstadoSincronizacion.PENDIENTE
        const val ESTADO_ENVIANDO = EstadoSincronizacion.ENVIANDO
        const val ESTADO_SINCRONIZADO = EstadoSincronizacion.SINCRONIZADO
        const val ESTADO_ERROR = EstadoSincronizacion.ERROR
        @Deprecated("Usar ESTADO_ENVIANDO")
        const val ESTADO_SINCRONIZANDO = "SINCRONIZANDO"
    }
}
