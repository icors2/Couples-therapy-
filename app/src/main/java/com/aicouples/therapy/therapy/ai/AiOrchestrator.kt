package com.aicouples.therapy.therapy.ai

import com.aicouples.therapy.data.model.TherapeuticMemoryDocument
import com.aicouples.therapy.therapy.prompts.TherapistPrompts
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds prompt context documents for debugging / offline preview.
 * Production AI calls always go through Edge Functions so API keys stay server-side.
 */
@Singleton
class AiOrchestrator @Inject constructor(
    private val json: Json,
) {
    fun buildSessionSystemPrompt(
        memory: TherapeuticMemoryDocument?,
        isFirstSession: Boolean,
    ): String = buildString {
        appendLine(TherapistPrompts.SYSTEM_ROLE.trimIndent())
        appendLine()
        if (isFirstSession || memory == null) {
            appendLine("This appears to be an early session. Establish safety and rapport.")
        } else {
            appendLine("You are continuing therapy with this couple.")
            appendLine("Review this therapeutic memory and maintain continuity.")
            appendLine("Do not repeat previous interventions unless appropriate.")
            appendLine()
            appendLine(json.encodeToString(memory))
        }
    }
}
