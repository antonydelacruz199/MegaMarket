package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.SyncMetadata
import com.megamarket.cliente.data.local.dao.CategoriaDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import com.megamarket.cliente.data.mapper.aEntidadLocal
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.CategoriaApiDto
import com.megamarket.cliente.data.remote.ProductoApiDto
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
            upsertCategorias(catsResp.body()?.categorias.orEmpty())

            val prodResp = api.listarProductos()
            if (!prodResp.isSuccessful) {
                return@withContext ResultadoDescargaCatalogo.Error(
                    "No se pudieron descargar los productos (${prodResp.code()})"
                )
            }
            val productos = prodResp.body()?.productos.orEmpty()
            upsertProductos(productos)

            val ahora = System.currentTimeMillis()
            metadatosSincronizacionDao.guardar(
                SyncMetadata(
                    id = SyncMetadata.FILA_UNICA,
                    ultimaSincronizacionExitosa = ahora,
                    ultimoIntento = ahora,
                    ultimoErrorGeneral = null
                )
            )

            if (categoriaDao.contar() == 0 || productoDao.contar() == 0) {
                return@withContext ResultadoDescargaCatalogo.Error(
                    "La configuración inicial falló: el catálogo remoto está vacío o no se pudo guardar."
                )
            }
            ResultadoDescargaCatalogo.Exito(
                categorias = catsResp.body()?.categorias?.size ?: 0,
                productos = productos.size
            )
        } catch (_: IOException) {
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
                ?: categoriaDao.obtenerActivas().firstOrNull {
                    it.nombre.equals(remota.nombre.trim(), ignoreCase = true)
                }
            if (existente != null) {
                categoriaDao.actualizarCategoria(remota.aEntidadLocal(idLocal = existente.id))
            } else {
                categoriaDao.insertar(remota.aEntidadLocal())
            }
        }
    }

    private suspend fun upsertProductos(remotas: List<ProductoApiDto>) {
        val idsRemotos = remotas.map { it.id }.toSet()
        for (remota in remotas) {
            val categoriaLocal = categoriaDao.obtenerPorRemoteId(remota.categoriaId) ?: continue
            val existente = productoDao.obtenerPorRemoteId(remota.id)
            if (existente != null) {
                val pendientesPedido = operacionDao.contarActivasDeEntidad(
                    TipoOperacionPendiente.PEDIDO,
                    existente.id
                )
                // No hay ops de producto en cliente; proteger stock local con movimientos pendientes.
                val pendientesMov = operacionDao.contarActivasDeEntidad(
                    TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                    existente.id
                )
                if (pendientesPedido > 0 || pendientesMov > 0) continue
                productoDao.actualizarProducto(
                    remota.aEntidadLocal(idLocal = existente.id, categoriaIdLocal = categoriaLocal.id)
                        .copy(providerId = existente.providerId)
                )
            } else {
                productoDao.insertar(
                    remota.aEntidadLocal(idLocal = 0, categoriaIdLocal = categoriaLocal.id)
                )
            }
        }

        for (local in productoDao.obtenerConRemoteId()) {
            val remoteId = local.remoteId ?: continue
            if (remoteId in idsRemotos) continue
            val pendientesMov = operacionDao.contarActivasDeEntidad(
                TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
                local.id
            )
            if (pendientesMov > 0) continue
            if (!local.activo && local.remoteDeletedAt != null) continue
            productoDao.actualizarProducto(
                local.copy(
                    activo = false,
                    remoteDeletedAt = local.remoteDeletedAt ?: System.currentTimeMillis().toString()
                )
            )
        }
    }

    suspend fun hayCatalogoLocal(): Boolean = withContext(Dispatchers.IO) {
        categoriaDao.contar() > 0 && productoDao.contar() > 0
    }
}

sealed class ResultadoDescargaCatalogo {
    data class Exito(val categorias: Int, val productos: Int) : ResultadoDescargaCatalogo()
    data class Error(val mensaje: String) : ResultadoDescargaCatalogo()
}
