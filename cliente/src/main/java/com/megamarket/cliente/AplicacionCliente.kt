package com.megamarket.cliente

import android.app.Application
import com.megamarket.cliente.data.local.BaseDatosCliente
import com.megamarket.cliente.data.repositorio.RepositorioCarrito
import com.megamarket.cliente.data.repositorio.RepositorioCatalogo
import com.megamarket.cliente.data.repositorio.RepositorioCompra
import com.megamarket.cliente.data.repositorio.RepositorioFavoritos
import com.megamarket.cliente.data.repositorio.RepositorioSesion

class AplicacionCliente : Application() {
    lateinit var contenedor: ContenedorCliente
        private set

    override fun onCreate() {
        super.onCreate()
        val base = BaseDatosCliente.obtener(this)
        val catalogo = RepositorioCatalogo(contentResolver)
        val carrito = RepositorioCarrito(base.carritoDao(), catalogo)
        contenedor = ContenedorCliente(
            repositorioSesion = RepositorioSesion(base.clienteDao()),
            repositorioCatalogo = catalogo,
            repositorioCarrito = carrito,
            repositorioFavoritos = RepositorioFavoritos(base.favoritoDao(), catalogo),
            repositorioCompra = RepositorioCompra(carrito)
        )
    }
}

data class ContenedorCliente(
    val repositorioSesion: RepositorioSesion,
    val repositorioCatalogo: RepositorioCatalogo,
    val repositorioCarrito: RepositorioCarrito,
    val repositorioFavoritos: RepositorioFavoritos,
    val repositorioCompra: RepositorioCompra
)
