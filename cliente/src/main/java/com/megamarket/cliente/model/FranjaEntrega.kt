package com.megamarket.cliente.model

import com.megamarket.cliente.data.remote.FranjaApiDto
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class ModalidadPagoCheckout(
    val valorApi: String,
    val titulo: String,
    val descripcion: String
) {
    ONLINE(
        "ONLINE",
        "Pago en línea",
        "Después de confirmar el pedido pagarás en línea. El pago no se confirma solo al mostrar un código QR."
    ),
    CONTRAENTREGA(
        "CONTRAENTREGA",
        "Contraentrega",
        "El pedido se prepara con pago pendiente. Pagarás con el repartidor antes de cerrar la entrega."
    );

    companion object {
        fun desdeApi(valor: String?): ModalidadPagoCheckout? =
            entries.firstOrNull { it.valorApi.equals(valor?.trim(), ignoreCase = true) }
    }
}

data class FranjaEntrega(
    val id: String,
    val fechaIso: String,
    val horaInicio: String,
    val horaFin: String,
    val capacidad: Int,
    val ocupados: Int,
    val cupoRestante: Int,
    val activa: Boolean
) {
    val disponible: Boolean
        get() = activa && cupoRestante > 0

    val etiquetaFecha: String
        get() = formatearFecha(fechaIso)

    val etiquetaHorario: String
        get() = "${formatearHora(horaInicio)} – ${formatearHora(horaFin)}"

    val etiquetaCompleta: String
        get() = "$etiquetaFecha · $etiquetaHorario"

    val etiquetaCupo: String
        get() = when {
            !activa -> "No disponible"
            cupoRestante <= 0 -> "Agotada"
            else -> "$cupoRestante de $capacidad cupos"
        }

    companion object {
        private val localeEs = Locale.Builder().setLanguage("es").setRegion("PE").build()

        fun desdeDto(dto: FranjaApiDto): FranjaEntrega? {
            val id = dto.id?.trim().orEmpty()
            if (id.isEmpty()) return null
            val fecha = normalizarFecha(dto.fecha) ?: return null
            val inicio = normalizarHora(dto.horaInicio) ?: return null
            val fin = normalizarHora(dto.horaFin) ?: return null
            val capacidad = (dto.capacidad ?: 0).coerceAtLeast(0)
            val ocupados = (dto.ocupados ?: 0).coerceAtLeast(0)
            val restante = dto.cupoRestante?.coerceAtLeast(0)
                ?: (capacidad - ocupados).coerceAtLeast(0)
            return FranjaEntrega(
                id = id,
                fechaIso = fecha,
                horaInicio = inicio,
                horaFin = fin,
                capacidad = capacidad,
                ocupados = ocupados,
                cupoRestante = restante,
                activa = dto.activa != false
            )
        }

        fun etiquetaDesdeCampos(fechaIso: String?, horaInicio: String?, horaFin: String?): String? {
            val fecha = fechaIso?.trim().orEmpty()
            val inicio = horaInicio?.trim().orEmpty()
            val fin = horaFin?.trim().orEmpty()
            if (fecha.isEmpty() || inicio.isEmpty() || fin.isEmpty()) return null
            return "${formatearFecha(fecha)} · ${formatearHora(inicio)} – ${formatearHora(fin)}"
        }

        fun formatearFecha(crudo: String): String {
            val iso = normalizarFecha(crudo) ?: return crudo.take(10)
            val parser = SimpleDateFormat("yyyy-MM-dd", localeEs).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val salida = SimpleDateFormat("EEE d MMM", localeEs).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return try {
                val fecha = parser.parse(iso) ?: return iso
                salida.format(fecha).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(localeEs) else it.toString()
                }
            } catch (_: ParseException) {
                iso
            }
        }

        fun formatearHora(crudo: String): String {
            val normalizada = normalizarHora(crudo) ?: return crudo.take(5)
            val partes = normalizada.split(':')
            val hora24 = partes[0].toIntOrNull() ?: return normalizada.take(5)
            val minuto = partes.getOrNull(1)?.take(2)?.padStart(2, '0') ?: "00"
            val periodo = if (hora24 < 12) "a.m." else "p.m."
            val hora12 = when {
                hora24 == 0 -> 12
                hora24 > 12 -> hora24 - 12
                else -> hora24
            }
            return "$hora12:$minuto $periodo"
        }

        fun normalizarFecha(crudo: String?): String? {
            val texto = crudo?.trim().orEmpty()
            if (texto.isEmpty()) return null
            val base = texto.substringBefore('T').take(10)
            return if (base.length == 10 && base[4] == '-' && base[7] == '-') base else null
        }

        fun normalizarHora(crudo: String?): String? {
            val texto = crudo?.trim().orEmpty()
            if (texto.isEmpty()) return null
            val base = texto.substringBefore('.').let { if (it.length == 5) "$it:00" else it }
            if (base.length < 5 || base[2] != ':') return null
            return base.take(8)
        }
    }
}
