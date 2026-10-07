package com.megamarket.app.data.mapper

import com.megamarket.app.data.local.entities.ProductoEntity
import com.megamarket.modelo.Producto

fun Producto.toEntity(): ProductoEntity = ProductoEntity(
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

fun ProductoEntity.toModel(): Producto = Producto(
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
