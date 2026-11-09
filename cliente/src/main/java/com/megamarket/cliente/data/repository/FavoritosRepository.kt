package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.FavoritoDao
import com.megamarket.cliente.data.local.Favorito
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FavoritosRepository(
    private val favoritoDao: FavoritoDao,
    private val catalogo: CatalogoRepository,
    private val sesion: com.megamarket.cliente.data.local.SesionLocalStore
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observarProductosFavoritos(): Flow<List<Producto>> = sesion.owner.flatMapLatest { owner -> combine(
        favoritoDao.observarFavoritos(owner),
        catalogo.observarProductos()
    ) { favoritos, productos ->
        val mapa = productos.associateBy { it.id }
        favoritos.mapNotNull { favorito ->
            mapa[favorito.productoId]?.takeIf { it.activo }
        }
    }.flowOn(Dispatchers.IO) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observarEsFavorito(productoId: Long): Flow<Boolean> =
        sesion.owner.flatMapLatest { favoritoDao.observarExiste(productoId,it) }.flowOn(Dispatchers.IO)

    suspend fun alternarFavorito(productoId: Long) = withContext(Dispatchers.IO) {
        if (favoritoDao.existe(productoId,sesion.owner.value)) {
            favoritoDao.quitarFavorito(productoId,sesion.owner.value)
        } else {
            favoritoDao.insertar(Favorito(productoId = productoId,ownerId=sesion.owner.value))
        }
    }
}
