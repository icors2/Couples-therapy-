package com.aicouples.therapy

import com.aicouples.therapy.data.model.TherapeuticMemoryDocument
import com.aicouples.therapy.therapy.ai.AiOrchestrator
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class AiOrchestratorTest {
    private val orchestrator = AiOrchestrator(Json { encodeDefaults = true })

    @Test
    fun buildSessionSystemPrompt_includes_memory_for_continuing_sessions() {
        val memory = TherapeuticMemoryDocument(
            relationshipSummary = "Working on listening",
            nextSessionFocus = "Repair after conflict",
        )
        val prompt = orchestrator.buildSessionSystemPrompt(memory, isFirstSession = false)
        assertThat(prompt).contains("continuing therapy")
        assertThat(prompt).contains("Working on listening")
        assertThat(prompt).contains("Repair after conflict")
    }

    @Test
    fun buildSessionSystemPrompt_marks_first_session() {
        val prompt = orchestrator.buildSessionSystemPrompt(null, isFirstSession = true)
        assertThat(prompt).contains("early session")
        assertThat(prompt).contains("not a licensed clinician")
    }
}
