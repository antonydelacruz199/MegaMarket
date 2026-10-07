package com.megamarket.app.di

import android.content.Context
import com.megamarket.app.data.local.AppDatabase
import com.megamarket.app.data.repository.AuthRepository
import com.megamarket.app.data.repository.ImagenRepository
import com.megamarket.app.data.repository.ProductoRepository

class AppContainer(context: Context) {

    private val database = AppDatabase.getInstance(context)

    val authRepository = AuthRepository(database.administradorDao())

    val productoRepository = ProductoRepository(
        database.productoDao(),
        context.contentResolver
    )

    val imagenRepository = ImagenRepository(context)
}
