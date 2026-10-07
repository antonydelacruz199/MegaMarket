package com.megamarket.cliente.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class EstadoConectividad {
    CONNECTED,
    DISCONNECTED
}

/**
 * Observa conectividad de red.
 * CONNECTED ≠ servidor disponible: la autoridad sigue siendo HTTP.
 */
class ConnectivityObserver(contexto: Context) {

    private val manager =
        contexto.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observar(): Flow<EstadoConectividad> = callbackFlow {
        val emitir = {
            trySend(if (hayRed()) EstadoConectividad.CONNECTED else EstadoConectividad.DISCONNECTED)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emitir()
            }

            override fun onLost(network: Network) {
                emitir()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                emitir()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        emitir()
        manager.registerNetworkCallback(request, callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun hayRed(): Boolean {
        val red = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(red) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
