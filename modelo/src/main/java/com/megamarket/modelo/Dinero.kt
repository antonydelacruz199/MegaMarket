package com.megamarket.modelo

import kotlin.math.abs

fun Long.formatearSoles(): String {
    val signo = if (this < 0) "-" else ""
    val absoluto = abs(this)
    val soles = absoluto / 100
    val centimos = absoluto % 100
    return "${signo}S/ $soles.${centimos.toString().padStart(2, '0')}"
}
