package com.megamarket.app.data.repository

import com.megamarket.app.data.remote.ActualizarProductoRequest
import com.megamarket.app.data.remote.ActualizarProveedorRequest
import com.megamarket.app.data.remote.AprobarOrdenRequest
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.BilleteraMovimientosResponse
import com.megamarket.app.data.remote.BilleteraSaldoResponse
import com.megamarket.app.data.remote.CategoriasResponse
import com.megamarket.app.data.remote.CrearOrdenCompraRequest
import com.megamarket.app.data.remote.CrearProductoRequest
import com.megamarket.app.data.remote.CrearProveedorRequest
import com.megamarket.app.data.remote.EliminarProductoRequest
import com.megamarket.app.data.remote.EliminarProductoResponse
import com.megamarket.app.data.remote.LoginRequest
import com.megamarket.app.data.remote.LoginResponse
import com.megamarket.app.data.remote.MovimientoInventarioRequest
import com.megamarket.app.data.remote.MovimientoInventarioResponse
import com.megamarket.app.data.remote.OrdenCompraResponse
import com.megamarket.app.data.remote.OrdenesCompraResponse
import com.megamarket.app.data.remote.PagarOrdenRequest
import com.megamarket.app.data.remote.ProductoResponse
import com.megamarket.app.data.remote.ProductosResponse
import com.megamarket.app.data.remote.ProveedorResponse
import com.megamarket.app.data.remote.ProveedoresResponse
import com.megamarket.app.data.remote.RecepcionOrdenRequest
import com.megamarket.app.data.remote.RecepcionesResponse
import com.megamarket.app.data.remote.SubidaImagenProductoResponse
import com.megamarket.app.data.remote.TransferenciaResponse
import okhttp3.MultipartBody
import retrofit2.Response

/**
 * Base de fakes de [ApiService] para pruebas: todo lanza [UnsupportedOperationException] y cada
 * prueba sobrescribe solo los endpoints que necesita. Así agregar endpoints no rompe pruebas ajenas.
 */
abstract class ApiServiceNoSoportado : ApiService {
    override suspend fun login(body: LoginRequest): Response<LoginResponse> = noSoportado()
    override suspend fun listarCategorias(): Response<CategoriasResponse> = noSoportado()
    override suspend fun listarProductos(page: Int?, limit: Int?, q: String?): Response<ProductosResponse> =
        noSoportado()

    override suspend fun obtenerProducto(id: String): Response<ProductoResponse> = noSoportado()
    override suspend fun crearProducto(body: CrearProductoRequest): Response<ProductoResponse> = noSoportado()
    override suspend fun actualizarProducto(
        id: String,
        body: ActualizarProductoRequest
    ): Response<ProductoResponse> = noSoportado()

    override suspend fun eliminarProducto(
        id: String,
        body: EliminarProductoRequest
    ): Response<EliminarProductoResponse> = noSoportado()

    override suspend fun registrarMovimiento(
        body: MovimientoInventarioRequest
    ): Response<MovimientoInventarioResponse> = noSoportado()

    override suspend fun obtenerBilleteraComercio(): Response<BilleteraSaldoResponse> = noSoportado()
    override suspend fun listarMovimientosBilletera(page: Int?, limit: Int?): Response<BilleteraMovimientosResponse> =
        noSoportado()

    override suspend fun listarProveedores(
        q: String?,
        activo: Boolean?,
        page: Int?,
        limit: Int?
    ): Response<ProveedoresResponse> = noSoportado()

    override suspend fun obtenerProveedor(id: String): Response<ProveedorResponse> = noSoportado()
    override suspend fun crearProveedor(body: CrearProveedorRequest): Response<ProveedorResponse> = noSoportado()
    override suspend fun actualizarProveedor(
        id: String,
        body: ActualizarProveedorRequest
    ): Response<ProveedorResponse> = noSoportado()

    override suspend fun listarOrdenesDeProveedor(
        id: String,
        estado: String?,
        page: Int?,
        limit: Int?
    ): Response<OrdenesCompraResponse> = noSoportado()

    override suspend fun listarOrdenesCompra(
        estado: String?,
        proveedorId: String?,
        page: Int?,
        limit: Int?
    ): Response<OrdenesCompraResponse> = noSoportado()

    override suspend fun obtenerOrdenCompra(id: String): Response<OrdenCompraResponse> = noSoportado()
    override suspend fun crearOrdenCompra(body: CrearOrdenCompraRequest): Response<OrdenCompraResponse> =
        noSoportado()

    override suspend fun aprobarOrdenCompra(id: String, body: AprobarOrdenRequest): Response<OrdenCompraResponse> =
        noSoportado()

    override suspend fun cancelarOrdenCompra(id: String): Response<OrdenCompraResponse> = noSoportado()
    override suspend fun pagarOrdenCompra(id: String, body: PagarOrdenRequest): Response<OrdenCompraResponse> =
        noSoportado()

    override suspend fun registrarRecepcionOrden(
        id: String,
        body: RecepcionOrdenRequest
    ): Response<OrdenCompraResponse> = noSoportado()

    override suspend fun listarRecepcionesOrden(id: String): Response<RecepcionesResponse> = noSoportado()
    override suspend fun consultarTransferenciaProveedor(uuidOperacion: String): Response<TransferenciaResponse> =
        noSoportado()

    override suspend fun subirImagenProducto(
        productoId: String,
        archivo: MultipartBody.Part
    ): Response<SubidaImagenProductoResponse> = noSoportado()

    private fun noSoportado(): Nothing = throw UnsupportedOperationException()
}
