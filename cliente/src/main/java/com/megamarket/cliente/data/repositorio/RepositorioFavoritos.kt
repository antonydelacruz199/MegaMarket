package com.megamarket.cliente.data.repositorio

import com.megamarket.cliente.data.local.EntidadFavorito
import com.megamarket.cliente.data.local.FavoritoDao
import com.megamarket.modelo.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RepositorioFavoritos(
    private val dao: FavoritoDao,
    private val catalogo: RepositorioCatalogo
) {
    fun observar(): Flow<List<Producto>> = dao.observar().map { favoritos ->
        val productos = catalogo.leerActivos().associateBy { it.id }
        favoritos.mapNotNull { favorito ->
            productos[favorito.productoId]?.takeIf { it.activo }
        }
    }.flowOn(Dispatchers.IO)

    fun observarEsFavorito(productoId: Long): Flow<Boolean> =
        dao.observarExiste(productoId).flowOn(Dispatchers.IO)

    suspend fun alternar(productoId: Long) = withContext(Dispatchers.IO) {
        if (dao.existe(productoId)) {
            dao.eliminar(productoId)
        } else {
            dao.insertar(EntidadFavorito(productoId = productoId))
        }
    }
}
