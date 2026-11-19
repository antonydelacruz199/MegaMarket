package com.megamarket.cliente.data.repository

import com.google.gson.Gson
import com.megamarket.cliente.data.local.dao.PedidoDao
import com.megamarket.cliente.data.remote.ApiService
import com.megamarket.cliente.data.remote.DetallePedidoRemotoDto
import com.megamarket.cliente.data.remote.DireccionPedidoRemotoDto
import com.megamarket.cliente.data.remote.ErrorApiDto
import com.megamarket.cliente.data.remote.PagoPedidoRemotoDto
import com.megamarket.cliente.data.remote.PedidoRemotoDto
import com.megamarket.cliente.model.FranjaEntrega
import com.megamarket.cliente.model.ModalidadPagoCheckout
import com.megamarket.modelo.EstadoSincronizacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class MisPedidosRepository(
    private val api: ApiService,
    private val pedidoDao: PedidoDao,
    private val sustituciones: SustitucionesRepository,
    private val franjas: FranjasRepository,
    private val usuarioRemotoId: () -> String?,
    private val ownerLocalId: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun listar(filtro: FiltroMisPedidos): ResultadoMisPedidos<List<PedidoResumenCliente>> =
        withContext(io) {
            val owner = usuarioRemotoId() ?: return@withContext sesionVencida()
            try {
                val respuesta = api.listarPedidos(estado = null, owner = owner)
                if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
                val pendientes = when (val s = sustituciones.listar(EstadoSustitucion.PENDIENTE)) {
                    is ResultadoSustitucion.Exito -> s.valor.mapNotNull { it.pedidoId }.toSet()
                    else -> emptySet()
                }
                val etiquetasFranja = cargarEtiquetasFranja()
                val lista = respuesta.body()?.pedidos.orEmpty()
                    .mapNotNull { dto -> dto.aResumen(pendientes, etiquetasFranja) }
                    .filter { filtro.coincide(it.estado, it.modalidadPago) }
                ResultadoMisPedidos.Exito(lista)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                falloRed(error)
            }
        }

    suspend fun obtenerDetalle(pedidoId: String): ResultadoMisPedidos<PedidoDetalleCliente> =
        withContext(io) {
            val owner = usuarioRemotoId() ?: return@withContext sesionVencida()
            val id = pedidoId.trim()
            if (id.isEmpty()) {
                return@withContext ResultadoMisPedidos.Error(
                    TipoErrorSustitucion.NO_ENCONTRADO,
                    "Pedido no encontrado"
                )
            }
            try {
                val respuesta = api.obtenerPedido(id, owner)
                if (!respuesta.isSuccessful) return@withContext fallo(respuesta)
                val body = respuesta.body()
                val pedido = body?.pedido
                    ?: return@withContext ResultadoMisPedidos.Error(
                        TipoErrorSustitucion.SERVIDOR,
                        MensajesSustitucionCliente.SERVIDOR
                    )
                if (!pedido.clienteId.isNullOrBlank() && pedido.clienteId != owner) {
                    return@withContext ResultadoMisPedidos.Error(
                        TipoErrorSustitucion.PROHIBIDO,
                        MensajesSustitucionCliente.PROHIBIDO
                    )
                }
                sincronizarRoomSiMasNuevo(pedido)
                val susts = when (val s = sustituciones.listar(null)) {
                    is ResultadoSustitucion.Exito -> s.valor.filter { it.pedidoId == id }
                    else -> emptyList()
                }
                ResultadoMisPedidos.Exito(
                    PedidoDetalleCliente(
                        id = id,
                        fecha = pedido.fecha,
                        totalCentimos = pedido.totalCentimos ?: 0L,
                        estado = pedido.estado?.trim().orEmpty(),
                        franjaEntregaId = pedido.franjaEntregaId,
                        franjaEtiqueta = resolverEtiquetaFranja(pedido.franjaEntregaId, id),
                        version = pedido.version,
                        lineas = body.detalles.orEmpty().mapNotNull { it.aLinea() },
                        direccion = body.direccion?.aDominio(),
                        pago = body.pago?.aDominio(),
                        sustituciones = susts
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                falloRed(error)
            }
        }

    private suspend fun cargarEtiquetasFranja(): Map<String, String> {
        val mapa = mutableMapOf<String, String>()
        pedidoDao.listarDelUsuario(ownerLocalId()).forEach { local ->
            val id = local.franjaEntregaId?.trim().orEmpty()
            val etiqueta = FranjaEntrega.etiquetaDesdeCampos(
                local.franjaFecha,
                local.franjaHoraInicio,
                local.franjaHoraFin
            )
            if (id.isNotEmpty() && !etiqueta.isNullOrBlank()) {
                mapa[id] = etiqueta
            }
        }
        when (val r = franjas.listar()) {
            is ResultadoFranjas.Exito -> r.valor.forEach { mapa.putIfAbsent(it.id, it.etiquetaCompleta) }
            is ResultadoFranjas.Error -> Unit
        }
        return mapa
    }

    private suspend fun resolverEtiquetaFranja(franjaId: String?, pedidoRemotoId: String): String? {
        val id = franjaId?.trim().orEmpty()
        if (id.isEmpty()) return null
        pedidoDao.obtenerPorRemoteId(pedidoRemotoId)
            ?.takeIf { it.clienteId == ownerLocalId() }
            ?.let { local ->
                FranjaEntrega.etiquetaDesdeCampos(
                    local.franjaFecha,
                    local.franjaHoraInicio,
                    local.franjaHoraFin
                )?.let { return it }
            }
        pedidoDao.listarDelUsuario(ownerLocalId())
            .firstOrNull { it.franjaEntregaId == id }
            ?.let { local ->
                FranjaEntrega.etiquetaDesdeCampos(
                    local.franjaFecha,
                    local.franjaHoraInicio,
                    local.franjaHoraFin
                )?.let { return it }
            }
        return when (val r = franjas.obtener(id)) {
            is ResultadoFranjas.Exito -> r.valor.etiquetaCompleta
            is ResultadoFranjas.Error -> null
        }
    }

    private suspend fun sincronizarRoomSiMasNuevo(pedidoApi: PedidoRemotoDto) {
        val remoto = pedidoApi.id?.trim().orEmpty()
        if (remoto.isEmpty()) return
        val local = pedidoDao.obtenerPorRemoteId(remoto)
            ?.takeIf { it.clienteId == ownerLocalId() }
            ?: return
        val versionRemota = pedidoApi.version
        val versionLocal = local.remoteVersion
        if (versionRemota != null && versionLocal != null && versionRemota < versionLocal) {
            return
        }
        val estado = pedidoApi.estado?.trim().orEmpty().ifBlank { local.estado }
        pedidoDao.actualizarPedido(
            local.copy(
                estado = estado,
                totalCentimos = pedidoApi.totalCentimos ?: local.totalCentimos,
                remoteVersion = versionRemota ?: local.remoteVersion,
                estadoSync = EstadoSincronizacion.SINCRONIZADO,
                franjaEntregaId = pedidoApi.franjaEntregaId ?: local.franjaEntregaId
            )
        )
    }

    private suspend fun PedidoRemotoDto.aResumen(
        pendientes: Set<String>,
        etiquetasFranja: Map<String, String>
    ): PedidoResumenCliente? {
        val id = this.id?.trim().orEmpty()
        if (id.isEmpty()) return null
        val local = pedidoDao.obtenerPorRemoteId(id)?.takeIf { it.clienteId == ownerLocalId() }
        val modalidad = local?.modalidadPago
        val franjaEtiqueta = local?.let {
            FranjaEntrega.etiquetaDesdeCampos(it.franjaFecha, it.franjaHoraInicio, it.franjaHoraFin)
        } ?: franjaEntregaId?.let { etiquetasFranja[it] }
        return PedidoResumenCliente(
            id = id,
            fecha = fecha,
            totalCentimos = totalCentimos ?: 0L,
            estado = estado?.trim().orEmpty(),
            franjaEntregaId = franjaEntregaId ?: local?.franjaEntregaId,
            franjaEtiqueta = franjaEtiqueta,
            modalidadPago = modalidad,
            modalidadEtiqueta = ModalidadPagoCheckout.desdeApi(modalidad)?.titulo,
            tienePropuestaPendiente = pendientes.contains(id),
            version = version
        )
    }

    private fun DetallePedidoRemotoDto.aLinea(): LineaPedidoCliente? {
        val id = this.id?.trim().orEmpty()
        if (id.isEmpty()) return null
        return LineaPedidoCliente(
            id = id,
            productoId = productoId,
            nombre = nombreProducto?.trim().orEmpty().ifBlank { "Producto" },
            cantidad = cantidad ?: 0,
            precioUnitarioCentimos = precioUnitarioCentimos ?: 0L,
            subtotalCentimos = subtotalCentimos ?: 0L,
            estadoPicking = estadoPicking,
            cantidadArmada = cantidadArmada
        )
    }

    private fun DireccionPedidoRemotoDto.aDominio() = DireccionPedidoCliente(
        departamento = departamento,
        provincia = provincia,
        distrito = distrito,
        direccion = direccion,
        telefono = telefono
    )

    private fun PagoPedidoRemotoDto.aDominio() = PagoPedidoCliente(
        id = id,
        modalidad = modalidad,
        estado = estado,
        montoCentimos = montoCentimos,
        medioPago = medioPago,
        numeroOperacionBanco = numeroOperacionBanco,
        fechaConfirmacion = fechaConfirmacion
    )

    private fun <T> fallo(response: Response<*>): ResultadoMisPedidos<T> {
        val crudo = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val dto = runCatching { gson.fromJson(crudo, ErrorApiDto::class.java) }.getOrNull()
        val tipo = when (response.code()) {
            401 -> TipoErrorSustitucion.SESION
            403 -> TipoErrorSustitucion.PROHIBIDO
            404 -> TipoErrorSustitucion.NO_ENCONTRADO
            409 -> TipoErrorSustitucion.CONFLICTO
            else -> TipoErrorSustitucion.SERVIDOR
        }
        return ResultadoMisPedidos.Error(
            tipo,
            dto?.error?.takeIf { it.isNotBlank() } ?: MensajesSustitucionCliente.SERVIDOR,
            dto?.codigo
        )
    }

    private fun <T> falloRed(error: IOException): ResultadoMisPedidos<T> =
        ResultadoMisPedidos.Error(
            TipoErrorSustitucion.RED,
            error.message?.takeIf { it.isNotBlank() } ?: MensajesSustitucionCliente.SIN_CONEXION
        )

    private fun <T> sesionVencida(): ResultadoMisPedidos<T> =
        ResultadoMisPedidos.Error(TipoErrorSustitucion.SESION, MensajesSustitucionCliente.SESION)
}
