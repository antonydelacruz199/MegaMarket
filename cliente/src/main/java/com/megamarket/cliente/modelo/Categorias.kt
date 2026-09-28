package com.megamarket.cliente.modelo

fun nombreCategoria(categoriaId: Long): String = when (categoriaId) {
    1L -> "Abarrotes"
    2L -> "Lácteos"
    3L -> "Bebidas"
    4L -> "Limpieza"
    5L -> "Snacks"
    6L -> "Cuidado personal"
    7L -> "Frutas y verduras"
    else -> "Categoría $categoriaId"
}
