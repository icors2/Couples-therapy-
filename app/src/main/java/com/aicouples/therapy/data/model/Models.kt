package com.aicouples.therapy.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
enum class SessionStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("ended") ENDED,
    @SerialName("declined") DECLINED,
    @SerialName("expired") EXPIRED,
}

@Serializable
enum class MessageSender {
    @SerialName("partner_a") PARTNER_A,
    @SerialName("partner_b") PARTNER_B,
    @SerialName("ai") AI,
    @SerialName("system") SYSTEM,
}

@Serializable
enum class NotificationType {
    @SerialName("session_invite") SESSION_INVITE,
    @SerialName("partner_joined") PARTNER_JOINED,
    @SerialName("session_ended") SESSION_ENDED,
    @SerialName("partner_paired") PARTNER_PAIRED,
    @SerialName("session_timeout") SESSION_TIMEOUT,
    @SerialName("partner_unpaired") PARTNER_UNPAIRED,
}

@Serializable
data class UserProfile(
    val id: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("pair_code") val pairCode: String,
    @SerialName("relationship_id") val relationshipId: String? = null,
    @SerialName("google_id") val googleId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Relationship(
    val id: String,
    @SerialName("partner1_id") val partner1Id: String,
    @SerialName("partner2_id") val partner2Id: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

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
    @SerialName("last_user_message_at") val lastUserMessageAt: String? = null,
    @SerialName("partner_a_joined") val partnerAJoined: Boolean = false,
    @SerialName("partner_b_joined") val partnerBJoined: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String,
    val sender: MessageSender,
    @SerialName("sender_user_id") val senderUserId: String? = null,
    val content: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_by") val readBy: List<String> = emptyList(),
    @SerialName("token_count") val tokenCount: Int? = null,
    val model: String? = null,
    val pinned: Boolean = false,
)

@Serializable
data class TherapeuticMemory(
    val id: String? = null,
    @SerialName("relationship_id") val relationshipId: String,
    val version: Int,
    @SerialName("memory_json") val memoryJson: TherapeuticMemoryDocument,
    @SerialName("sessions_included") val sessionsIncluded: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class TherapeuticMemoryDocument(
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
    @SerialName("strengths") val strengths: List<String> = emptyList(),
    @SerialName("homework") val homework: List<String> = emptyList(),
    @SerialName("key_facts") val keyFacts: List<String> = emptyList(),
    @SerialName("partner_a_notes") val partnerANotes: List<String> = emptyList(),
    @SerialName("partner_b_notes") val partnerBNotes: List<String> = emptyList(),
    @SerialName("sessions_included") val sessionsIncluded: Int = 0,
)

@Serializable
data class MemoryArchive(
    val id: String? = null,
    @SerialName("relationship_id") val relationshipId: String,
    @SerialName("archive_number") val archiveNumber: Int,
    val summary: TherapeuticMemoryDocument,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    @SerialName("payload") val payload: JsonObject? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class UserSettings(
    @SerialName("user_id") val userId: String,
    @SerialName("dark_mode") val darkMode: Boolean? = null,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = true,
    @SerialName("ai_provider") val aiProvider: String = "openai",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PairRequest(
    @SerialName("partner_code") val partnerCode: String,
)

@Serializable
data class StartSessionRequest(
    @SerialName("relationship_id") val relationshipId: String,
)

@Serializable
data class SessionActionRequest(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class AiRespondRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("trigger_message_id") val triggerMessageId: String? = null,
)

@Serializable
data class GenerateMemoryRequest(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class FunctionResult(
    val ok: Boolean = true,
    val message: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("relationship_id") val relationshipId: String? = null,
    val data: JsonElement? = null,
)
