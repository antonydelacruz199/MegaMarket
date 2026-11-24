package com.megamarket.picker.data.repository

import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.PerfilSesion
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.LoginRequest
import com.megamarket.picker.data.remote.LoginResponse
import com.megamarket.picker.data.remote.MeResponse
import com.megamarket.picker.data.remote.UsuarioApiDto
import com.megamarket.picker.model.RolOperativo
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SesionRepositoryTest {

    @Test
    fun loginPickerGuardaLaSesion() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        api.loginRespuesta = loginOk("PICKER")
        val resultado = repositorio(api, almacen).iniciarSesion("ana", "secreta")
        val exito = resultado as ResultadoInicioSesion.Exito
        assertEquals(RolOperativo.PICKER, exito.usuario.rol)
        assertEquals("token-picker", almacen.tokenActual())
        assertEquals("ana-id", almacen.usuarioActivoId())
        assertEquals("PICKER", almacen.perfil()?.rol)
    }

    @Test
    fun loginRepartidorGuardaLaSesion() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        api.loginRespuesta = loginOk("repartidor", token = "token-reparto", id = "reparto-id")
        val resultado = repositorio(api, almacen).iniciarSesion("luis", "secreta")
        val exito = resultado as ResultadoInicioSesion.Exito
        assertEquals(RolOperativo.REPARTIDOR, exito.usuario.rol)
        assertEquals("token-reparto", almacen.tokenDe("reparto-id"))
    }

    @Test
    fun rechazaClienteYNoGuardaToken() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        api.loginRespuesta = loginOk("CLIENTE")
        val resultado = repositorio(api, almacen).iniciarSesion("cliente", "secreta")
        val error = resultado as ResultadoInicioSesion.Error
        assertEquals(MensajesSesion.PERMISO, error.mensaje)
        assertNull(almacen.tokenActual())
    }

    @Test
    fun rechazaAdministradorYNoGuardaToken() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        api.loginRespuesta = loginOk("ADMINISTRADOR")
        val resultado = repositorio(api, almacen).iniciarSesion("admin", "secreta")
        val error = resultado as ResultadoInicioSesion.Error
        assertEquals(MensajesSesion.PERMISO, error.mensaje)
        assertNull(almacen.tokenActual())
        assertNull(almacen.perfil())
    }

    @Test
    fun restauraConConexionConsultandoMe() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        almacen.guardar("token-picker", perfil("PICKER"))
        api.meRespuesta = Response.success(MeResponse(dto("PICKER", nombre = "Ana actualizada")))
        val resultado = repositorio(api, almacen, conectado = true).restaurar()
        val restaurada = resultado as RestauracionSesion.Restaurada
        assertEquals(1, api.meLlamadas)
        assertEquals("ana-id", api.ultimoOwner)
        assertEquals("Ana actualizada", restaurada.usuario.nombre)
        assertEquals(RolOperativo.PICKER, restaurada.usuario.rol)
    }

    @Test
    fun restauraSinConexionNoConsultaMe() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        almacen.guardar("token-reparto", perfil("REPARTIDOR", id = "reparto-id"))
        val resultado = repositorio(api, almacen, conectado = false).restaurar()
        val restaurada = resultado as RestauracionSesion.Restaurada
        assertEquals(0, api.meLlamadas)
        assertEquals(RolOperativo.REPARTIDOR, restaurada.usuario.rol)
    }

    @Test
    fun tokenExpiradoLocalmenteNoConsultaMe() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        val expira = "2020-01-01T00:00:00.000Z"
        val fin = epochMillisIso(expira)!!
        almacen.guardar("token-picker", perfil("PICKER", expiresAt = expira))
        val resultado = repositorio(api, almacen, conectado = true, ahora = fin).restaurar()
        val invalida = resultado as RestauracionSesion.Invalida
        assertEquals(MensajesSesion.EXPIRADA, invalida.mensaje)
        assertEquals(0, api.meLlamadas)
        assertNull(almacen.tokenActual())
    }

    @Test
    fun tokenInvalidoEnMeLimpiaLaSesion() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        almacen.guardar("token-viejo", perfil("PICKER"))
        api.meRespuesta = http(401)
        val resultado = repositorio(api, almacen, conectado = true).restaurar()
        val invalida = resultado as RestauracionSesion.Invalida
        assertEquals(MensajesSesion.EXPIRADA, invalida.mensaje)
        assertEquals(1, api.meLlamadas)
        assertNull(almacen.tokenActual())
        assertNull(almacen.usuarioActivoId())
    }

    @Test
    fun meConRolNoPermitidoLimpiaLaSesion() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        almacen.guardar("token-picker", perfil("PICKER"))
        api.meRespuesta = Response.success(MeResponse(dto("CLIENTE")))
        val resultado = repositorio(api, almacen, conectado = true).restaurar()
        assertTrue(resultado is RestauracionSesion.Invalida)
        assertNull(almacen.tokenActual())
    }

    @Test
    fun cerrarSesionImpideRestaurar() = runBlocking {
        val api = ApiFalsa()
        val almacen = AlmacenMemoria()
        val repo = repositorio(api, almacen)
        almacen.guardar("token-picker", perfil("PICKER"))
        repo.cerrarSesion()
        assertNull(almacen.tokenActual())
        assertTrue(repo.restaurar() is RestauracionSesion.Ausente)
        assertEquals(0, api.meLlamadas)
    }

    @Test
    fun credencialesIncorrectas() = runBlocking {
        val api = ApiFalsa()
        api.loginRespuesta = http(401)
        val resultado = repositorio(api, AlmacenMemoria()).iniciarSesion("ana", "mala")
        val error = resultado as ResultadoInicioSesion.Error
        assertEquals(MensajesSesion.CREDENCIALES, error.mensaje)
    }

    private fun repositorio(
        api: ApiFalsa,
        almacen: AlmacenMemoria,
        conectado: Boolean = true,
        ahora: Long = 0L
    ) = SesionRepository(
        almacen = almacen,
        api = api,
        hayConexion = { conectado },
        ahoraMillis = { ahora },
        nombreDispositivo = { "Prueba" }
    )

    private fun dto(rol: String, id: String = "ana-id", nombre: String = "Ana") = UsuarioApiDto(
        id = id,
        nombre = nombre,
        usuario = "ana",
        correo = "ana@mega.test",
        rol = rol,
        version = 1
    )

    private fun perfil(
        rol: String,
        id: String = "ana-id",
        expiresAt: String? = "2099-01-01T00:00:00.000Z"
    ) = PerfilSesion(
        id = id,
        nombre = "Ana",
        usuario = "ana",
        correo = "ana@mega.test",
        rol = rol,
        expiresAt = expiresAt
    )

    private fun loginOk(
        rol: String,
        token: String = "token-picker",
        id: String = "ana-id"
    ) = Response.success(
        LoginResponse(
            accessToken = token,
            tokenType = "Bearer",
            expiresAt = "2099-01-01T00:00:00.000Z",
            usuario = dto(rol, id = id)
        )
    )

    private fun <T> http(codigo: Int): Response<T> =
        Response.error(codigo, "{}".toResponseBody("application/json".toMediaType()))

    private inner class ApiFalsa : ApiService {

        override suspend fun generarQr(
            body: com.megamarket.picker.data.remote.GenerarQrRequest,
            owner: String
        ): retrofit2.Response<com.megamarket.picker.data.remote.GenerarQrResponse> = throw UnsupportedOperationException()
        override suspend fun confirmarPago(
            id: String,
            body: com.megamarket.picker.data.remote.ConfirmarPagoRequest,
            owner: String
        ): retrofit2.Response<com.megamarket.picker.data.remote.ConfirmarPagoResponse> = throw UnsupportedOperationException()
        override suspend fun obtenerPagoPedido(
            pedidoId: String,
            owner: String
        ): retrofit2.Response<com.megamarket.picker.data.remote.PagoPedidoResponse> = throw UnsupportedOperationException()
        var loginRespuesta: Response<LoginResponse> = http(500)
        var meRespuesta: Response<MeResponse> = http(500)
        var meLlamadas: Int = 0
        var ultimoOwner: String? = null

        override suspend fun login(body: LoginRequest): Response<LoginResponse> = loginRespuesta

        override suspend fun me(owner: String?): Response<MeResponse> {
            meLlamadas++
            ultimoOwner = owner
            return meRespuesta
        }

        override suspend fun listarPedidos(estado: String, owner: String, page: Int?, limit: Int?) =
            throw UnsupportedOperationException("no usado")

        override suspend fun obtenerPedido(id: String, owner: String) =
            throw UnsupportedOperationException("no usado")

        override suspend fun transicionarPedido(
            id: String,
            body: com.megamarket.picker.data.remote.TransicionPedidoRequest,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun listarPicking(owner: String, page: Int?, limit: Int?) =
            throw UnsupportedOperationException("no usado")

        override suspend fun obtenerPicking(id: String, owner: String) =
            throw UnsupportedOperationException("no usado")

        override suspend fun registrarEscaneo(
            id: String,
            detalleId: String,
            body: com.megamarket.picker.data.remote.EscaneoRequest,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun marcarFaltante(
            id: String,
            detalleId: String,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun eliminarLinea(
            id: String,
            detalleId: String,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun listarSustitutos(
            id: String,
            detalleId: String,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun proponerSustitucion(
            id: String,
            detalleId: String,
            body: com.megamarket.picker.data.remote.PropuestaSustitucionRequest,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun avisarSinStock(
            id: String,
            detalleId: String,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun listarEntregas(owner: String, estado: String?, page: Int?, limit: Int?) =
            throw UnsupportedOperationException("no usado")

        override suspend fun obtenerEntrega(
            id: String,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun registrarEntrega(
            id: String,
            body: com.megamarket.picker.data.remote.RegistrarEntregaRequest,
            owner: String
        ) = throw UnsupportedOperationException("no usado")

        override suspend fun subirFotoEntrega(
            pedidoId: String,
            archivo: okhttp3.MultipartBody.Part,
            owner: String
        ) = throw UnsupportedOperationException("no usado")


        override suspend fun obtenerSustitucion(
            id: String,
            owner: String
        ) = throw UnsupportedOperationException("no usado")
    }

    private class AlmacenMemoria : AlmacenSesion {
        private var token: String? = null
        private var perfilGuardado: PerfilSesion? = null

        override fun usuarioActivoId(): String? =
            perfilGuardado?.id?.takeIf { !token.isNullOrBlank() }

        override fun tokenActual(): String? = token?.takeIf { it.isNotBlank() }

        override fun tokenDe(usuarioId: String): String? =
            tokenActual()?.takeIf { usuarioActivoId() == usuarioId }

        override fun perfil(): PerfilSesion? = perfilGuardado?.takeIf { !token.isNullOrBlank() }

        override fun guardar(accessToken: String, perfil: PerfilSesion) {
            token = accessToken
            perfilGuardado = perfil
        }

        override fun limpiarSesion() {
            token = null
            perfilGuardado = null
        }
    }
}
