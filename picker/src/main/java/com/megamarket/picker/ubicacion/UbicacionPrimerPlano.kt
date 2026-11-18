package com.megamarket.picker.ubicacion

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.megamarket.picker.data.repository.Coordenadas
import com.megamarket.picker.data.repository.gpsValido
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Orden de lectura en primer plano. Sin permiso fino no se consulta el GPS:
 * en Android 12+ el usuario puede conceder solo la ubicación aproximada.
 */
fun proveedoresLectura(permisoFino: Boolean, gpsActivo: Boolean, redActiva: Boolean): List<String> {
    val proveedores = ArrayList<String>(2)
    if (permisoFino && gpsActivo) proveedores += LocationManager.GPS_PROVIDER
    if (redActiva) proveedores += LocationManager.NETWORK_PROVIDER
    return proveedores
}

/**
 * Lectura de ubicación en primer plano, mientras la pantalla de entrega está abierta.
 * Prefiere GPS y no pide acceso en segundo plano.
 */
class UbicacionPrimerPlano(private val contexto: Context) {

    fun permisoConcedido(): Boolean {
        val fino = ContextCompat.checkSelfPermission(
            contexto,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val grueso = ContextCompat.checkSelfPermission(
            contexto,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fino || grueso
    }

    suspend fun leer(): Coordenadas? {
        if (!permisoConcedido()) return null
        val manager = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val proveedores = proveedoresLectura(
            permisoFino = permisoFino(),
            gpsActivo = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
                .getOrDefault(false),
            redActiva = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
                .getOrDefault(false)
        )
        for (proveedor in proveedores) {
            val punto = withTimeoutOrNull(12_000) { unaLectura(manager, proveedor) }
                ?: lecturaConocida(manager, proveedor)
            if (punto != null && gpsValido(punto.latitude, punto.longitude)) {
                return Coordenadas(punto.latitude, punto.longitude)
            }
        }
        return null
    }

    private fun permisoFino(): Boolean =
        ContextCompat.checkSelfPermission(
            contexto,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lecturaConocida(manager: LocationManager, proveedor: String): Location? =
        runCatching { manager.getLastKnownLocation(proveedor) }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun unaLectura(manager: LocationManager, proveedor: String): Location? =
        suspendCancellableCoroutine { cont ->
            val oyente = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Compatibilidad con API 24")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                override fun onProviderDisabled(provider: String) {
                    runCatching { manager.removeUpdates(this) }
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(oyente) } }
            val abierto = runCatching {
                manager.requestLocationUpdates(proveedor, 0L, 0f, oyente, Looper.getMainLooper())
            }
            if (abierto.isFailure && cont.isActive) cont.resume(null)
        }
}
