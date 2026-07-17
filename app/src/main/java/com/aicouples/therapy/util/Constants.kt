package com.aicouples.therapy.util

object Constants {
    const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
    const val RECENT_MESSAGES_FOR_PROMPT = 24
    const val SESSIONS_PER_MEMORY_BLOCK = 5
    const val DEFAULT_AI_MODEL = "gpt-4o-mini"
    const val OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions"
    const val TYPING_INDICATOR_MIN_MS = 800L
}
