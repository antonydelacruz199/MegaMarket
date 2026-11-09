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
import androidx.room.withTransaction

class CatalogoRemotoRepository(
    private val api: ApiService,
    private val categoriaDao: CategoriaDao,
    private val productoDao: ProductoDao,
    private val operacionDao: OperacionPendienteDao,
    private val metadatosSincronizacionDao: SyncMetadataDao,
    private val database: com.megamarket.cliente.data.local.AppDatabase? = null,
    private val sesion: com.megamarket.cliente.data.local.SesionSincronizacion? = null,
    private val bootCount: () -> Int = { -1 }
) {

    suspend fun descargarYGuardarCatalogo(): ResultadoDescargaCatalogo = withContext(Dispatchers.IO) {
        val ownerAlIniciar=sesion?.owner?.value
        val remoteOwner=sesion?.usuarioRemotoId
        try {
            val catsResp = api.listarCategorias(remoteOwner)
            if (!catsResp.isSuccessful) {
                return@withContext ResultadoDescargaCatalogo.Error(
                    "No se pudieron descargar las categorías (${catsResp.code()})"
                )
            }
            val categorias = catsResp.body()?.categorias ?: return@withContext ResultadoDescargaCatalogo.Error("Respuesta de categorías inválida")

            val prodResp = api.listarProductos(remoteOwner)
            if (!prodResp.isSuccessful) {
                return@withContext ResultadoDescargaCatalogo.Error(
                    "No se pudieron descargar los productos (${prodResp.code()})"
                )
            }
            val productos = prodResp.body()?.productos ?: return@withContext ResultadoDescargaCatalogo.Error("Respuesta de catálogo inválida")
            suspend fun guardarCatalogo() {
                if(sesion?.owner?.value!=ownerAlIniciar) throw IOException("La cuenta activa cambió")
                upsertCategorias(categorias)
                upsertProductos(productos)
                if(categoriaDao.contar()==0) throw IOException("El catálogo no contiene categorías")
                val ahora=System.currentTimeMillis()
                metadatosSincronizacionDao.guardar(SyncMetadata(
                    ultimaSincronizacionExitosa=ahora,ultimoIntento=ahora,
                    sincronizadoElapsed=android.os.SystemClock.elapsedRealtime(),bootCount=bootCount()
                ))
            }
            if(database!=null) database.withTransaction { guardarCatalogo() } else guardarCatalogo()
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
        val reservas=database?.carritoDao()?.todasLasReservas().orEmpty().filter { it.ownerId==sesion?.owner?.value }.map { Triple(it.productoId,it.cartUuid,it.cantidad) }.toMutableList()
        for(pedido in database?.pedidoDao()?.reservasPendientes().orEmpty().filter { it.clienteId==sesion?.owner?.value }) {
            for(d in database!!.pedidoDao().obtenerDetallesPedido(pedido.id)) reservas.add(Triple(d.productoId,pedido.clientUuid,d.cantidad))
        }
        for (remota in remotas) {
            val categoriaLocal = categoriaDao.obtenerPorRemoteId(remota.categoriaId) ?: continue
            val existente = productoDao.obtenerPorRemoteId(remota.id)
            if (existente != null) {
                val propias=reservas.filter { it.first==existente.id }
                val ids=propias.map { it.second }.toSet()
                val reconocidas=remota.reservas.orEmpty().filter { it.carritoId in ids }.sumOf { it.cantidad }
                val disponible=(remota.stock+reconocidas-propias.sumOf { it.third }).coerceAtLeast(0)
                productoDao.actualizarProducto(
                    remota.aEntidadLocal(idLocal = existente.id, categoriaIdLocal = categoriaLocal.id)
                        .copy(providerId = existente.providerId,stock=disponible)
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
