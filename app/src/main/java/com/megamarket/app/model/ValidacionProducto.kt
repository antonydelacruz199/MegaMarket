package com.megamarket.app.model

import com.megamarket.app.model.estado.ProductoFormUiState
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
        val textoCategoria = formulario.categoria.trim()
        val categoria = if (textoCategoria.isEmpty()) 0L else textoCategoria.toLongOrNull()
        val oferta = formulario.precioOferta.aCentimos()

        val mensaje = when {
            nombre.isEmpty() -> "Ingresa el nombre del producto"
            marca.isEmpty() -> "Ingresa la marca del producto"
            categoria == null || categoria < 0 -> "La categoría debe ser un número entero positivo"
            precio == null -> "Ingresa un precio válido"
            precio <= 0L -> "El precio debe ser mayor a 0"
            stock == null -> "El stock debe ser un número entero"
            stock < 0 -> "El stock no puede ser negativo"
            formulario.esOferta && formulario.precioOferta.isBlank() -> "Ingresa el precio de oferta"
            formulario.esOferta && oferta == null -> "Ingresa un precio de oferta válido"
            formulario.esOferta && oferta != null && oferta <= 0L -> "El precio de oferta debe ser mayor a 0"
            formulario.esOferta && oferta != null && oferta >= precio -> "La oferta debe ser menor al precio"
            else -> null
        }
        if (mensaje != null) return Resultado.Invalido(mensaje)

        return Resultado.Valido(
            Producto(
                id = formulario.id,
                nombre = nombre,
                marca = marca,
                descripcion = formulario.descripcion.trim(),
                categoriaId = checkNotNull(categoria),
                precioCentimos = checkNotNull(precio),
                precioOfertaCentimos = if (formulario.esOferta) oferta else null,
                stock = checkNotNull(stock),
                imagenKey = formulario.imagenVisibleKey,
                esOferta = formulario.esOferta,
                activo = formulario.activo
            )
        )
    }
}

/** Convierte el texto en soles ("4.80" o "4,80") a céntimos sin pasar por Double. */
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
