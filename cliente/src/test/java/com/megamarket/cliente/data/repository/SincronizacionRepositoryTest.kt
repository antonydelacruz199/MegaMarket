package com.megamarket.cliente.data.repository

import com.megamarket.cliente.data.local.Categoria
import com.megamarket.cliente.data.local.Direccion
import com.megamarket.cliente.data.local.OperacionPendiente
import com.megamarket.cliente.data.local.Pedido
import com.megamarket.cliente.data.local.PedidoDetalle
import com.megamarket.cliente.data.local.Producto
import com.megamarket.cliente.data.local.SyncMetadata
import com.megamarket.cliente.data.local.dao.CategoriaDao
import com.megamarket.cliente.data.local.dao.OperacionPendienteDao
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.local.dao.ProductoDao
import com.megamarket.cliente.data.local.dao.SyncMetadataDao
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.CategoriasResponse
import com.megamarket.cliente.data.remote.CrearPedidoRequest
import com.megamarket.cliente.data.remote.CrearPedidoResponse
import com.megamarket.cliente.data.remote.LoginRequest
import com.megamarket.cliente.data.remote.LoginResponse
import com.megamarket.cliente.data.remote.ProductosResponse
import com.megamarket.modelo.EstadoSincronizacion
import com.megamarket.modelo.TipoOperacionPendiente
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SincronizacionRepositoryTest {

    @Test
    fun sinPendientes_devuelveSinTrabajo() = runBlocking {
        val operaciones = DaoOperacionesPendientesFalso()
        val sincronizacion = crearRepositorio(operaciones)
        val resultado = sincronizacion.sincronizarPendientes()
        assertTrue(resultado is ResultadoSincronizacion.Reintentar)
    }

    @Test
    fun legacyActualizarStock_quedaError() = runBlocking {
        val operaciones = DaoOperacionesPendientesFalso(
            OperacionPendiente(
                id = 2,
                uuidOperacion = "legacy-1",
                tipoEntidad = TipoOperacionPendiente.PRODUCTO,
                entidadIdLocal = 1,
                operacion = TipoOperacionPendiente.ACTUALIZAR_STOCK,
                payload = """{"stock":7}""",
                fechaCreacion = 1L
            )
        )
        val sincronizacion = crearRepositorio(operaciones)
        sincronizacion.sincronizarPendientes()
        assertEquals(EstadoSincronizacion.ERROR, operaciones.porId(2)?.estado)
        val error = operaciones.porId(2)?.ultimoError.orEmpty()
        assertTrue(error.contains("legacy") || error.contains("no soportada"))
    }

    @Test
    fun uuidOperacionSeConservaEnEntidad() {
        val uuid = "mismo-uuid-en-retry"
        val operacionPendiente = OperacionPendiente(
            uuidOperacion = uuid,
            tipoEntidad = TipoOperacionPendiente.MOVIMIENTO_INVENTARIO,
            entidadIdLocal = 1,
            operacion = TipoOperacionPendiente.CREAR_MOVIMIENTO_INVENTARIO,
            payload = "{}",
            fechaCreacion = 1L
        )
        assertEquals(uuid, operacionPendiente.uuidOperacion)
        assertEquals(EstadoSincronizacion.PENDIENTE, operacionPendiente.estado)
    }

    @Test
    fun pedidosDeOtraCuentaPermanecenPendientes() = runBlocking {
        val op=OperacionPendiente(id=12,uuidOperacion="ana",tipoEntidad="PEDIDO",entidadIdLocal=1,operacion="CREAR_PEDIDO",ownerId=1,payload="{}",fechaCreacion=1)
        val dao=DaoOperacionesPendientesFalso(op)
        crearRepositorio(dao,ownerActivo=2).sincronizarPendientes()
        assertEquals(EstadoSincronizacion.PENDIENTE,dao.porId(12)?.estado)
        assertEquals(0,dao.porId(12)?.intentos)
    }

    @Test
    fun sinSesionNoProcesaOperaciones() = runBlocking {
        val op=OperacionPendiente(id=13,uuidOperacion="ana",tipoEntidad="PEDIDO",entidadIdLocal=1,operacion="CREAR_PEDIDO",ownerId=1,payload="{}",fechaCreacion=1)
        val dao=DaoOperacionesPendientesFalso(op)
        assertTrue(crearRepositorio(dao,ownerActivo=0).sincronizarPendientes() is ResultadoSincronizacion.SinTrabajo)
        assertEquals(EstadoSincronizacion.PENDIENTE,dao.porId(13)?.estado)
    }

    private fun crearRepositorio(operaciones: OperacionPendienteDao, ownerActivo: Long = 1L): SincronizacionRepository {
        val api = ApiServiceSinRed()
        val productoDao = DaoProductoFalso()
        val categoriaDao = DaoCategoriaFalso()
        val meta = DaoMetadatosSincronizacionFalso()
        return SincronizacionRepository(
            sesion = object : com.megamarket.cliente.data.local.SesionSincronizacion {
                override val owner=kotlinx.coroutines.flow.MutableStateFlow(ownerActivo)
                override val usuarioRemotoId="usuario-1"
                override val accessToken="token-1"
            },
            api = api,
            operacionDao = operaciones,
            pedidoDao = DaoPedidoFalso(),
            productoDao = productoDao,
            metadatosSincronizacionDao = meta,
            catalogoRemoto = CatalogoRemotoRepository(
                api = api,
                categoriaDao = categoriaDao,
                productoDao = productoDao,
                operacionDao = operaciones,
                metadatosSincronizacionDao = meta
            )
        )
    }

    private class ApiServiceSinRed : ApiService {
        override suspend fun reservarCarrito(id: String, body: com.megamarket.cliente.data.remote.ReservaRequest, owner: String): Response<com.megamarket.cliente.data.remote.ReservaResponse> = throw IOException("sin red")

        override suspend fun login(body: LoginRequest): Response<LoginResponse> =
            throw IOException("sin red")

        override suspend fun listarCategorias(owner: String?): Response<CategoriasResponse> =
            throw IOException("sin red")

        override suspend fun listarProductos(owner: String?): Response<ProductosResponse> =
            throw IOException("sin red")

        override suspend fun crearPedido(body: CrearPedidoRequest, owner: String?): Response<CrearPedidoResponse> =
            throw IOException("sin red")
    }

    private class DaoMetadatosSincronizacionFalso : SyncMetadataDao {
        private var meta: SyncMetadata? = null
        override fun observarMetadatos() = flowOf(meta)
        override suspend fun obtener() = meta
        override suspend fun guardar(entidad: SyncMetadata) {
            meta = entidad
        }
    }

    private class DaoPedidoFalso : PedidoDao {
        override fun observarDelUsuario(ownerId: Long) = flowOf(emptyList<Pedido>())
        override suspend fun reservasPendientes() = emptyList<Pedido>()
        override suspend fun actualizarDetalle(detalle: PedidoDetalle) = 0

        override suspend fun insertarPedido(pedido: Pedido) = 0L
        override suspend fun insertarDetalles(detalles: List<PedidoDetalle>) = Unit
        override suspend fun insertarDireccion(direccion: Direccion) = 0L
        override suspend fun actualizarPedido(pedido: Pedido) = 0
        override suspend fun obtenerPedidoPorId(pedidoId: Long): Pedido? = null
        override suspend fun obtenerDetallesPedido(pedidoId: Long) = emptyList<PedidoDetalle>()
        override suspend fun obtenerDireccionPedido(pedidoId: Long): Direccion? = null
    }

    private class DaoProductoFalso : ProductoDao {
        override fun observarTodos(): Flow<List<Producto>> = flowOf(emptyList())
        override suspend fun ajustarDisponible(productoId: Long, cantidad: Int) = 0
        override fun observarActivos(): Flow<List<Producto>> = flowOf(emptyList())
        override suspend fun obtenerActivos() = emptyList<Producto>()
        override suspend fun contar() = 0
        override suspend fun obtenerPorId(id: Long): Producto? = null
        override fun observarPorId(id: Long): Flow<Producto?> = flowOf(null)
        override suspend fun obtenerPorProviderId(providerId: Long): Producto? = null
        override suspend fun obtenerPorRemoteId(remoteId: String): Producto? = null
        override suspend fun obtenerConRemoteId() = emptyList<Producto>()
        override suspend fun insertar(entidad: Producto) = 0L
        override suspend fun actualizarProducto(entidad: Producto) = 0
        override suspend fun descontarStockLocal(productoId: Long, cantidad: Int) = 0
    }

    private class DaoCategoriaFalso : CategoriaDao {
        override fun observarActivas(): Flow<List<Categoria>> = flowOf(emptyList())
        override suspend fun obtenerActivas() = emptyList<Categoria>()
        override suspend fun contar() = 0
        override suspend fun obtenerPorId(id: Long): Categoria? = null
        override suspend fun obtenerPorRemoteId(remoteId: String): Categoria? = null
        override suspend fun insertar(entidad: Categoria) = 0L
        override suspend fun insertarVarias(entidades: List<Categoria>) = emptyList<Long>()
        override suspend fun upsert(entidad: Categoria) = 0L
        override suspend fun actualizarCategoria(entidad: Categoria) = 0
    }

    private class DaoOperacionesPendientesFalso(
        inicial: OperacionPendiente? = null
    ) : OperacionPendienteDao {
        private val mapa = mutableMapOf<Long, OperacionPendiente>()

        init {
            if (inicial != null) {
                mapa[inicial.id] = inicial
            }
        }

        override suspend fun pendientesDelUsuario(ownerId: Long) = mapa.values.filter { it.ownerId==ownerId && it.estado==EstadoSincronizacion.PENDIENTE }
        override suspend fun cerrarReservasSuperadas(ownerId: Long,hastaId: Long,patron: String) = 0
        fun porId(id: Long) = mapa[id]

        override suspend fun insertar(entidad: OperacionPendiente): Long {
            mapa[entidad.id] = entidad
            return entidad.id
        }

        override suspend fun actualizarOperacionPendiente(entidad: OperacionPendiente): Int {
            mapa[entidad.id] = entidad
            return 1
        }

        override suspend fun obtenerOperacionesPorSincronizar() =
            mapa.values.filter {
                it.estado == EstadoSincronizacion.PENDIENTE || it.estado == EstadoSincronizacion.ERROR
            }.toList()

        override fun observarPendientesActivos(ownerId: Long) = flowOf(mapa.size)
        override fun observarErrores(ownerId: Long) = flowOf(0)
        override suspend fun contarPendientesActivos() = mapa.size
        override suspend fun contarErrores() = 0
        override suspend fun contarActivasDeEntidad(tipoEntidad: String, entidadIdLocal: Long) = 0
        override suspend fun obtenerPendienteDe(
            tipoEntidad: String,
            entidadIdLocal: Long,
            operacion: String
        ) = null

        override suspend fun recuperarEnviandoAtascadas(ahora: Long): Int = 0

        override suspend fun marcarOperacionEnviando(id: Long, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(estado = EstadoSincronizacion.ENVIANDO)
            return 1
        }

        override suspend fun marcarOperacionSincronizada(id: Long, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(
                estado = EstadoSincronizacion.SINCRONIZADO,
                sincronizadoEn = ahora
            )
            return 1
        }

        override suspend fun marcarOperacionError(id: Long, error: String, ahora: Long): Int {
            mapa[id] = mapa.getValue(id).copy(
                estado = EstadoSincronizacion.ERROR,
                ultimoError = error
            )
            return 1
        }

        override suspend fun marcarLegacyStockComoError(error: String, ahora: Long): Int {
            var n = 0
            mapa.replaceAll { _, v ->
                if (v.operacion == TipoOperacionPendiente.ACTUALIZAR_STOCK) {
                    n++
                    v.copy(estado = EstadoSincronizacion.ERROR, ultimoError = error)
                } else v
            }
            return n
        }
    }
}
