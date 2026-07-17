package com.aicouples.therapy.common

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

inline fun <T> runCatchingResult(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (t: Throwable) {
    Result.Error(t.message ?: "Unexpected error", t)
}

suspend inline fun <T> runCatchingResultSuspend(block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (t: Throwable) {
    Result.Error(t.message ?: "Unexpected error", t)
}
