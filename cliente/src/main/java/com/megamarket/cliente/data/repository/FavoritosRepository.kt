package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.FavoritoDao
import com.megamarket.cliente.data.local.entities.FavoritoEntity
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FavoritosRepository(
    private val dao: FavoritoDao,
    private val catalogo: CatalogoRepository
) {
    fun observar(): Flow<List<Producto>> = combine(
        dao.observar(),
        catalogo.observarProductos()
    ) { favoritos, productos ->
        val mapa = productos.associateBy { it.id }
        favoritos.mapNotNull { favorito ->
            mapa[favorito.productoId]?.takeIf { it.activo }
        }
    }.flowOn(Dispatchers.IO)

    fun observarEsFavorito(productoId: Long): Flow<Boolean> =
        dao.observarExiste(productoId).flowOn(Dispatchers.IO)

    suspend fun alternar(productoId: Long) = withContext(Dispatchers.IO) {
        if (dao.existe(productoId)) {
            dao.eliminar(productoId)
        } else {
            dao.insertar(FavoritoEntity(productoId = productoId))
        }
    }
}
