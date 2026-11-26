package com.megamarket.app.data.repository

import com.google.gson.Gson
import com.megamarket.app.data.remote.AprobarOrdenRequest
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.CrearOrdenCompraRequest
import com.megamarket.app.data.remote.ErrorPagoDto
import com.megamarket.app.data.remote.ItemOrdenRequest
import com.megamarket.app.data.remote.ItemRecepcionRequest
import com.megamarket.app.data.remote.OrdenCompraResponse
import com.megamarket.app.data.remote.PagarOrdenRequest
import com.megamarket.app.data.remote.ProductoApiDto
import com.megamarket.app.data.remote.RecepcionOrdenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private val SOLES_REGEX = Regex("^\\d{1,9}([.,]\\d{1,2})?$")

/** Convierte "12", "12.5" o "12,50" (soles) a céntimos. Devuelve null si el texto no es un monto válido. */
fun parsearSolesACentimos(texto: String): Long? {
    val limpio = texto.trim()
    if (!SOLES_REGEX.matches(limpio)) return null
    val partes = limpio.replace(',', '.').split('.')
    val soles = partes[0].toLongOrNull() ?: return null
    val centimos = partes.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
    return soles * 100 + centimos
}

/** Total de una orden nueva: suma de cantidad × precio unitario. */
fun totalLineas(lineas: List<LineaOrdenNueva>): Long = lineas.sumOf { it.subtotalCentimos }

/**
 * Cliente delgado de `api/compras` para el rol administrador. El servidor es la única fuente de
 * estados y totales. El pago a proveedor usa el adaptador Banco Andino.
 */
class ComprasRepository(
    private val api: ApiService,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    gson: Gson = Gson()
) {
    private val ejecutor = EjecutorApi(io, gson)

    suspend fun listarOrdenes(
        estado: EstadoOrden? = null,
        proveedorId: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): ResultadoAbastecimiento<Pagina<OrdenCompra>> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudieron consultar las órdenes.",
        mensajeBase = "No se pudieron consultar las órdenes de compra.",
        llamada = {
            api.listarOrdenesCompra(
                estado = estado?.takeIf { it != EstadoOrden.DESCONOCIDO }?.name,
                proveedorId = proveedorId,
                page = page,
                limit = limit
            )
        },
        mapear = { it.aPaginaOrdenes(page) }
    )

    suspend fun obtenerOrden(id: String): ResultadoAbastecimiento<OrdenCompra> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo consultar la orden.",
        mensajeBase = "No se pudo consultar la orden de compra.",
        llamada = { api.obtenerOrdenCompra(id) },
        mapear = { it.aOrden() }
    )

    suspend fun crearOrden(
        proveedorId: String,
        lineas: List<LineaOrdenNueva>
    ): ResultadoAbastecimiento<OrdenCompra> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo crear la orden.",
        mensajeBase = "No se pudo crear la orden de compra.",
        llamada = {
            api.crearOrdenCompra(
                CrearOrdenCompraRequest(
                    proveedorId = proveedorId,
                    items = lineas.map {
                        ItemOrdenRequest(it.productoId, it.cantidad, it.precioUnitarioCentimos)
                    }
                )
            )
        },
        mapear = { it.aOrden() }
    )

    suspend fun aprobar(id: String, uuidOperacion: String): ResultadoAbastecimiento<OrdenCompra> =
        ejecutor.ejecutar(
            mensajeSinRed = "Sin conexión. No se pudo aprobar la orden.",
            mensajeBase = "No se pudo aprobar la orden de compra.",
            llamada = { api.aprobarOrdenCompra(id, AprobarOrdenRequest(uuidOperacion)) },
            mapear = { it.aOrden() }
        )

    suspend fun cancelar(id: String): ResultadoAbastecimiento<OrdenCompra> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo cancelar la orden.",
        mensajeBase = "No se pudo cancelar la orden de compra.",
        llamada = { api.cancelarOrdenCompra(id) },
        mapear = { it.aOrden() }
    )

    /**
     * Pago a proveedor. El servidor responde 409 cuando la transferencia no queda CONFIRMADA pero
     * adjunta la transferencia: en ese caso se devuelve [ResultadoAbastecimiento.Ok] con
     * [RespuestaPago.confirmada] en falso para que la UI muestre el estado real.
     * Solo un error sin transferencia (red, validación, conflicto) se devuelve como Error.
     */
    suspend fun pagar(
        id: String,
        uuidOperacion: String,
        montoCentimos: Long? = null
    ): ResultadoAbastecimiento<RespuestaPago> = withContext(io) {
        try {
            val respuesta = api.pagarOrdenCompra(id, PagarOrdenRequest(uuidOperacion, montoCentimos))
            if (respuesta.isSuccessful) {
                val cuerpo = respuesta.body()
                    ?: return@withContext ResultadoAbastecimiento.Error("El servidor devolvió una respuesta inválida")
                return@withContext ResultadoAbastecimiento.Ok(cuerpo.aRespuestaPago())
            }
            val texto = ejecutor.leerCuerpoError(respuesta)
            val conTransferencia = ejecutor.aDto(texto, OrdenCompraResponse::class.java)
            if (conTransferencia != null && conTransferencia.aRespuestaPago().transferencia != null) {
                return@withContext ResultadoAbastecimiento.Ok(conTransferencia.aRespuestaPago())
            }
            val detalle = ejecutor.aDto(texto, ErrorPagoDto::class.java)
            ResultadoAbastecimiento.Error(
                mensaje = ejecutor.mensajeError(
                    respuesta.code(), texto, "No se pudo registrar el pago a proveedor."
                ),
                codigoHttp = respuesta.code(),
                uuidOperacionExistente = detalle?.uuidOperacionExistente?.takeIf { it.isNotBlank() }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            // Sin red el resultado es incierto: la UI conserva el uuid para reintentar sin duplicar.
            ResultadoAbastecimiento.Error("Sin conexión. No se pudo confirmar el pago; reintenta o consulta la transferencia.")
        } catch (_: com.google.gson.JsonParseException) {
            ResultadoAbastecimiento.Error("El servidor devolvió una respuesta ilegible. Consulta la transferencia antes de reintentar.")
        }
    }

    suspend fun consultarTransferencia(
        uuidOperacion: String
    ): ResultadoAbastecimiento<TransferenciaProveedor> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo consultar la transferencia.",
        mensajeBase = "No se pudo consultar la transferencia.",
        llamada = { api.consultarTransferenciaProveedor(uuidOperacion) },
        mapear = { it.transferencia?.aDominio() }
    )

    suspend fun recibir(
        id: String,
        uuidOperacion: String,
        items: List<ItemRecepcion>
    ): ResultadoAbastecimiento<RespuestaRecepcion> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo registrar la recepción.",
        mensajeBase = "No se pudo registrar la recepción.",
        llamada = {
            api.registrarRecepcionOrden(
                id,
                RecepcionOrdenRequest(
                    uuidOperacion = uuidOperacion,
                    items = items.map { ItemRecepcionRequest(it.detalleId, it.cantidad) }
                )
            )
        },
        mapear = { cuerpo ->
            cuerpo.aOrden()?.let { orden ->
                RespuestaRecepcion(
                    orden = orden,
                    recepcionCompleta = cuerpo.recepcionCompleta == true,
                    idempotente = cuerpo.idempotente == true,
                    movimientos = cuerpo.movimientos.orEmpty().mapNotNull { it.aDominio() }
                )
            }
        }
    )

    suspend fun historialRecepciones(id: String): ResultadoAbastecimiento<List<MovimientoRecepcion>> =
        ejecutor.ejecutar(
            mensajeSinRed = "Sin conexión. No se pudo consultar el historial de recepción.",
            mensajeBase = "No se pudo consultar el historial de recepción.",
            llamada = { api.listarRecepcionesOrden(id) },
            mapear = { cuerpo -> cuerpo.movimientos?.mapNotNull { it.aDominio() } }
        )

    /** Búsqueda de productos del catálogo remoto para armar las líneas de una orden. */
    suspend fun buscarProductos(
        consulta: String = "",
        page: Int = 1,
        limit: Int = 20
    ): ResultadoAbastecimiento<Pagina<ProductoOpcion>> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo consultar el catálogo.",
        mensajeBase = "No se pudo consultar el catálogo de productos.",
        llamada = {
            api.listarProductos(page = page, limit = limit, q = consulta.trim().takeIf { it.isNotEmpty() })
        },
        mapear = { cuerpo ->
            val items = cuerpo.productos.filter { it.activo }.map { it.aOpcion() }
            Pagina(
                items = items,
                pagina = cuerpo.pagina ?: page,
                hayMas = cuerpo.hayMas == true,
                total = cuerpo.total ?: items.size
            )
        }
    )
}

private fun ProductoApiDto.aOpcion() = ProductoOpcion(
    id = id,
    nombre = nombre,
    marca = marca,
    precioVentaCentimos = precioCentimos,
    stock = stock
)

/** Orden del cuerpo; si la transferencia viene al nivel raíz se adjunta a la orden. */
internal fun OrdenCompraResponse.aOrden(): OrdenCompra? {
    val base = orden?.aDominio() ?: return null
    if (base.transferencia != null) return base
    return base.copy(transferencia = transferencia?.aDominio())
}

internal fun OrdenCompraResponse.aRespuestaPago(): RespuestaPago {
    val ordenDominio = aOrden()
    val transf = transferencia?.aDominio() ?: ordenDominio?.transferencia
    return RespuestaPago(
        orden = ordenDominio,
        transferencia = transf,
        simulacion = true,
        aviso = aviso?.takeIf { it.isNotBlank() },
        idempotente = idempotente == true,
        mensajeServidor = error?.takeIf { it.isNotBlank() } ?: transf?.mensaje
    )
}
