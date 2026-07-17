package com.aicouples.therapy.data.repository

import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.EndSessionArgs
import com.aicouples.therapy.data.model.MessageInsert
import com.aicouples.therapy.data.model.MessageSender
import com.aicouples.therapy.data.model.SessionRespondArgs
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.TherapySession
import com.aicouples.therapy.data.model.TouchActivityArgs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@Singleton
class SessionRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var subscriptionJob: Job? = null

    private val _messageEvents = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    private val _sessionEvents = MutableSharedFlow<TherapySession>(extraBufferCapacity = 16)

    val messageEvents = _messageEvents.asSharedFlow()
    val sessionEvents = _sessionEvents.asSharedFlow()

    suspend fun startSession(): TherapySession =
        supabase.postgrest.rpc("start_therapy_session").decodeAs()

    suspend fun respondToSession(sessionId: String, accept: Boolean): TherapySession =
        supabase.postgrest.rpc(
            function = "respond_to_session",
            parameters = SessionRespondArgs(sessionId = sessionId, accept = accept),
        ).decodeAs()

    suspend fun endSession(sessionId: String, reason: String = "manual"): TherapySession =
        supabase.postgrest.rpc(
            function = "end_therapy_session",
            parameters = EndSessionArgs(sessionId = sessionId, reason = reason),
        ).decodeAs()

    suspend fun touchActivity(sessionId: String) {
        supabase.postgrest.rpc(
            function = "touch_session_activity",
            parameters = TouchActivityArgs(sessionId = sessionId),
        )
    }

    suspend fun getSession(sessionId: String): TherapySession? =
        supabase.from("therapy_sessions")
            .select { filter { eq("id", sessionId) } }
            .decodeSingleOrNull()

    suspend fun getActiveOrPendingSession(relationshipId: String): TherapySession? =
        supabase.from("therapy_sessions")
            .select {
                filter {
                    eq("relationship_id", relationshipId)
                    isIn("status", listOf("pending", "active"))
                }
            }
            .decodeList<TherapySession>()
            .maxByOrNull { it.createdAt.orEmpty() }

    suspend fun listSessions(relationshipId: String, limit: Int = 50): List<TherapySession> =
        supabase.from("therapy_sessions")
            .select {
                filter { eq("relationship_id", relationshipId) }
            }
            .decodeList<TherapySession>()
            .sortedByDescending { it.startedAt.orEmpty() }
            .take(limit)

    suspend fun listMessages(sessionId: String, limit: Int = 200): List<ChatMessage> =
        supabase.from("messages")
            .select {
                filter { eq("session_id", sessionId) }
            }
            .decodeList<ChatMessage>()
            .sortedBy { it.createdAt.orEmpty() }
            .takeLast(limit)

    suspend fun sendUserMessage(
        sessionId: String,
        content: String,
        role: MessageSender,
    ): ChatMessage {
        val uid = authRepository.currentUserId()
        val insert = MessageInsert(
            sessionId = sessionId,
            senderId = uid,
            senderRole = role,
            content = content.trim(),
        )
        val message = supabase.from("messages")
            .insert(insert) { select() }
            .decodeSingle<ChatMessage>()
        touchActivity(sessionId)
        return message
    }

    suspend fun sendAiMessage(
        sessionId: String,
        content: String,
        tokens: Int?,
        model: String?,
    ): ChatMessage {
        val insert = MessageInsert(
            sessionId = sessionId,
            senderId = null,
            senderRole = MessageSender.AI,
            content = content.trim(),
            tokens = tokens,
            model = model,
        )
        return supabase.from("messages")
            .insert(insert) { select() }
            .decodeSingle()
    }

    fun subscribeToSession(sessionId: String) {
        subscriptionJob?.cancel()
        subscriptionJob = scope.launch {
            val channel = supabase.channel("session-$sessionId")
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
            }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "therapy_sessions"
            }
            channel.subscribe()

            launch {
                inserts.collect { action ->
                    runCatching { action.decodeRecord<ChatMessage>() }
                        .onSuccess { message ->
                            if (message.sessionId == sessionId) {
                                _messageEvents.emit(message)
                            }
                        }
                }
            }
            launch {
                updates.collect { action ->
                    runCatching { action.decodeRecord<TherapySession>() }
                        .onSuccess { session ->
                            if (session.id == sessionId) {
                                _sessionEvents.emit(session)
                            }
                        }
                }
            }
        }
    }

    fun unsubscribe() {
        subscriptionJob?.cancel()
        subscriptionJob = null
    }

    suspend fun countCompletedSessions(relationshipId: String): Int =
        listSessions(relationshipId, limit = 500)
            .count { it.status == SessionStatus.ENDED || it.status == SessionStatus.EXPIRED }
}
