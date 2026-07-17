package com.aicouples.therapy.ai

import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.MessageSender
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PromptOrchestratorTest {

    private val orchestrator = PromptOrchestrator()

    @Test
    fun shouldAiRespond_falseWhenOnlyOnePartnerSpokeOnce() {
        val messages = listOf(
            msg("1", MessageSender.PARTNER_A, "Hi"),
        )
        assertThat(orchestrator.shouldAiRespond(messages)).isFalse()
    }

    @Test
    fun shouldAiRespond_trueWhenBothPartnersSpoke() {
        val messages = listOf(
            msg("1", MessageSender.PARTNER_A, "I feel unheard"),
            msg("2", MessageSender.PARTNER_B, "I am trying"),
        )
        assertThat(orchestrator.shouldAiRespond(messages)).isTrue()
    }

    @Test
    fun shouldAiRespond_trueWhenTherapistAddressed() {
        val messages = listOf(
            msg("1", MessageSender.PARTNER_A, "Therapist, what do you think?"),
        )
        assertThat(orchestrator.shouldAiRespond(messages)).isTrue()
    }

    @Test
    fun shouldAiRespond_falseRightAfterAi() {
        val messages = listOf(
            msg("1", MessageSender.PARTNER_A, "Hello"),
            msg("2", MessageSender.PARTNER_B, "Hi"),
            msg("3", MessageSender.AI, "I hear you both."),
        )
        assertThat(orchestrator.shouldAiRespond(messages)).isFalse()
    }

    private fun msg(id: String, role: MessageSender, content: String) = ChatMessage(
        id = id,
        sessionId = "s1",
        senderRole = role,
        content = content,
    )
}
