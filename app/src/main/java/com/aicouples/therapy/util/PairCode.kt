package com.aicouples.therapy.util

object PairCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun normalize(input: String): String =
        input.trim().uppercase().filter { it in ALPHABET }

    fun isValid(code: String): Boolean =
        normalize(code).length == 6
}
