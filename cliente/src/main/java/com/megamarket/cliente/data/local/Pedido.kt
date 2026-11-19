package com.megamarket.cliente.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.megamarket.modelo.EstadoSincronizacion

@Entity(
    tableName = "pedidos",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["clienteId"]),
        Index(value = ["clientUuid"], unique = true),
        Index(value = ["remote_id"], unique = true)
    ]
)
data class Pedido(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    /** UUID de idempotencia del pedido (generado una vez al crear). */
    val clientUuid: String,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long? = null,
    /** Momento de la confirmación en milisegundos desde epoch. */
    val fecha: Long,
    val totalCentimos: Long,
    /** Estado de negocio (p. ej. CONFIRMADO). */
    val estado: String,
    /** Estado de sincronización (PENDIENTE / ENVIANDO / SINCRONIZADO / ERROR). */
    val estadoSync: String = EstadoSincronizacion.PENDIENTE,
    /** 2 = legado sin franja; 3 = checkout con franja y modalidad. */
    @ColumnInfo(name = "contrato_version") val contratoVersion: Int = 2,
    @ColumnInfo(name = "franja_entrega_id") val franjaEntregaId: String? = null,
    @ColumnInfo(name = "modalidad_pago") val modalidadPago: String? = null,
    /** Instantánea legible de la franja elegida (no inventada en sync). */
    @ColumnInfo(name = "franja_fecha") val franjaFecha: String? = null,
    @ColumnInfo(name = "franja_hora_inicio") val franjaHoraInicio: String? = null,
    @ColumnInfo(name = "franja_hora_fin") val franjaHoraFin: String? = null
)
