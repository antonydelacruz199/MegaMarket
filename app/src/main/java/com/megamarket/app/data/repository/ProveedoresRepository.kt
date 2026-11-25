package com.megamarket.app.data.repository

import com.google.gson.Gson
import com.megamarket.app.data.remote.ActualizarProveedorRequest
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.CrearProveedorRequest
import com.megamarket.app.data.remote.OrdenesCompraResponse
import com.megamarket.app.data.remote.ProveedoresResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Errores de validación local del formulario de proveedor (el servidor revalida). */
data class ErroresProveedor(
    val ruc: String? = null,
    val razonSocial: String? = null,
    val correo: String? = null
) {
    val hayErrores: Boolean get() = ruc != null || razonSocial != null || correo != null
}

private val CORREO_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun validarDatosProveedor(datos: DatosProveedor): ErroresProveedor = ErroresProveedor(
    ruc = if (Regex("^\\d{11}$").matches(datos.ruc.trim())) null else "El RUC debe tener exactamente 11 dígitos",
    razonSocial = if (datos.razonSocial.isBlank()) "La razón social es obligatoria" else null,
    correo = datos.correo.trim().takeIf { it.isNotEmpty() && !CORREO_REGEX.matches(it) }
        ?.let { "El correo no tiene un formato válido" }
)

/** Cliente delgado de `api/proveedores` para el rol administrador (sin Room: el servidor manda). */
class ProveedoresRepository(
    private val api: ApiService,
    io: CoroutineDispatcher = Dispatchers.IO,
    gson: Gson = Gson()
) {
    private val ejecutor = EjecutorApi(io, gson)

    suspend fun listar(
        consulta: String = "",
        activo: Boolean? = null,
        page: Int = 1,
        limit: Int = 20
    ): ResultadoAbastecimiento<Pagina<Proveedor>> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo consultar los proveedores.",
        mensajeBase = "No se pudo consultar los proveedores.",
        llamada = {
            api.listarProveedores(
                q = consulta.trim().takeIf { it.isNotEmpty() },
                activo = activo,
                page = page,
                limit = limit
            )
        },
        mapear = { it.aPagina(page) }
    )

    suspend fun obtener(id: String): ResultadoAbastecimiento<Proveedor> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo consultar el proveedor.",
        mensajeBase = "No se pudo consultar el proveedor.",
        llamada = { api.obtenerProveedor(id) },
        mapear = { it.proveedor?.aDominio() }
    )

    suspend fun crear(datos: DatosProveedor): ResultadoAbastecimiento<Proveedor> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudo registrar el proveedor.",
        mensajeBase = "No se pudo registrar el proveedor.",
        llamada = {
            api.crearProveedor(
                CrearProveedorRequest(
                    ruc = datos.ruc.trim(),
                    razonSocial = datos.razonSocial.trim(),
                    telefono = datos.telefono.trim().takeIf { it.isNotEmpty() },
                    correo = datos.correo.trim().takeIf { it.isNotEmpty() },
                    cuentaBancaria = datos.cuentaBancaria.trim().takeIf { it.isNotEmpty() },
                    activo = datos.activo
                )
            )
        },
        mapear = { it.proveedor?.aDominio() }
    )

    suspend fun actualizar(id: String, datos: DatosProveedor): ResultadoAbastecimiento<Proveedor> =
        ejecutor.ejecutar(
            mensajeSinRed = "Sin conexión. No se pudo actualizar el proveedor.",
            mensajeBase = "No se pudo actualizar el proveedor.",
            llamada = {
                api.actualizarProveedor(
                    id,
                    ActualizarProveedorRequest(
                        ruc = datos.ruc.trim(),
                        razonSocial = datos.razonSocial.trim(),
                        telefono = datos.telefono.trim(),
                        correo = datos.correo.trim(),
                        cuentaBancaria = datos.cuentaBancaria.trim(),
                        activo = datos.activo
                    )
                )
            },
            mapear = { it.proveedor?.aDominio() }
        )

    suspend fun ordenesDeProveedor(
        id: String,
        page: Int = 1,
        limit: Int = 10
    ): ResultadoAbastecimiento<Pagina<OrdenCompra>> = ejecutor.ejecutar(
        mensajeSinRed = "Sin conexión. No se pudieron consultar las órdenes del proveedor.",
        mensajeBase = "No se pudieron consultar las órdenes del proveedor.",
        llamada = { api.listarOrdenesDeProveedor(id = id, page = page, limit = limit) },
        mapear = { it.aPaginaOrdenes(page) }
    )
}

internal fun ProveedoresResponse.aPagina(paginaSolicitada: Int): Pagina<Proveedor>? {
    val lista = proveedores ?: return null
    val items = lista.mapNotNull { it.aDominio() }
    return Pagina(
        items = items,
        pagina = pagina ?: paginaSolicitada,
        hayMas = hayMas == true,
        total = total ?: items.size
    )
}

internal fun OrdenesCompraResponse.aPaginaOrdenes(paginaSolicitada: Int): Pagina<OrdenCompra>? {
    val lista = ordenes ?: return null
    val items = lista.mapNotNull { it.aDominio() }
    return Pagina(
        items = items,
        pagina = pagina ?: paginaSolicitada,
        hayMas = hayMas == true,
        total = total ?: items.size
    )
}
