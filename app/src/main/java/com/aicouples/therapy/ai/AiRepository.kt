package com.aicouples.therapy.ai

import com.aicouples.therapy.ai.memory.MemoryEngine
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.TherapeuticMemory
import com.aicouples.therapy.data.repository.SessionRepository
import com.aicouples.therapy.util.Constants
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class AiRepository @Inject constructor(
    private val openAiClient: OpenAiClient,
    private val promptOrchestrator: PromptOrchestrator,
    private val memoryEngine: MemoryEngine,
    private val sessionRepository: SessionRepository,
) {
    suspend fun maybeRespond(
        relationshipId: String,
        sessionId: String,
        sessionMessages: List<ChatMessage>,
        partnerAName: String,
        partnerBName: String,
        completedSessionCount: Int,
    ): ChatMessage? {
        if (!promptOrchestrator.shouldAiRespond(sessionMessages)) return null

        val memory = memoryEngine.loadOrDefault(relationshipId)
        delay(Constants.TYPING_INDICATOR_MIN_MS)

        val completion = openAiClient.complete(
            messages = promptOrchestrator.buildTherapyMessages(
                memory = memory,
                sessionMessages = sessionMessages,
                partnerAName = partnerAName,
                partnerBName = partnerBName,
                isFirstSession = completedSessionCount == 0,
            ),
            temperature = 0.7,
        )

        if (completion.text.trim() == "[WAIT]") return null

        return sessionRepository.sendAiMessage(
            sessionId = sessionId,
            content = completion.text,
            tokens = completion.tokens,
            model = completion.model,
        )
    }

    suspend fun generateSessionHandoff(
        relationshipId: String,
        sessionMessages: List<ChatMessage>,
        partnerAName: String,
        partnerBName: String,
    ): TherapeuticMemory = memoryEngine.generateHandoff(
        relationshipId = relationshipId,
        sessionMessages = sessionMessages,
        partnerAName = partnerAName,
        partnerBName = partnerBName,
    )

    suspend fun hasApiKey(): Boolean = openAiClient.resolveApiKey() != null
}
