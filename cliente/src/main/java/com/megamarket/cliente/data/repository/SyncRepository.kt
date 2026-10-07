package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.dao.MovimientoInventarioDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import com.megamarket.cliente.data.local.entities.OperacionPendienteEntity
import com.megamarket.cliente.data.local.entities.SyncMetadataEntity
import com.megamarket.cliente.data.remote.RetrofitProvider
import com.megamarket.cliente.data.remote.api.CategoriaApi
import com.megamarket.cliente.data.remote.api.InventarioApi
import com.megamarket.cliente.data.remote.api.PedidoApi
import com.megamarket.cliente.data.remote.api.ProductoApi
import com.megamarket.cliente.data.remote.dto.CrearPedidoRequest
import com.megamarket.cliente.data.remote.dto.DireccionPedidoRequest
import com.megamarket.cliente.data.remote.dto.MovimientoInventarioRequest
import com.megamarket.cliente.data.remote.dto.PedidoDetalleRequest
import com.megamarket.cliente.data.session.SessionStore
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Cola + envío a API REST.
 * Orden: catálogo → pedidos → movimientos → pull final.
 */
class SyncRepository(
    private val operacionDao: OperacionPendienteDao,
    private val productoDao: ProductoDao,
    private val pedidoDao: PedidoDao,
    private val movimientoDao: MovimientoInventarioDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val baseUrlApi: String,
    private val catalogoRepository: CatalogoRepository? = null,
    private val sessionStore: SessionStore? = null,
    private val remote: RetrofitProvider? = null
) {
    private val pedidoApi: PedidoApi? by lazy { remote?.pedidoApi ?: RetrofitProvider.crear(baseUrlApi)?.pedidoApi }
    private val inventarioApi: InventarioApi? by lazy {
        remote?.inventarioApi ?: RetrofitProvider.crear(baseUrlApi)?.inventarioApi
    }
    private val categoriaApi: CategoriaApi? by lazy {
        remote?.categoriaApi ?: RetrofitProvider.crear(baseUrlApi)?.categoriaApi
    }
    private val productoApi: ProductoApi? by lazy {
        remote?.productoApi ?: RetrofitProvider.crear(baseUrlApi)?.productoApi
    }

    data class ResumenSync(
        val pendientes: Int,
        val errores: Int,
        val ultimaExitosa: Long?,
        val ultimoError: String?,
        val enviando: Boolean = false
    )

    fun observarResumen(): Flow<ResumenSync> = combine(
        operacionDao.observarPendientesActivos(),
        operacionDao.observarErrores(),
        syncMetadataDao.observar()
    ) { pendientes, errores, meta ->
        ResumenSync(
            pendientes = pendientes,
            errores = errores,
            ultimaExitosa = meta?.ultimaSincronizacionExitosa,
            ultimoError = meta?.ultimoErrorGeneral
        )
    }

    suspend fun sincronizarPendientes(): ResultadoSincronizacion = withContext(Dispatchers.IO) {
        val ahora = System.currentTimeMillis()
        operacionDao.recuperarEnviandoAtascadas(ahora)
        operacionDao.marcarLegacyStockComoError(
            "Operación legacy ACTUALIZAR_STOCK incompatible con movimientos de inventario.",
            ahora
        )
        registrarIntento(ahora, null)

        if (baseUrlApi.isBlank() || remote == null) {
            registrarIntento(ahora, "API REST no configurada (MEGAMARKET_API_BASE_URL).")
            return@withContext ResultadoSincronizacion.Reintentar(
                "Servidor no configurado. Las operaciones permanecen pendientes."
            )
        }

        if (sessionStore?.accessToken.isNullOrBlank()) {
            registrarIntento(ahora, "Sesión remota requerida (HTTP 401).")
            return@withContext ResultadoSincronizacion.Reintentar("Autenticación requerida.")
        }

        when (val pullInicial = pullCatalogo()) {
            is Envio.Reintentable -> {
                registrarIntento(ahora, pullInicial.mensaje)
                return@withContext ResultadoSincronizacion.Reintentar(pullInicial.mensaje)
            }
            is Envio.ErrorDefinitivo -> {
                if (pullInicial.mensaje.contains("401")) {
                    sessionStore?.marcarSesionInvalidaRemota()
                }
                registrarIntento(ahora, pullInicial.mensaje)
                return@withContext ResultadoSincronizacion.Reintentar(pullInicial.mensaje)
            }
            else -> Unit
        }

        val pendientes = operacionDao.obtenerParaEnviar()
            .filter { it.operacion != TipoOperacionPendiente.ACTUALIZAR_STOCK }

        var huboExito = pendientes.isEmpty()
        var debeReintentar = false
        var ultimoMensaje: String? = null

        val ordenadas = pendientes.sortedWith(
            compareBy(
                { if (it.operacion == TipoOperacionPendiente.CREAR_PEDIDO) 0 else 1 },
                { it.fechaCreacion }
            )
        )

        for (op in ordenadas) {
            operacionDao.marcarEnviando(op.id, System.currentTimeMillis())
            when (val r = enviarOperacion(op)) {
                is Envio.Exito -> {
                    operacionDao.marcarSincronizado(op.id, System.currentTimeMillis())
                    huboExito = true
                }
                is Envio.ErrorDefinitivo -> {
                    if (r.mensaje.contains("401")) {
                        sessionStore?.marcarSesionInvalidaRemota()
                    }
                    operacionDao.marcarError(op.id, r.mensaje, System.currentTimeMillis())
                    ultimoMensaje = r.mensaje
                }
                is Envio.Reintentable -> {
                    operacionDao.marcarError(op.id, r.mensaje, System.currentTimeMillis())
                    debeReintentar = true
                    ultimoMensaje = r.mensaje
                }
            }
        }

        pullCatalogo()

        if (huboExito) {
            syncMetadataDao.guardar(
                SyncMetadataEntity(
                    id = SyncMetadataEntity.FILA_UNICA,
                    ultimaSincronizacionExitosa = System.currentTimeMillis(),
                    ultimoIntento = System.currentTimeMillis(),
                    ultimoErrorGeneral = if (debeReintentar) ultimoMensaje else null
                )
            )
        } else {
            registrarIntento(System.currentTimeMillis(), ultimoMensaje)
        }

        when {
            debeReintentar -> ResultadoSincronizacion.Reintentar(ultimoMensaje)
            huboExito -> ResultadoSincronizacion.Exito
            else -> ResultadoSincronizacion.SinTrabajo
        }
    }

    private suspend fun pullCatalogo(): Envio {
        val catalogo = catalogoRepository ?: return Envio.Exito
        val catApi = categoriaApi ?: return Envio.Reintentable("CategoriaApi no disponible")
        val prodApi = productoApi ?: return Envio.Reintentable("ProductoApi no disponible")
        return try {
            val cats = catApi.obtenerCategorias()
            if (cats.code() == 401) {
                return Envio.ErrorDefinitivo("HTTP 401: sesión inválida / auth necesaria")
            }
            if (!cats.isSuccessful) {
                return clasificarHttp(cats.code(), cats.message())
            }
            val prods = prodApi.obtenerProductos()
            if (prods.code() == 401) {
                return Envio.ErrorDefinitivo("HTTP 401: sesión inválida / auth necesaria")
            }
            if (!prods.isSuccessful) {
                return clasificarHttp(prods.code(), prods.message())
            }
            catalogo.sincronizarRemotoSiDisponible(
                categoriasRemotas = cats.body().orEmpty(),
                productosRemotos = prods.body().orEmpty()
            )
            Envio.Exito
        } catch (e: SocketTimeoutException) {
            Envio.Reintentable("Timeout: ${e.message}")
        } catch (e: IOException) {
            Envio.Reintentable("Red: ${e.message}")
        } catch (e: Exception) {
            Envio.Reintentable(e.message ?: "Error pull catálogo")
        }
    }

    private suspend fun enviarOperacion(op: OperacionPendienteEntity): Envio {
        return try {
            when (op.operacion) {
                TipoOperacionPendiente.CREAR_PEDIDO -> enviarPedido(op)
                TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO -> enviarMovimiento(op)
                TipoOperacionPendiente.ACTUALIZAR_STOCK ->
                    Envio.ErrorDefinitivo(
                        "Operación legacy ACTUALIZAR_STOCK incompatible con movimientos de inventario."
                    )
                else -> Envio.ErrorDefinitivo("Operación no soportada: ${op.operacion}")
            }
        } catch (e: SocketTimeoutException) {
            Envio.Reintentable("Timeout: ${e.message}")
        } catch (e: IOException) {
            Envio.Reintentable("Red: ${e.message}")
        } catch (e: HttpException) {
            clasificarHttp(e.code(), e.message())
        } catch (e: Exception) {
            Envio.Reintentable(e.message ?: "Error desconocido")
        }
    }

    private suspend fun enviarPedido(op: OperacionPendienteEntity): Envio {
        val api = pedidoApi ?: return Envio.Reintentable("PedidoApi no disponible")
        val pedido = pedidoDao.obtenerPedidoPorId(op.entidadIdLocal)
            ?: return Envio.ErrorDefinitivo("Pedido local ${op.entidadIdLocal} no existe")
        val detalles = pedidoDao.obtenerDetallesPedido(pedido.id)
        val direccion = pedidoDao.obtenerDireccionPedido(pedido.id)
            ?: return Envio.ErrorDefinitivo("Dirección del pedido no existe")

        val detallesRemotos = mutableListOf<PedidoDetalleRequest>()
        for (d in detalles) {
            val remoteProducto = productoDao.obtenerPorId(d.productoId)?.remoteId
            if (remoteProducto.isNullOrBlank()) {
                return Envio.Reintentable(
                    "Falta remoteId del producto ${d.productoId} para sincronizar pedido."
                )
            }
            detallesRemotos += PedidoDetalleRequest(
                productoId = remoteProducto,
                nombreProducto = d.nombreProducto,
                precioUnitarioCentimos = d.precioUnitarioCentimos,
                cantidad = d.cantidad,
                subtotalCentimos = d.subtotalCentimos
            )
        }

        val body = CrearPedidoRequest(
            clientUuid = pedido.clientUuid,
            totalCentimos = pedido.totalCentimos,
            estado = pedido.estado,
            detalles = detallesRemotos,
            direccion = DireccionPedidoRequest(
                departamento = direccion.departamento,
                provincia = direccion.provincia,
                distrito = direccion.distrito,
                direccion = direccion.direccion,
                telefono = direccion.telefono
            )
        )
        val respuesta = api.crearPedido(body)
        if (respuesta.code() !in 200..299) {
            return clasificarHttp(respuesta.code(), respuesta.message())
        }
        respuesta.body()?.let { cuerpo ->
            pedidoDao.actualizarPedido(
                pedido.copy(
                    remoteId = cuerpo.id,
                    remoteVersion = cuerpo.version,
                    estadoSync = EstadoSincronizacion.SINCRONIZADO
                )
            )
        }
        return Envio.Exito
    }

    private suspend fun enviarMovimiento(op: OperacionPendienteEntity): Envio {
        val api = inventarioApi ?: return Envio.Reintentable("InventarioApi no disponible")
        val json = JSONObject(op.payload)
        val productoLocalId = json.getLong("productoIdLocal")
        val tipo = json.getString("tipo")
        val cantidad = json.getInt("cantidad")
        val pedidoUuid = if (json.has("pedidoUuid") && !json.isNull("pedidoUuid")) {
            json.getString("pedidoUuid")
        } else {
            null
        }
        val remoteProducto = productoDao.obtenerPorId(productoLocalId)?.remoteId
        if (remoteProducto.isNullOrBlank()) {
            return Envio.Reintentable(
                "Falta remoteId del producto $productoLocalId para movimiento."
            )
        }
        val body = MovimientoInventarioRequest(
            uuidOperacion = op.uuidOperacion,
            productoId = remoteProducto,
            tipo = tipo,
            cantidad = cantidad,
            pedidoUuid = pedidoUuid
        )
        val respuesta = api.crearMovimiento(body)
        if (respuesta.code() !in 200..299) {
            return clasificarHttp(respuesta.code(), respuesta.message())
        }
        val mov = movimientoDao.obtenerPorUuid(op.uuidOperacion)
        respuesta.body()?.let { cuerpo ->
            if (mov != null) {
                movimientoDao.actualizar(
                    mov.copy(
                        remoteId = cuerpo.id,
                        remoteVersion = cuerpo.version
                    )
                )
            }
        }
        return Envio.Exito
    }

    private fun clasificarHttp(code: Int, message: String?): Envio = when (code) {
        408, 429 -> Envio.Reintentable("HTTP $code: ${message.orEmpty()}")
        in 500..599 -> Envio.Reintentable("HTTP $code servidor: ${message.orEmpty()}")
        401 -> Envio.ErrorDefinitivo("HTTP 401: sesión inválida / auth necesaria")
        403 -> Envio.ErrorDefinitivo("HTTP 403: permiso denegado")
        404 -> Envio.ErrorDefinitivo("HTTP 404: recurso no encontrado")
        409 -> Envio.ErrorDefinitivo("HTTP 409 conflicto: ${message.orEmpty()}")
        in 400..499 -> Envio.ErrorDefinitivo("HTTP $code: ${message.orEmpty()}")
        else -> Envio.Reintentable("HTTP $code: ${message.orEmpty()}")
    }

    private suspend fun registrarIntento(ahora: Long, error: String?) {
        val actual = syncMetadataDao.obtener()
        syncMetadataDao.guardar(
            SyncMetadataEntity(
                id = SyncMetadataEntity.FILA_UNICA,
                ultimaSincronizacionExitosa = actual?.ultimaSincronizacionExitosa,
                ultimoIntento = ahora,
                ultimoErrorGeneral = error ?: actual?.ultimoErrorGeneral
            )
        )
    }

    private sealed class Envio {
        data object Exito : Envio()
        data class Reintentable(val mensaje: String) : Envio()
        data class ErrorDefinitivo(val mensaje: String) : Envio()
    }
}

sealed class ResultadoSincronizacion {
    data object Exito : ResultadoSincronizacion()
    data object SinTrabajo : ResultadoSincronizacion()
    data class Reintentar(val motivo: String?) : ResultadoSincronizacion()
}
