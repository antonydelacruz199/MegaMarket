package com.megamarket.app.data.repository

import android.os.Build
import android.util.Log
import com.megamarket.app.BuildConfig
import com.megamarket.app.data.connectivity.ObservadorConexion
import com.megamarket.app.data.local.Administrador
import com.megamarket.app.data.local.SesionLocalStore
import com.megamarket.app.data.local.dao.AdministradorDao
import com.megamarket.app.data.mapper.toModel
import com.megamarket.app.data.remote.ApiService
import com.megamarket.app.data.remote.LoginRequest
import com.megamarket.modelo.ClaveAcceso
import com.megamarket.modelo.RolUsuario
import com.megamarket.modelo.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

class SesionRepository(
    private val administradorDao: AdministradorDao,
    private val sesionLocal: SesionLocalStore,
    private val api: ApiService,
    private val catalogoRemoto: CatalogoRemotoRepository,
    private val observadorConexion: ObservadorConexion
) {
    private val _usuario = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    suspend fun iniciarSesion(nombreUsuario: String, clave: String): ResultadoInicioSesion =
        withContext(Dispatchers.IO) {
            val usuarioTrim = nombreUsuario.trim()
            if (usuarioTrim.isBlank() || clave.isBlank()) {
                return@withContext ResultadoInicioSesion.Error("Completa usuario y contraseña")
            }

            val urlLogin = "${BuildConfig.MEGAMARKET_API_BASE_URL.trimEnd('/')}/api/auth/login"
            Log.i(TAG, "Login remoto: POST $urlLogin")

            try {
                val respuesta = api.login(
                    LoginRequest(
                        usuario = usuarioTrim,
                        clave = clave,
                        deviceName = nombreDispositivo()
                    )
                )
                val codigo = respuesta.code()
                Log.i(TAG, "Login remoto HTTP: $codigo")
                when (codigo) {
                    200 -> {
                        val cuerpo = respuesta.body()
                            ?: return@withContext ResultadoInicioSesion.Error("Respuesta de login inválida")
                        val remoto = cuerpo.usuario
                        if (!remoto.rol.equals(RolUsuario.ADMINISTRADOR.name, ignoreCase = true)) {
                            return@withContext ResultadoInicioSesion.Error(
                                "No tienes permiso para acceder a esta aplicación."
                            )
                        }
                        sesionLocal.guardarSesionRemota(
                            accessToken = cuerpo.accessToken,
                            usuarioRemotoId = remoto.id,
                            usuario = remoto.usuario,
                            rol = remoto.rol,
                            expiresAt = cuerpo.expiresAt
                        )
                        val local = guardarPerfilLocal(remoto.nombre, remoto.usuario, remoto.correo, clave)
                        val descarga = catalogoRemoto.descargarYGuardarCatalogo()
                        if (descarga is ResultadoDescargaCatalogo.Error &&
                            !catalogoRemoto.hayCatalogoLocal()
                        ) {
                            return@withContext ResultadoInicioSesion.Error(
                                "Error de configuración inicial: ${descarga.mensaje}"
                            )
                        }
                        local.toModel().also { _usuario.value = it }
                            .let { ResultadoInicioSesion.Exito(it) }
                    }
                    400 -> ResultadoInicioSesion.Error("Datos de inicio de sesión inválidos.")
                    401 -> ResultadoInicioSesion.Error("Usuario o contraseña incorrectos.")
                    403 -> ResultadoInicioSesion.Error(
                        "No tienes permiso para acceder a esta aplicación."
                    )
                    in 500..599 -> ResultadoInicioSesion.Error(
                        "El servidor de MegaMarket no está disponible temporalmente."
                    )
                    else -> ResultadoInicioSesion.Error(
                        "No se pudo iniciar sesión ($codigo)"
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Log.w(TAG, "Login remoto excepción: ${error.javaClass.simpleName}")
                manejarFalloDeRed(usuarioTrim, clave, error)
            } catch (error: Exception) {
                Log.w(TAG, "Login remoto excepción: ${error.javaClass.simpleName}")
                ResultadoInicioSesion.Error("No se pudo iniciar sesión")
            }
        }

    private suspend fun manejarFalloDeRed(
        usuarioTrim: String,
        clave: String,
        error: IOException
    ): ResultadoInicioSesion {
        if (sesionLocal.autenticacionRemotaPrevia) {
            return intentarOffline(usuarioTrim, clave)
        }
        val hayRed = observadorConexion.hayInternet()
        val sinHost = error is UnknownHostException
        val falloServidor = error is ConnectException ||
            error is SocketTimeoutException ||
            (hayRed && !sinHost)
        return if (falloServidor) {
            ResultadoInicioSesion.Error("No se pudo conectar con el servidor de MegaMarket.")
        } else {
            ResultadoInicioSesion.Error(
                "Se requiere conexión a Internet para realizar la configuración inicial."
            )
        }
    }

    private suspend fun intentarOffline(usuarioTrim: String, clave: String): ResultadoInicioSesion {
        val cuenta = administradorDao.buscarPorUsuario(usuarioTrim)
            ?: return ResultadoInicioSesion.Error(
                "Se requiere conexión a Internet para realizar la configuración inicial."
            )
        if (!ClaveAcceso.coincide(clave, cuenta.claveHash)) {
            return ResultadoInicioSesion.Error("Usuario o contraseña incorrectos.")
        }
        if (!sesionLocal.rol.equals(RolUsuario.ADMINISTRADOR.name, ignoreCase = true) &&
            sesionLocal.rol != null
        ) {
            return ResultadoInicioSesion.Error(
                "No tienes permiso para acceder a esta aplicación."
            )
        }
        if (!catalogoRemoto.hayCatalogoLocal() && !observadorConexion.hayInternet()) {
            return ResultadoInicioSesion.Error(
                "Se requiere conexión a Internet para realizar la configuración inicial."
            )
        }
        return cuenta.toModel().also { _usuario.value = it }.let { ResultadoInicioSesion.Exito(it) }
    }

    private suspend fun guardarPerfilLocal(
        nombre: String,
        usuario: String,
        correo: String,
        clave: String
    ): Administrador {
        val hash = ClaveAcceso.generar(clave)
        val existente = administradorDao.buscarPorUsuario(usuario)
        return if (existente == null) {
            val id = administradorDao.insertar(
                Administrador(
                    nombre = nombre,
                    usuario = usuario,
                    correo = correo,
                    claveHash = hash
                )
            )
            administradorDao.buscarPorUsuario(usuario)
                ?: Administrador(id = id, nombre = nombre, usuario = usuario, correo = correo, claveHash = hash)
        } else {
            val actualizado = existente.copy(
                nombre = nombre,
                correo = correo,
                claveHash = hash
            )
            administradorDao.actualizarAdministrador(actualizado)
            actualizado
        }
    }

    suspend fun cerrarSesion() {
        _usuario.value = null
        sesionLocal.limpiarToken()
    }

    private fun nombreDispositivo(): String =
        listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .ifBlank { "Android" }

    companion object {
        private const val TAG = "MegaMarketSesion"
    }
}

sealed class ResultadoInicioSesion {
    data class Exito(val usuario: Usuario) : ResultadoInicioSesion()
    data class Error(val mensaje: String) : ResultadoInicioSesion()
}
