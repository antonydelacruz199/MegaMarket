package com.megamarket.cliente.model

import com.megamarket.modelo.Categoria

fun nombreCategoria(categorias: List<Categoria>, categoriaId: Long): String =
    categorias.firstOrNull { it.id == categoriaId }?.nombre ?: "Categoría"
