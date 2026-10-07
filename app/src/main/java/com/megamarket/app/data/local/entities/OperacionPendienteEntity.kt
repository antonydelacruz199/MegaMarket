package com.megamarket.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoOperacionPendiente

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
        const val TIPO_MOVIMIENTO = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO
        const val OPERACION_CREAR_PRODUCTO = TipoOperacionPendiente.CREAR_PRODUCTO
        const val OPERACION_ACTUALIZAR_PRODUCTO = TipoOperacionPendiente.ACTUALIZAR_PRODUCTO
        const val OPERACION_ELIMINAR_PRODUCTO = TipoOperacionPendiente.ELIMINAR_PRODUCTO
        const val OPERACION_CREAR_MOVIMIENTO = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO
        const val ESTADO_PENDIENTE = EstadoSincronizacion.PENDIENTE
        const val ESTADO_ENVIANDO = EstadoSincronizacion.ENVIANDO
        const val ESTADO_SINCRONIZADO = EstadoSincronizacion.SINCRONIZADO
        const val ESTADO_ERROR = EstadoSincronizacion.ERROR
    }
}
