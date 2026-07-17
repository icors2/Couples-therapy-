package com.aicouples.therapy.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Profile(
    val id: String,
    val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("google_id") val googleId: String? = null,
    @SerialName("pair_code") val pairCode: String = "",
    @SerialName("relationship_id") val relationshipId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Relationship(
    val id: String,
    @SerialName("partner1_id") val partner1Id: String,
    @SerialName("partner2_id") val partner2Id: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
enum class SessionStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("ended") ENDED,
    @SerialName("declined") DECLINED,
    @SerialName("expired") EXPIRED,
}

@Serializable
data class TherapySession(
    val id: String,
    @SerialName("relationship_id") val relationshipId: String,
    @SerialName("started_by") val startedBy: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("ended_by") val endedBy: String? = null,
    val status: SessionStatus = SessionStatus.PENDING,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("partner_joined_at") val partnerJoinedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
enum class MessageSender {
    @SerialName("partner_a") PARTNER_A,
    @SerialName("partner_b") PARTNER_B,
    @SerialName("ai") AI,
    @SerialName("system") SYSTEM,
}

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_role") val senderRole: MessageSender,
    val content: String,
    val tokens: Int? = null,
    val model: String? = null,
    @SerialName("read_by_partner1") val readByPartner1: Boolean = false,
    @SerialName("read_by_partner2") val readByPartner2: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TherapeuticMemory(
    @SerialName("relationship_summary") val relationshipSummary: String = "",
    @SerialName("major_conflicts") val majorConflicts: List<String> = emptyList(),
    @SerialName("communication_patterns") val communicationPatterns: List<String> = emptyList(),
    val wins: List<String> = emptyList(),
    val goals: List<String> = emptyList(),
    @SerialName("follow_up_topics") val followUpTopics: List<String> = emptyList(),
    @SerialName("emotional_progress") val emotionalProgress: String = "",
    @SerialName("next_session_focus") val nextSessionFocus: String = "",
    @SerialName("agreed_commitments") val agreedCommitments: List<String> = emptyList(),
    @SerialName("unresolved_issues") val unresolvedIssues: List<String> = emptyList(),
    @SerialName("sessions_included") val sessionsIncluded: Int = 0,
)

@Serializable
data class AiMemoryRow(
    val id: String? = null,
    @SerialName("relationship_id") val relationshipId: String,
    val version: Int,
    @SerialName("memory_json") val memoryJson: TherapeuticMemory,
    @SerialName("sessions_included") val sessionsIncluded: Int = 0,
    @SerialName("is_current") val isCurrent: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class AiArchiveRow(
    val id: String? = null,
    @SerialName("relationship_id") val relationshipId: String,
    @SerialName("archive_number") val archiveNumber: Int,
    @SerialName("summary_json") val summaryJson: TherapeuticMemory,
    @SerialName("sessions_from") val sessionsFrom: Int,
    @SerialName("sessions_to") val sessionsTo: Int,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
enum class NotificationType {
    @SerialName("session_invite") SESSION_INVITE,
    @SerialName("partner_joined") PARTNER_JOINED,
    @SerialName("session_ended") SESSION_ENDED,
    @SerialName("session_timeout") SESSION_TIMEOUT,
    @SerialName("partner_paired") PARTNER_PAIRED,
}

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class UserSettings(
    @SerialName("user_id") val userId: String,
    @SerialName("dark_mode") val darkMode: Boolean = false,
    @SerialName("ai_model") val aiModel: String = "gpt-4o-mini",
    @SerialName("notify_session_invite") val notifySessionInvite: Boolean = true,
    @SerialName("notify_session_end") val notifySessionEnd: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MessageInsert(
    @SerialName("session_id") val sessionId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_role") val senderRole: MessageSender,
    val content: String,
    val tokens: Int? = null,
    val model: String? = null,
)

@Serializable
data class PairCodeArgs(
    @SerialName("partner_code") val partnerCode: String,
)

@Serializable
data class SessionRespondArgs(
    @SerialName("p_session_id") val sessionId: String,
    val accept: Boolean,
)

@Serializable
data class EndSessionArgs(
    @SerialName("p_session_id") val sessionId: String,
    val reason: String = "manual",
)

@Serializable
data class TouchActivityArgs(
    @SerialName("p_session_id") val sessionId: String,
)

/** OpenAI chat completion wire models */
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Double = 0.7,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val stream: Boolean = false,
)

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OpenAiChatResponse(
    val id: String? = null,
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

@Serializable
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ProfileUpdate(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
)

@Serializable
data class SettingsUpdate(
    @SerialName("dark_mode") val darkMode: Boolean? = null,
    @SerialName("ai_model") val aiModel: String? = null,
    @SerialName("notify_session_invite") val notifySessionInvite: Boolean? = null,
    @SerialName("notify_session_end") val notifySessionEnd: Boolean? = null,
)

@Serializable
data class NotificationReadUpdate(
    @SerialName("read_at") val readAt: String,
)
