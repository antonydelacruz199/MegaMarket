package com.megamarket.app.data.repository

import com.megamarket.app.data.local.SyncMetadata
import com.megamarket.app.data.local.dao.CategoriaDao
import com.megamarket.app.data.local.dao.OperacionPendienteDao
import com.megamarket.app.data.local.dao.ProductoDao
import com.megamarket.app.data.local.dao.SyncMetadataDao
import com.megamarket.app.data.mapper.aEntidadLocal
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.CategoriaApiDto
import com.megamarket.app.data.remote.ProductoApiDto
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class CatalogoRemotoRepository(
    private val api: ApiService,
    private val categoriaDao: CategoriaDao,
    private val productoDao: ProductoDao,
    private val operacionDao: OperacionPendienteDao,
    private val metadatosSincronizacionDao: SyncMetadataDao
) {

    suspend fun descargarYGuardarCatalogo(): ResultadoDescargaCatalogo = withContext(Dispatchers.IO) {
        try {
            val catsResp = api.listarCategorias()
            if (!catsResp.isSuccessful) {
                return@withContext ResultadoDescargaCatalogo.Error(
                    "No se pudieron descargar las categorías (${catsResp.code()})"
                )
            }
            val categorias = catsResp.body()?.categorias.orEmpty()
            upsertCategorias(categorias)

            val prodResp = api.listarProductos()
            if (!prodResp.isSuccessful) {
                return@withContext ResultadoDescargaCatalogo.Error(
                    "No se pudieron descargar los productos (${prodResp.code()})"
                )
            }
            val productos = prodResp.body()?.productos.orEmpty()
            upsertProductos(productos)

            val ahora = System.currentTimeMillis()
            val actual = metadatosSincronizacionDao.obtener()
            metadatosSincronizacionDao.guardar(
                SyncMetadata(
                    id = SyncMetadata.FILA_UNICA,
                    ultimaSincronizacionExitosa = ahora,
                    ultimoIntento = ahora,
                    ultimoErrorGeneral = null
                )
            )

            if (categoriaDao.contar() == 0 || productoDao.obtenerActivos().isEmpty()) {
                return@withContext ResultadoDescargaCatalogo.Error(
                    "La configuración inicial falló: el catálogo remoto está vacío o no se pudo guardar."
                )
            }
            ResultadoDescargaCatalogo.Exito(
                categorias = categorias.size,
                productos = productos.size
            )
        } catch (error: IOException) {
            ResultadoDescargaCatalogo.Error(
                "No se pudo contactar al servidor para la descarga inicial."
            )
        } catch (error: Exception) {
            ResultadoDescargaCatalogo.Error(
                error.message ?: "Error en la descarga inicial del catálogo."
            )
        }
    }

    private suspend fun upsertCategorias(remotas: List<CategoriaApiDto>) {
        for (remota in remotas) {
            val existente = categoriaDao.obtenerPorRemoteId(remota.id)
                ?: categoriaDao.obtenerPorNombre(remota.nombre.trim())
            if (existente != null) {
                categoriaDao.actualizarCategoria(
                    remota.aEntidadLocal(idLocal = existente.id).copy(
                        remoteDeletedAt = existente.remoteDeletedAt
                    )
                )
            } else {
                categoriaDao.insertar(remota.aEntidadLocal())
            }
        }
    }

    private suspend fun upsertProductos(remotas: List<ProductoApiDto>) {
        val idsRemotos = remotas.map { it.id }.toSet()
        for (remota in remotas) {
            val categoriaLocal = categoriaDao.obtenerPorRemoteId(remota.categoriaId)
                ?: continue
            val existente = productoDao.obtenerPorRemoteId(remota.id)
            if (existente != null) {
                val pendientesProducto = operacionDao.contarActivasDeEntidad(
                    TipoOperacionPendiente.PRODUCTO,
                    existente.id
                )
                val pendientesMovimiento = operacionDao.contarActivasDeEntidad(
                    TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                    existente.id
                )
                if (pendientesProducto > 0 || pendientesMovimiento > 0) continue
                productoDao.actualizarProducto(
                    remota.aEntidadLocal(idLocal = existente.id, categoriaIdLocal = categoriaLocal.id)
                )
            } else {
                productoDao.insertar(
                    remota.aEntidadLocal(idLocal = 0, categoriaIdLocal = categoriaLocal.id)
                )
            }
        }

        val localesConRemoto = productoDao.obtenerConRemoteId()
        val ahoraIso = System.currentTimeMillis().toString()
        for (local in localesConRemoto) {
            val remoteId = local.remoteId ?: continue
            if (remoteId in idsRemotos) continue
            val pendientes = operacionDao.contarActivasDeEntidad(
                TipoOperacionPendiente.PRODUCTO,
                local.id
            )
            if (pendientes > 0) continue
            if (!local.activo && local.remoteDeletedAt != null) continue
            productoDao.actualizarProducto(
                local.copy(
                    activo = false,
                    remoteDeletedAt = local.remoteDeletedAt ?: ahoraIso
                )
            )
        }
    }

    suspend fun hayCatalogoLocal(): Boolean = withContext(Dispatchers.IO) {
        categoriaDao.contar() > 0 && productoDao.obtenerActivos().isNotEmpty()
    }
}

sealed class ResultadoDescargaCatalogo {
    data class Exito(val categorias: Int, val productos: Int) : ResultadoDescargaCatalogo()
    data class Error(val mensaje: String) : ResultadoDescargaCatalogo()
}
