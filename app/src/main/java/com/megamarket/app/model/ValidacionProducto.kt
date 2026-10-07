package com.megamarket.app.model

import com.megamarket.app.model.estado.ProductoFormUiState
import com.megamarket.modelo.PrecioDescuento
import com.megamarket.modelo.Producto
import java.math.RoundingMode

/** Reglas del formulario de producto. Sin dependencias de Android para poder probarlas. */
object ValidacionProducto {

    sealed interface Resultado {
        data class Valido(val producto: Producto) : Resultado
        data class Invalido(val mensaje: String) : Resultado
    }

    fun validar(formulario: ProductoFormUiState): Resultado {
        val nombre = formulario.nombre.trim()
        val marca = formulario.marca.trim()
        val precio = formulario.precio.aCentimos()
        val stock = formulario.stock.trim().toIntOrNull()
        val categoriaId = formulario.categoriaId
        val descuento = formulario.descuentoPorcentaje.trim().toIntOrNull()

        val mensaje = when {
            nombre.isEmpty() -> "Ingresa el nombre del producto"
            marca.isEmpty() -> "Ingresa la marca del producto"
            categoriaId <= 0L -> "Selecciona una categoría"
            precio == null -> "Ingresa un precio válido"
            precio <= 0L -> "El precio debe ser mayor a 0"
            stock == null -> "El stock debe ser un número entero"
            stock < 0 -> "El stock no puede ser negativo"
            formulario.esOferta && formulario.descuentoPorcentaje.isBlank() ->
                "Ingresa el porcentaje de descuento"
            formulario.esOferta && descuento == null ->
                "Ingresa un porcentaje de descuento válido"
            formulario.esOferta && descuento != null && !PrecioDescuento.esDescuentoValido(descuento) ->
                "El descuento debe estar entre 0 y 100"
            formulario.esOferta && descuento != null && descuento == 0 ->
                "El descuento debe ser mayor a 0 cuando hay oferta"
            formulario.esOferta && descuento != null && descuento >= 100 ->
                "El descuento debe ser menor a 100"
            else -> null
        }
        if (mensaje != null) return Resultado.Invalido(mensaje)

        val descuentoFinal = if (formulario.esOferta) checkNotNull(descuento) else 0
        val precioOferta = PrecioDescuento.precioOfertaDesdeDescuento(
            checkNotNull(precio),
            descuentoFinal
        )

        return Resultado.Valido(
            Producto(
                id = formulario.id,
                nombre = nombre,
                marca = marca,
                descripcion = formulario.descripcion.trim(),
                categoriaId = categoriaId,
                precioCentimos = checkNotNull(precio),
                precioOfertaCentimos = precioOferta,
                descuentoPorcentaje = descuentoFinal,
                stock = checkNotNull(stock),
                imagenKey = formulario.imagenVisibleKey,
                esOferta = formulario.esOferta,
                activo = formulario.activo,
                remoteId = formulario.remoteId,
                remoteVersion = formulario.remoteVersion,
                remoteUpdatedAt = formulario.remoteUpdatedAt,
                remoteDeletedAt = formulario.remoteDeletedAt
            )
        )
    }
}

fun String.aCentimos(): Long? {
    val valor = trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (valor.signum() < 0) return null
    return runCatching {
        valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()
}

fun Long.aTextoDecimal(): String {
    val soles = this / 100
    val centimos = (this % 100).toString().padStart(2, '0')
    return "$soles.$centimos"
}
