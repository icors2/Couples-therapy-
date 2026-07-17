package com.aicouples.therapy.ai

import com.aicouples.therapy.ai.prompts.TherapistPrompts
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.MessageSender
import com.aicouples.therapy.data.model.OpenAiMessage
import com.aicouples.therapy.data.model.TherapeuticMemory
import com.aicouples.therapy.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptOrchestrator @Inject constructor() {

    fun buildTherapyMessages(
        memory: TherapeuticMemory,
        sessionMessages: List<ChatMessage>,
        partnerAName: String,
        partnerBName: String,
        isFirstSession: Boolean,
    ): List<OpenAiMessage> {
        val recent = sessionMessages
            .filter { it.senderRole != MessageSender.SYSTEM }
            .takeLast(Constants.RECENT_MESSAGES_FOR_PROMPT)

        val transcript = recent.joinToString("\n") { msg ->
            val who = when (msg.senderRole) {
                MessageSender.PARTNER_A -> partnerAName
                MessageSender.PARTNER_B -> partnerBName
                MessageSender.AI -> "Therapist"
                MessageSender.SYSTEM -> "System"
            }
            "$who: ${msg.content}"
        }

        return listOf(
            OpenAiMessage("system", TherapistPrompts.SYSTEM_THERAPIST),
            OpenAiMessage("system", TherapistPrompts.memoryContext(memory, isFirstSession)),
            OpenAiMessage("system", TherapistPrompts.responseInstruction(partnerAName, partnerBName)),
            OpenAiMessage(
                "user",
                """
Current session transcript (most recent messages):
$transcript

Respond now as the therapist if an intervention is appropriate. If silence would be better because a partner just asked the other a direct question and they have not answered yet, reply with exactly: [WAIT]
""".trimIndent()
            ),
        )
    }

    fun buildHandoffMessages(
        memory: TherapeuticMemory,
        sessionMessages: List<ChatMessage>,
        partnerAName: String,
        partnerBName: String,
    ): List<OpenAiMessage> {
        val transcript = sessionMessages.joinToString("\n") { msg ->
            val who = when (msg.senderRole) {
                MessageSender.PARTNER_A -> partnerAName
                MessageSender.PARTNER_B -> partnerBName
                MessageSender.AI -> "Therapist"
                MessageSender.SYSTEM -> "System"
            }
            "$who: ${msg.content}"
        }
        return listOf(
            OpenAiMessage("system", TherapistPrompts.SYSTEM_THERAPIST),
            OpenAiMessage("system", TherapistPrompts.memoryContext(memory, isFirstSession = false)),
            OpenAiMessage("system", TherapistPrompts.handoffInstruction()),
            OpenAiMessage(
                "user",
                """
Previous memory sessions_included=${memory.sessionsIncluded}

Full session transcript:
$transcript
""".trimIndent()
            ),
        )
    }

    fun buildCompressionMessages(memory: TherapeuticMemory): List<OpenAiMessage> =
        listOf(
            OpenAiMessage("system", TherapistPrompts.compressionInstruction()),
            OpenAiMessage("user", TherapistPrompts.memoryContext(memory, isFirstSession = false)),
        )

    /**
     * Heuristic: AI should respond after both partners have spoken since the last AI message,
     * or after a direct question to the therapist, or after a long pause in turn-taking.
     */
    fun shouldAiRespond(messages: List<ChatMessage>): Boolean {
        val meaningful = messages.filter {
            it.senderRole == MessageSender.PARTNER_A ||
                it.senderRole == MessageSender.PARTNER_B ||
                it.senderRole == MessageSender.AI
        }
        if (meaningful.isEmpty()) return false

        val last = meaningful.last()
        if (last.senderRole == MessageSender.AI) return false

        val sinceAi = meaningful.takeLastWhile { it.senderRole != MessageSender.AI }
        val partnerMessages = sinceAi.filter {
            it.senderRole == MessageSender.PARTNER_A || it.senderRole == MessageSender.PARTNER_B
        }
        if (partnerMessages.isEmpty()) return false

        val addressedTherapist = partnerMessages.any {
            it.content.contains("therapist", ignoreCase = true) ||
                it.content.contains("@ai", ignoreCase = true) ||
                it.content.contains("what do you think", ignoreCase = true)
        }
        if (addressedTherapist) return true

        val roles = partnerMessages.map { it.senderRole }.toSet()
        if (roles.size >= 2) return true

        // Single partner sent 2+ messages without reply from the other or AI
        return partnerMessages.size >= 2
    }
}
