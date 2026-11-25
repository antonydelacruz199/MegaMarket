package com.megamarket.app.data.repository

import com.megamarket.app.data.remote.ActualizarProveedorRequest
import com.megamarket.app.data.remote.CrearProveedorRequest
import com.megamarket.app.data.remote.OrdenCompraDto
import com.megamarket.app.data.remote.OrdenesCompraResponse
import com.megamarket.app.data.remote.ProveedorDto
import com.megamarket.app.data.remote.ProveedorResponse
import com.megamarket.app.data.remote.ProveedoresResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ProveedoresRepositoryTest {

    private fun repo(api: ApiProveedores) = ProveedoresRepository(api, io = Dispatchers.Unconfined)

    @Test
    fun listaMapeaProveedoresYDescartaLosSinId() = runBlocking {
        val api = ApiProveedores().apply {
            lista = Response.success(
                ProveedoresResponse(
                    proveedores = listOf(
                        ProveedorDto(id = "p1", ruc = "20123456789", razonSocial = "Demo SAC", telefono = " ", activo = true),
                        ProveedorDto(id = null, ruc = "20999999999", razonSocial = "Sin id")
                    ),
                    pagina = 1, limite = 20, total = 41, hayMas = true
                )
            )
        }

        val resultado = repo(api).listar(consulta = " demo ", activo = true)

        val pagina = (resultado as ResultadoAbastecimiento.Ok).valor
        assertEquals(listOf("p1"), pagina.items.map { it.id })
        assertNull("teléfono en blanco se normaliza a null", pagina.items[0].telefono)
        assertTrue(pagina.hayMas)
        assertEquals(41, pagina.total)
        assertEquals("demo", api.ultimaConsulta)
        assertEquals(true, api.ultimoActivo)
    }

    @Test
    fun errorDelServidorSeMuestraTalCual() = runBlocking {
        val api = ApiProveedores().apply {
            crear = Response.error(
                409,
                "{\"error\":\"Ya existe un proveedor con ese RUC\"}".toResponseBody("application/json".toMediaType())
            )
        }

        val resultado = repo(api).crear(datos())

        val error = resultado as ResultadoAbastecimiento.Error
        assertEquals("Ya existe un proveedor con ese RUC", error.mensaje)
        assertEquals(409, error.codigoHttp)
    }

    @Test
    fun sinPermisoUsaMensajePorDefecto() = runBlocking {
        val api = ApiProveedores().apply {
            lista = Response.error(403, "".toResponseBody("application/json".toMediaType()))
        }

        val error = repo(api).listar() as ResultadoAbastecimiento.Error

        assertEquals("Solo el administrador puede realizar esta acción.", error.mensaje)
    }

    @Test
    fun sinRedDevuelveError() = runBlocking {
        val api = ApiProveedores().apply { fallaRed = true }

        val resultado = repo(api).listar()

        assertTrue((resultado as ResultadoAbastecimiento.Error).mensaje.startsWith("Sin conexión"))
    }

    @Test
    fun respuestaSinProveedorEsInvalida() = runBlocking {
        val api = ApiProveedores().apply { obtener = Response.success(ProveedorResponse()) }

        val resultado = repo(api).obtener("p1")

        assertTrue(resultado is ResultadoAbastecimiento.Error)
    }

    @Test
    fun crearEnviaCamposLimpiosYOpcionalesNulos() = runBlocking {
        val api = ApiProveedores().apply {
            crear = Response.success(
                ProveedorResponse(ProveedorDto(id = "p9", ruc = "20123456789", razonSocial = "Nuevo", activo = true))
            )
        }

        val resultado = repo(api).crear(
            datos(ruc = " 20123456789 ", razon = " Nuevo ", telefono = " ", correo = "", cuenta = " 001-123 ")
        )

        assertEquals("p9", (resultado as ResultadoAbastecimiento.Ok).valor.id)
        val enviado = api.ultimoCrear!!
        assertEquals("20123456789", enviado.ruc)
        assertEquals("Nuevo", enviado.razonSocial)
        assertNull(enviado.telefono)
        assertNull(enviado.correo)
        assertEquals("001-123", enviado.cuentaBancaria)
    }

    @Test
    fun actualizarEnviaCadenaVaciaParaLimpiarCampos() = runBlocking {
        val api = ApiProveedores().apply {
            actualizar = Response.success(
                ProveedorResponse(ProveedorDto(id = "p1", ruc = "20123456789", razonSocial = "X", activo = false))
            )
        }

        val resultado = repo(api).actualizar("p1", datos(telefono = "", correo = "", cuenta = "", activo = false))

        assertFalse((resultado as ResultadoAbastecimiento.Ok).valor.activo)
        val enviado = api.ultimoActualizar!!
        assertEquals("", enviado.telefono)
        assertEquals("", enviado.cuentaBancaria)
        assertFalse(enviado.activo)
    }

    @Test
    fun ordenesDelProveedorMapeaEstadosYDescartaInvalidas() = runBlocking {
        val api = ApiProveedores().apply {
            ordenes = Response.success(
                OrdenesCompraResponse(
                    ordenes = listOf(
                        OrdenCompraDto(id = "o1", numeroOrden = "OC-00000001", estado = "PAGADA", totalCentimos = 12_500),
                        OrdenCompraDto(id = "o2", estado = "ALGO_NUEVO", totalCentimos = 100),
                        OrdenCompraDto(id = "o3", estado = "SUGERIDA", totalCentimos = null)
                    ),
                    hayMas = false
                )
            )
        }

        val pagina = (repo(api).ordenesDeProveedor("p1") as ResultadoAbastecimiento.Ok).valor

        assertEquals(listOf("o1", "o2"), pagina.items.map { it.id })
        assertEquals(EstadoOrden.PAGADA, pagina.items[0].estado)
        assertEquals(EstadoOrden.DESCONOCIDO, pagina.items[1].estado)
    }

    // ---------- validación del formulario ----------

    @Test
    fun validacionAceptaDatosCorrectos() {
        assertFalse(validarDatosProveedor(datos()).hayErrores)
    }

    @Test
    fun validacionRechazaRucInvalidoRazonVaciaYCorreoMalo() {
        val errores = validarDatosProveedor(datos(ruc = "123", razon = "  ", correo = "no-es-correo"))

        assertTrue(errores.hayErrores)
        assertEquals("El RUC debe tener exactamente 11 dígitos", errores.ruc)
        assertEquals("La razón social es obligatoria", errores.razonSocial)
        assertEquals("El correo no tiene un formato válido", errores.correo)
    }

    @Test
    fun correoVacioEsValido() {
        assertNull(validarDatosProveedor(datos(correo = "  ")).correo)
    }

    private fun datos(
        ruc: String = "20123456789",
        razon: String = "Distribuidora Demo SAC",
        telefono: String = "999888777",
        correo: String = "ventas@demo.pe",
        cuenta: String = "001-1234567",
        activo: Boolean = true
    ) = DatosProveedor(ruc, razon, telefono, correo, cuenta, activo)

    private class ApiProveedores : ApiServiceNoSoportado() {
        var lista: Response<ProveedoresResponse> = Response.success(ProveedoresResponse(proveedores = emptyList()))
        var obtener: Response<ProveedorResponse> = Response.success(ProveedorResponse())
        var crear: Response<ProveedorResponse> = Response.success(ProveedorResponse())
        var actualizar: Response<ProveedorResponse> = Response.success(ProveedorResponse())
        var ordenes: Response<OrdenesCompraResponse> = Response.success(OrdenesCompraResponse(ordenes = emptyList()))
        var fallaRed = false
        var ultimaConsulta: String? = null
        var ultimoActivo: Boolean? = null
        var ultimoCrear: CrearProveedorRequest? = null
        var ultimoActualizar: ActualizarProveedorRequest? = null

        private fun red() {
            if (fallaRed) throw IOException("sin red")
        }

        override suspend fun listarProveedores(
            q: String?,
            activo: Boolean?,
            page: Int?,
            limit: Int?
        ): Response<ProveedoresResponse> {
            red()
            ultimaConsulta = q
            ultimoActivo = activo
            return lista
        }

        override suspend fun obtenerProveedor(id: String): Response<ProveedorResponse> {
            red()
            return obtener
        }

        override suspend fun crearProveedor(body: CrearProveedorRequest): Response<ProveedorResponse> {
            red()
            ultimoCrear = body
            return crear
        }

        override suspend fun actualizarProveedor(
            id: String,
            body: ActualizarProveedorRequest
        ): Response<ProveedorResponse> {
            red()
            ultimoActualizar = body
            return actualizar
        }

        override suspend fun listarOrdenesDeProveedor(
            id: String,
            estado: String?,
            page: Int?,
            limit: Int?
        ): Response<OrdenesCompraResponse> {
            red()
            return ordenes
        }
    }
}
