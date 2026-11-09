package com.megamarket.cliente.model

object FrescuraCatalogo {
    const val LIMITE_MS = 60L * 60 * 1000
    fun vigente(ultima: Long?, ahora: Long): Boolean = ultima != null && ahora >= ultima && ahora-ultima <= LIMITE_MS
    fun vigenteConReloj(ultima: Long?, ahora: Long, elapsed: Long?, elapsedAhora: Long, boot: Int?, bootAhora: Int): Boolean =
        vigente(ultima,ahora) && elapsed != null && boot != null && boot>=0 && boot==bootAhora &&
            elapsedAhora>=elapsed && elapsedAhora-elapsed<=LIMITE_MS
}

