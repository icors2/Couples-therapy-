package com.aicouples.therapy.common

object PairCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun normalize(raw: String): String =
        raw.trim().uppercase().filter { it in ALPHABET }

    fun isValid(code: String): Boolean =
        normalize(code).length == 6
}
