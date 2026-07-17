package com.aicouples.therapy.ai.memory

import com.aicouples.therapy.ai.OpenAiClient
import com.aicouples.therapy.ai.PromptOrchestrator
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.TherapeuticMemory
import com.aicouples.therapy.data.repository.MemoryRepository
import com.aicouples.therapy.data.repository.SessionRepository
import com.aicouples.therapy.util.AppJson
import com.aicouples.therapy.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryEngine @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val sessionRepository: SessionRepository,
    private val openAiClient: OpenAiClient,
    private val promptOrchestrator: PromptOrchestrator,
) {
    suspend fun loadOrDefault(relationshipId: String): TherapeuticMemory {
        return memoryRepository.loadCurrentMemory(relationshipId)?.memoryJson
            ?: TherapeuticMemory(
                relationshipSummary = "New couple beginning therapy.",
                nextSessionFocus = "Build rapport and understand each partner's hopes for therapy.",
            )
    }

    suspend fun generateHandoff(
        relationshipId: String,
        sessionMessages: List<ChatMessage>,
        partnerAName: String,
        partnerBName: String,
    ): TherapeuticMemory {
        val current = loadOrDefault(relationshipId)
        val completion = openAiClient.complete(
            messages = promptOrchestrator.buildHandoffMessages(
                memory = current,
                sessionMessages = sessionMessages,
                partnerAName = partnerAName,
                partnerBName = partnerBName,
            ),
            jsonMode = true,
            temperature = 0.4,
        )
        val updated = AppJson.decodeFromString<TherapeuticMemory>(completion.text)
        val sessionsIncluded = current.sessionsIncluded + 1
        val withCount = updated.copy(sessionsIncluded = sessionsIncluded)

        memoryRepository.saveNewMemoryVersion(
            relationshipId = relationshipId,
            memory = withCount,
            sessionsIncluded = sessionsIncluded,
        )

        maybeCompress(relationshipId, withCount)
        return withCount
    }

    private suspend fun maybeCompress(relationshipId: String, memory: TherapeuticMemory) {
        if (memory.sessionsIncluded <= 0) return
        if (memory.sessionsIncluded % Constants.SESSIONS_PER_MEMORY_BLOCK != 0) return

        val completion = openAiClient.complete(
            messages = promptOrchestrator.buildCompressionMessages(memory),
            jsonMode = true,
            temperature = 0.3,
        )
        val archiveSummary = AppJson.decodeFromString<TherapeuticMemory>(completion.text)
        val to = memory.sessionsIncluded
        val from = to - Constants.SESSIONS_PER_MEMORY_BLOCK + 1

        memoryRepository.archiveMemory(
            relationshipId = relationshipId,
            summary = archiveSummary,
            sessionsFrom = from,
            sessionsTo = to,
        )

        // Reset rolling window marker while preserving continuity themes from archive.
        val rolled = archiveSummary.copy(
            sessionsIncluded = 0,
            relationshipSummary = archiveSummary.relationshipSummary,
            nextSessionFocus = archiveSummary.nextSessionFocus,
        )
        memoryRepository.saveNewMemoryVersion(
            relationshipId = relationshipId,
            memory = rolled,
            sessionsIncluded = 0,
        )
    }
}
