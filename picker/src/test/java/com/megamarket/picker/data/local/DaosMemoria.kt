package com.megamarket.picker.data.local

import com.megamarket.picker.data.local.dao.LineaPickingDao
import com.megamarket.picker.data.local.dao.OperacionPickingDao
import com.megamarket.picker.data.local.dao.PedidoPickingDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class PedidoDaoMemoria : PedidoPickingDao {
    private val datos = mutableMapOf<String, PedidoPickingEntity>()

    override suspend fun guardar(pedido: PedidoPickingEntity) {
        datos[pedido.id + ":" + pedido.ownerId] = pedido
    }

    override suspend fun obtener(id: String, ownerId: String): PedidoPickingEntity? =
        datos["$id:$ownerId"]

    override suspend fun eliminar(id: String, ownerId: String) {
        datos.remove("$id:$ownerId")
    }
}

class LineaDaoMemoria : LineaPickingDao {
    private val datos = mutableListOf<LineaPickingEntity>()
    private val flujo = MutableStateFlow(datos.toList())

    private fun publicar() {
        flujo.value = datos.toList()
    }

    override suspend fun guardarTodas(lineas: List<LineaPickingEntity>) {
        for (linea in lineas) {
            datos.removeAll { it.id == linea.id && it.ownerId == linea.ownerId }
            datos += linea
        }
        publicar()
    }

    override suspend fun dePedido(pedidoId: String, ownerId: String): List<LineaPickingEntity> =
        datos.filter { it.pedidoId == pedidoId && it.ownerId == ownerId }

    override fun observarDePedido(pedidoId: String, ownerId: String): Flow<List<LineaPickingEntity>> =
        flujo.map { lista -> lista.filter { it.pedidoId == pedidoId && it.ownerId == ownerId } }

    override suspend fun obtener(id: String, ownerId: String): LineaPickingEntity? =
        datos.firstOrNull { it.id == id && it.ownerId == ownerId }

    override suspend fun actualizarConfirmada(
        id: String,
        ownerId: String,
        armada: Int,
        estado: String,
        ahora: Long
    ): Int {
        val idx = datos.indexOfFirst { it.id == id && it.ownerId == ownerId }
        if (idx < 0) return 0
        datos[idx] = datos[idx].copy(
            cantidadArmada = armada,
            estadoPicking = estado,
            actualizadoEn = ahora
        )
        publicar()
        return 1
    }

    override suspend fun eliminarDePedido(pedidoId: String, ownerId: String) {
        datos.removeAll { it.pedidoId == pedidoId && it.ownerId == ownerId }
        publicar()
    }
}

class OperacionDaoMemoria : OperacionPickingDao {
    private val datos = mutableListOf<OperacionPickingEntity>()
    private val flujo = MutableStateFlow(datos.toList())
    private var seq = 1L

    private fun publicar() {
        flujo.value = datos.toList()
    }

    fun todas(): List<OperacionPickingEntity> = datos.toList()

    override suspend fun insertar(operacion: OperacionPickingEntity): Long {
        if (datos.any { it.uuidOperacion == operacion.uuidOperacion }) {
            throw IllegalStateException("uuid duplicado")
        }
        val id = if (operacion.id == 0L) seq++ else operacion.id
        datos += operacion.copy(id = id)
        publicar()
        return id
    }

    override suspend fun actualizar(operacion: OperacionPickingEntity): Int {
        val idx = datos.indexOfFirst { it.id == operacion.id }
        if (idx < 0) return 0
        datos[idx] = operacion
        publicar()
        return 1
    }

    override suspend fun pendientesDelUsuario(ownerId: String): List<OperacionPickingEntity> =
        datos.filter { it.ownerId == ownerId && it.estado == OperacionPicking.PENDIENTE }
            .sortedWith(compareBy({ it.fechaCreacion }, { it.id }))

    override suspend fun porUuid(uuid: String): OperacionPickingEntity? =
        datos.firstOrNull { it.uuidOperacion == uuid }

    override suspend fun activasDeDetalle(detalleId: String, ownerId: String): List<OperacionPickingEntity> =
        datos.filter {
            it.detalleId == detalleId && it.ownerId == ownerId && it.estado in OperacionPicking.ACTIVAS
        }

    override suspend fun deDetalle(detalleId: String, ownerId: String): List<OperacionPickingEntity> =
        datos.filter { it.detalleId == detalleId && it.ownerId == ownerId }
            .sortedByDescending { it.fechaCreacion }

    override fun observarDeDetalle(detalleId: String, ownerId: String): Flow<List<OperacionPickingEntity>> =
        flujo.map { lista ->
            lista.filter { it.detalleId == detalleId && it.ownerId == ownerId }
                .sortedByDescending { it.fechaCreacion }
        }

    override fun observarDePedido(pedidoId: String, ownerId: String): Flow<List<OperacionPickingEntity>> =
        flujo.map { lista ->
            lista.filter { it.pedidoId == pedidoId && it.ownerId == ownerId }
                .sortedBy { it.fechaCreacion }
        }

    override suspend fun cantidadPendienteEscaneos(detalleId: String, ownerId: String): Int =
        datos.filter {
            it.detalleId == detalleId &&
                it.ownerId == ownerId &&
                it.operacion == OperacionPicking.ESCANEAR &&
                it.estado in OperacionPicking.EN_VUELO
        }.sumOf { it.cantidad }

    override suspend fun faltantesActivos(detalleId: String, ownerId: String): Int =
        datos.count {
            it.detalleId == detalleId &&
                it.ownerId == ownerId &&
                it.operacion == OperacionPicking.MARCAR_FALTANTE &&
                it.estado in OperacionPicking.EN_VUELO
        }

    override suspend fun recuperarEnviandoAtascadas(ahora: Long): Int {
        var n = 0
        for (i in datos.indices) {
            if (datos[i].estado == OperacionPicking.ENVIANDO) {
                datos[i] = datos[i].copy(estado = OperacionPicking.PENDIENTE, fechaActualizacion = ahora)
                n++
            }
        }
        if (n > 0) publicar()
        return n
    }

    override suspend fun marcarEnviando(id: Long, ownerId: String, ahora: Long): Int {
        val idx = datos.indexOfFirst { it.id == id && it.ownerId == ownerId }
        if (idx < 0) return 0
        if (datos[idx].estado !in setOf(OperacionPicking.PENDIENTE, OperacionPicking.ERROR)) return 0
        datos[idx] = datos[idx].copy(estado = OperacionPicking.ENVIANDO, fechaActualizacion = ahora)
        publicar()
        return 1
    }

    override suspend fun marcarSincronizada(
        id: Long,
        ownerId: String,
        resultado: String?,
        ahora: Long
    ): Int {
        val idx = datos.indexOfFirst { it.id == id && it.ownerId == ownerId }
        if (idx < 0) return 0
        datos[idx] = datos[idx].copy(
            estado = OperacionPicking.SINCRONIZADO,
            resultado = resultado,
            sincronizadoEn = ahora,
            fechaActualizacion = ahora,
            ultimoError = null
        )
        publicar()
        return 1
    }

    override suspend fun marcarError(
        id: Long,
        ownerId: String,
        error: String,
        ahora: Long,
        codigo: String?
    ): Int {
        val idx = datos.indexOfFirst { it.id == id && it.ownerId == ownerId }
        if (idx < 0) return 0
        datos[idx] = datos[idx].copy(
            estado = OperacionPicking.ERROR,
            resultado = codigo,
            ultimoError = error,
            intentos = datos[idx].intentos + 1,
            fechaActualizacion = ahora
        )
        publicar()
        return 1
    }

    override suspend fun devolverAPendiente(id: Long, ownerId: String, error: String?, ahora: Long): Int {
        val idx = datos.indexOfFirst { it.id == id && it.ownerId == ownerId }
        if (idx < 0) return 0
        datos[idx] = datos[idx].copy(
            estado = OperacionPicking.PENDIENTE,
            ultimoError = error,
            fechaActualizacion = ahora
        )
        publicar()
        return 1
    }
}
