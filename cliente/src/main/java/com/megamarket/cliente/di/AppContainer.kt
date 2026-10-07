package com.megamarket.cliente.di

import android.content.Context
import com.megamarket.cliente.data.local.AppDatabase
import com.megamarket.cliente.data.repository.AuthRepository
import com.megamarket.cliente.data.repository.CarritoRepository
import com.megamarket.cliente.data.repository.CatalogoRepository
import com.megamarket.cliente.data.repository.FavoritosRepository
import com.megamarket.cliente.data.repository.PedidoRepository

class AppContainer(context: Context) {

    private val database = AppDatabase.getInstance(context)

    val authRepository = AuthRepository(database.clienteDao())

    val catalogoRepository = CatalogoRepository(context.contentResolver)

    val carritoRepository = CarritoRepository(
        database.carritoDao(),
        catalogoRepository
    )

    val favoritosRepository = FavoritosRepository(
        database.favoritoDao(),
        catalogoRepository
    )

    val pedidoRepository = PedidoRepository(
        database,
        database.pedidoDao(),
        database.carritoDao(),
        catalogoRepository
    )
}
