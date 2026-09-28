package com.megamarket.app.data.local

import com.megamarket.modelo.Producto

fun Producto.aEntidad(): EntidadProducto = EntidadProducto(
    id = id,
    nombre = nombre,
    marca = marca,
    descripcion = descripcion,
    categoriaId = categoriaId,
    precioCentimos = precioCentimos,
    precioOfertaCentimos = precioOfertaCentimos,
    stock = stock,
    imagenKey = imagenKey,
    esOferta = esOferta,
    activo = activo
)

fun EntidadProducto.aProducto(): Producto = Producto(
    id = id,
    nombre = nombre,
    marca = marca,
    descripcion = descripcion,
    categoriaId = categoriaId,
    precioCentimos = precioCentimos,
    precioOfertaCentimos = precioOfertaCentimos,
    stock = stock,
    imagenKey = imagenKey,
    esOferta = esOferta,
    activo = activo
)
