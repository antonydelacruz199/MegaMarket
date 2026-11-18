package com.megamarket.picker.camera

enum class MotivoManual {
    NINGUNO,
    SIN_CAMARA,
    PERMISO_DENEGADO,
    ELECCION
}

data class EstadoCamaraUi(
    val camaraActiva: Boolean = false,
    val entradaManual: Boolean = false,
    val pedirPermiso: Boolean = false,
    val decidido: Boolean = false,
    val motivo: MotivoManual = MotivoManual.NINGUNO
)

fun estadoAlAbrir(tieneCamara: Boolean, permisoConcedido: Boolean): EstadoCamaraUi = when {
    !tieneCamara -> EstadoCamaraUi(
        camaraActiva = false,
        entradaManual = true,
        pedirPermiso = false,
        decidido = true,
        motivo = MotivoManual.SIN_CAMARA
    )
    permisoConcedido -> EstadoCamaraUi(
        camaraActiva = true,
        entradaManual = false,
        pedirPermiso = false,
        decidido = true
    )
    else -> EstadoCamaraUi(
        camaraActiva = false,
        entradaManual = false,
        pedirPermiso = true,
        decidido = true
    )
}

fun estadoTrasPermiso(concedido: Boolean): EstadoCamaraUi = if (concedido) {
    EstadoCamaraUi(camaraActiva = true, entradaManual = false, pedirPermiso = false, decidido = true)
} else {
    EstadoCamaraUi(
        camaraActiva = false,
        entradaManual = true,
        pedirPermiso = false,
        decidido = true,
        motivo = MotivoManual.PERMISO_DENEGADO
    )
}

fun estadoManual(): EstadoCamaraUi = EstadoCamaraUi(
    camaraActiva = false,
    entradaManual = true,
    pedirPermiso = false,
    decidido = true,
    motivo = MotivoManual.ELECCION
)
