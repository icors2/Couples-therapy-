package com.aicouples.therapy.therapy.memory

/**
 * Mirrors the server-side rolling window used by `generate-memory`.
 * Full session transcripts remain in `messages`; only structured memory rolls.
 */
object MemoryPolicy {
    const val ROLLING_SESSION_WINDOW = 5
}
