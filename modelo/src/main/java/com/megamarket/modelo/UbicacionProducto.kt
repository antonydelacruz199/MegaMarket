package com.megamarket.modelo

/**
 * Código de barras y pasillo opcionales.
 * Vacío se guarda como null para convivir con productos antiguos y con el
 * índice único parcial de `codigo_barras`.
 */
object UbicacionProducto {
    const val CODIGO_MAX = 32
    const val PASILLO_MAX = 40

    private val CODIGO = Regex("^[0-9A-Za-z]{1,$CODIGO_MAX}$")
    private val PASILLO = Regex(
        "^[0-9A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+(?:[ -][0-9A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*$"
    )

    sealed interface Resultado {
        data class Ok(val valor: String?) : Resultado
        data class Error(val mensaje: String) : Resultado
    }

    fun normalizarCodigo(valor: String?): Resultado {
        val codigo = valor?.trim().orEmpty()
        if (codigo.isEmpty()) return Resultado.Ok(null)
        if (codigo.length > CODIGO_MAX) {
            return Resultado.Error("El código de barras admite como máximo $CODIGO_MAX caracteres")
        }
        if (!CODIGO.matches(codigo)) {
            return Resultado.Error("El código de barras solo admite letras y dígitos")
        }
        return Resultado.Ok(codigo)
    }

    fun normalizarPasillo(valor: String?): Resultado {
        val pasillo = valor?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (pasillo.isEmpty()) return Resultado.Ok(null)
        if (pasillo.length > PASILLO_MAX) {
            return Resultado.Error("El pasillo admite como máximo $PASILLO_MAX caracteres")
        }
        if (!PASILLO.matches(pasillo)) {
            return Resultado.Error("El pasillo solo admite letras, números, espacios y guiones")
        }
        return Resultado.Ok(pasillo)
    }
}
