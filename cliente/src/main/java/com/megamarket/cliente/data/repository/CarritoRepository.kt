package com.megamarket.cliente.data.repository

import androidx.room.withTransaction
import com.megamarket.cliente.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CarritoRepository(
    private val database: AppDatabase,
    private val catalogo: CatalogoRepository,
    private val sesion: SesionLocalStore,
    private val programar: () -> Unit
) {
    private val dao = database.carritoDao()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observarLineasCarrito(): Flow<List<com.megamarket.cliente.model.LineaCarrito>> = sesion.owner.flatMapLatest { owner ->
        combine(dao.observarItems(owner), catalogo.observarTodos()) { items, productos ->
            items.mapNotNull { item -> productos.find { it.id == item.productoId }?.let {
                com.megamarket.cliente.model.LineaCarrito(it.copy(stock=it.stock+item.cantidad),item.cantidad)
            } }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun agregarAlCarrito(productoId: Long) = cambiar(productoId, null)
    suspend fun actualizarCantidadCarrito(productoId: Long, cantidad: Int) = cambiar(productoId,cantidad.coerceAtLeast(0))
    suspend fun quitarDelCarrito(productoId: Long) = cambiar(productoId,0)

    private suspend fun cambiar(productoId: Long, solicitada: Int?) = withContext(Dispatchers.IO) {
        val owner=sesion.owner.value
        if(owner<=0) return@withContext
        database.withTransaction {
            if(sesion.owner.value!=owner) return@withTransaction
            val p=database.productoDao().obtenerPorId(productoId) ?: return@withTransaction
            val old=dao.obtenerPorProducto(productoId,owner)?.cantidad ?: 0
            val nueva=solicitada ?: (old+1)
            if(nueva>old && (!p.activo || nueva-old>p.stock)) return@withTransaction
            if(database.productoDao().ajustarDisponible(productoId,old-nueva)==0) return@withTransaction
            val cart=sesion.carritoId()
            if(nueva==0) dao.quitarProducto(productoId,owner)
            else if(old==0) dao.insertar(CarritoItem(productoId=productoId,cantidad=nueva,ownerId=owner,cartUuid=cart))
            else dao.actualizarCantidad(productoId,nueva,owner)
            val items=JSONArray()
            for(line in dao.obtenerTodos(owner)) {
                val remote=database.productoDao().obtenerPorId(line.productoId)?.remoteId ?: continue
                items.put(JSONObject().put("productoId",remote).put("cantidad",line.cantidad))
            }
            val now=System.currentTimeMillis()
            database.operacionPendienteDao().insertar(OperacionPendiente(
                uuidOperacion=UUID.randomUUID().toString(),tipoEntidad="CARRITO",entidadIdLocal=owner,
                operacion="RESERVAR_CARRITO",ownerId=owner,
                payload=JSONObject().put("cartId",cart).put("revision",sesion.siguienteRevision(cart)).put("items",items).toString(),
                fechaCreacion=now
            ))
        }
        programar()
    }
}
