package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.FavoritoDao
import com.megamarket.cliente.data.local.Favorito
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FavoritosRepository(
    private val favoritoDao: FavoritoDao,
    private val catalogo: CatalogoRepository
) {
    fun observarProductosFavoritos(): Flow<List<Producto>> = combine(
        favoritoDao.observarFavoritos(),
        catalogo.observarProductos()
    ) { favoritos, productos ->
        val mapa = productos.associateBy { it.id }
        favoritos.mapNotNull { favorito ->
            mapa[favorito.productoId]?.takeIf { it.activo }
        }
    }.flowOn(Dispatchers.IO)

    fun observarEsFavorito(productoId: Long): Flow<Boolean> =
        favoritoDao.observarExiste(productoId).flowOn(Dispatchers.IO)

    suspend fun alternarFavorito(productoId: Long) = withContext(Dispatchers.IO) {
        if (favoritoDao.existe(productoId)) {
            favoritoDao.quitarFavorito(productoId)
        } else {
            favoritoDao.insertar(Favorito(productoId = productoId))
        }
    }
}
