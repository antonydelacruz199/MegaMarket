package com.megamarket.cliente.data.connectivity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.URI

private const val TAG = "MegaMarketRedLocal"
/** android.permission.ACCESS_LOCAL_NETWORK (API 37+). */
const val PERMISO_RED_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
const val MENSAJE_PERMISO_RED_LOCAL =
    "Se necesita permiso de red local para conectarse al servidor de desarrollo de MegaMarket."

/** Hosts / rangos usados por API REST de desarrollo (emulador y LAN). */
fun esUrlRedLocal(baseUrl: String): Boolean {
    val host = try {
        URI(baseUrl.trim()).host?.lowercase()?.trim('.').orEmpty()
    } catch (_: Exception) {
        return false
    }
    if (host.isEmpty()) return false
    if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") return true
    val partes = host.split('.')
    if (partes.size != 4) return false
    val octetos = partes.mapNotNull { it.toIntOrNull() }
    if (octetos.size != 4 || octetos.any { it !in 0..255 }) return false
    val (a, b, _, _) = octetos
    return when {
        a == 10 -> true
        a == 192 && b == 168 -> true
        a == 172 && b in 16..31 -> true
        else -> false
    }
}

fun requierePermisoRedLocal(baseUrl: String): Boolean =
    Build.VERSION.SDK_INT >= 37 && esUrlRedLocal(baseUrl)

fun tienePermisoRedLocal(contexto: Context): Boolean {
    if (Build.VERSION.SDK_INT < 37) return true
    return ContextCompat.checkSelfPermission(contexto, PERMISO_RED_LOCAL) ==
        PackageManager.PERMISSION_GRANTED
}

fun registrarEstadoPermisoRedLocal(concedido: Boolean) {
    Log.i(TAG, "ACCESS_LOCAL_NETWORK concedido=$concedido sdk=${Build.VERSION.SDK_INT}")
}
