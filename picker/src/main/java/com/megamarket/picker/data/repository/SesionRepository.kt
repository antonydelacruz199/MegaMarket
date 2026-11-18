package com.megamarket.picker.data.repository

import com.megamarket.picker.data.local.AlmacenSesion
import com.megamarket.picker.data.local.PerfilSesion
import com.megamarket.picker.data.remote.ApiClient
import com.megamarket.picker.data.remote.ApiService
import com.megamarket.picker.data.remote.LoginRequest
import com.megamarket.picker.data.remote.LoginResponse
import com.megamarket.picker.data.remote.UsuarioApiDto
import com.megamarket.picker.model.RolOperativo
import com.megamarket.picker.model.UsuarioOperativo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object MensajesSesion {
    const val PERMISO = "No tienes permiso para acceder a esta aplicación."
    const val CREDENCIALES = "Usuario o contraseña incorrectos."
    const val EXPIRADA = "Sesión expirada. Inicia sesión de nuevo."
    const val DATOS = "Datos de inicio de sesión inválidos."
    const val SERVIDOR = "El servidor de MegaMarket no está disponible temporalmente."
    const val SIN_CONEXION = "No se pudo conectar con el servidor de MegaMarket."
    const val CONFIG_INICIAL = "Se requiere conexión a Internet para realizar la configuración inicial."
    const val COMPLETA = "Completa usuario y contraseña"
    const val RESPUESTA = "Respuesta de login inválida"
    const val GENERICO = "No se pudo iniciar sesión"
    const val USUARIO_AUSENTE = "Usuario no encontrado"
}

sealed class ResultadoInicioSesion {
    data class Exito(val usuario: UsuarioOperativo) : ResultadoInicioSesion()
    data class Error(val mensaje: String) : ResultadoInicioSesion()
}

sealed class RestauracionSesion {
    data class Restaurada(val usuario: UsuarioOperativo) : RestauracionSesion()
    data object Ausente : RestauracionSesion()
    data class Invalida(val mensaje: String) : RestauracionSesion()
}

class SesionRepository(
    private val almacen: AlmacenSesion,
    private val api: ApiService,
    private val hayConexion: () -> Boolean,
    private val ahoraMillis: () -> Long = { System.currentTimeMillis() },
    private val nombreDispositivo: () -> String = { "Android" }
) {
    suspend fun iniciarSesion(nombreUsuario: String, clave: String): ResultadoInicioSesion =
        withContext(Dispatchers.IO) {
            val usuarioTrim = nombreUsuario.trim()
            if (usuarioTrim.isBlank() || clave.isBlank()) {
                return@withContext ResultadoInicioSesion.Error(MensajesSesion.COMPLETA)
            }
            try {
                val respuesta = api.login(
                    LoginRequest(
                        usuario = usuarioTrim,
                        clave = clave,
                        deviceName = nombreDispositivo()
                    )
                )
                when (val codigo = respuesta.code()) {
                    200 -> aceptarLogin(respuesta.body())
                    400 -> ResultadoInicioSesion.Error(MensajesSesion.DATOS)
                    401 -> ResultadoInicioSesion.Error(MensajesSesion.CREDENCIALES)
                    403 -> ResultadoInicioSesion.Error(MensajesSesion.PERMISO)
                    in 500..599 -> ResultadoInicioSesion.Error(MensajesSesion.SERVIDOR)
                    else -> ResultadoInicioSesion.Error("${MensajesSesion.GENERICO} ($codigo)")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                ResultadoInicioSesion.Error(mensajeRed(error))
            } catch (_: Exception) {
                ResultadoInicioSesion.Error(MensajesSesion.GENERICO)
            }
        }

    suspend fun restaurar(): RestauracionSesion = withContext(Dispatchers.IO) {
        val perfil = almacen.perfil() ?: return@withContext RestauracionSesion.Ausente
        if (almacen.tokenActual().isNullOrBlank()) {
            return@withContext RestauracionSesion.Ausente
        }
        if (!tokenSigueVigente(perfil.expiresAt, ahoraMillis())) {
            almacen.limpiarSesion()
            return@withContext RestauracionSesion.Invalida(MensajesSesion.EXPIRADA)
        }
        val local = perfil.aUsuario()
        if (local == null) {
            almacen.limpiarSesion()
            return@withContext RestauracionSesion.Invalida(MensajesSesion.PERMISO)
        }
        if (!hayConexion()) {
            return@withContext RestauracionSesion.Restaurada(local)
        }
        try {
            val respuesta = api.me(perfil.id)
            when (respuesta.code()) {
                200 -> aceptarPerfilRemoto(respuesta.body()?.usuario, perfil.expiresAt)
                401 -> {
                    almacen.limpiarSesion()
                    RestauracionSesion.Invalida(MensajesSesion.EXPIRADA)
                }
                403 -> {
                    almacen.limpiarSesion()
                    RestauracionSesion.Invalida(MensajesSesion.PERMISO)
                }
                404 -> {
                    almacen.limpiarSesion()
                    RestauracionSesion.Invalida(MensajesSesion.USUARIO_AUSENTE)
                }
                else -> RestauracionSesion.Restaurada(local)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (error.message == ApiClient.MENSAJE_CUENTA_CAMBIADA || almacen.usuarioActivoId() == null) {
                RestauracionSesion.Ausente
            } else {
                RestauracionSesion.Restaurada(local)
            }
        }
    }

    fun cerrarSesion() {
        almacen.limpiarSesion()
    }

    private fun aceptarLogin(cuerpo: LoginResponse?): ResultadoInicioSesion {
        val token = cuerpo?.accessToken?.trim().orEmpty()
        val remoto = cuerpo?.usuario
        if (token.isEmpty() || remoto == null) {
            return ResultadoInicioSesion.Error(MensajesSesion.RESPUESTA)
        }
        val usuario = usuarioPermitido(remoto) ?: return ResultadoInicioSesion.Error(MensajesSesion.PERMISO)
        almacen.guardar(
            accessToken = token,
            perfil = usuario.aPerfil(cuerpo.expiresAt)
        )
        return ResultadoInicioSesion.Exito(usuario)
    }

    private fun aceptarPerfilRemoto(
        remoto: UsuarioApiDto?,
        expiresAt: String?
    ): RestauracionSesion {
        val usuario = remoto?.let(::usuarioPermitido)
        if (usuario == null) {
            almacen.limpiarSesion()
            return RestauracionSesion.Invalida(MensajesSesion.PERMISO)
        }
        val token = almacen.tokenActual()
        if (token.isNullOrBlank() || almacen.usuarioActivoId() != usuario.id) {
            almacen.limpiarSesion()
            return RestauracionSesion.Ausente
        }
        almacen.guardar(token, usuario.aPerfil(expiresAt))
        return RestauracionSesion.Restaurada(usuario)
    }

    private fun usuarioPermitido(remoto: UsuarioApiDto): UsuarioOperativo? {
        val rol = RolOperativo.desde(remoto.rol) ?: return null
        val id = remoto.id?.trim().orEmpty()
        if (id.isEmpty()) return null
        return UsuarioOperativo(
            id = id,
            nombre = remoto.nombre?.trim().orEmpty().ifBlank { remoto.usuario?.trim().orEmpty() },
            usuario = remoto.usuario?.trim().orEmpty(),
            correo = remoto.correo?.trim().orEmpty(),
            rol = rol
        )
    }

    private fun mensajeRed(error: IOException): String {
        if (error.message == ApiClient.MENSAJE_CUENTA_CAMBIADA) return MensajesSesion.GENERICO
        val hayRed = hayConexion()
        val sinHost = error is UnknownHostException
        val falloServidor = error is ConnectException ||
            error is SocketTimeoutException ||
            (hayRed && !sinHost)
        return if (falloServidor) MensajesSesion.SIN_CONEXION else MensajesSesion.CONFIG_INICIAL
    }
}

private fun UsuarioOperativo.aPerfil(expiresAt: String?) = PerfilSesion(
    id = id,
    nombre = nombre,
    usuario = usuario,
    correo = correo,
    rol = rol.name,
    expiresAt = expiresAt
)

private fun PerfilSesion.aUsuario(): UsuarioOperativo? {
    val rolOperativo = RolOperativo.desde(rol) ?: return null
    if (id.isBlank()) return null
    return UsuarioOperativo(
        id = id,
        nombre = nombre,
        usuario = usuario,
        correo = correo,
        rol = rolOperativo
    )
}
