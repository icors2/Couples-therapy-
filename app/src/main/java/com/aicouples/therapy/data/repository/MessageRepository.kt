package com.aicouples.therapy.data.repository

import com.aicouples.therapy.common.Result
import com.aicouples.therapy.common.runCatchingResultSuspend
import com.aicouples.therapy.data.model.AiRespondRequest
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.FunctionResult
import com.aicouples.therapy.data.model.MessageSender
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Singleton
class MessageRepository @Inject constructor(
    private val client: SupabaseClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _messageEvents = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    val messageEvents = _messageEvents.asSharedFlow()

    suspend fun listMessages(sessionId: String, limit: Long = 200): List<ChatMessage> =
        client.from("messages")
            .select {
                filter { eq("session_id", sessionId) }
                order("created_at", Order.ASCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun sendMessage(
        sessionId: String,
        content: String,
        senderRole: MessageSender,
    ): Result<ChatMessage> = runCatchingResultSuspend {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not authenticated")

        val inserted = client.from("messages")
            .insert(
                MessageInsert(
                    sessionId = sessionId,
                    sender = senderRole,
                    senderUserId = userId,
                    content = content.trim(),
                ),
            ) {
                select()
            }
            .decodeSingle<ChatMessage>()

        if (senderRole == MessageSender.PARTNER_A || senderRole == MessageSender.PARTNER_B) {
            scope.launch {
                runCatching {
                    client.functions.invoke(
                        function = "ai-respond",
                        body = AiRespondRequest(
                            sessionId = sessionId,
                            triggerMessageId = inserted.id,
                        ),
                    )
                }
            }
        }

        inserted
    }

    suspend fun markRead(sessionId: String, messageIds: List<String>): Result<Unit> =
        runCatchingResultSuspend {
            val userId = client.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("Not authenticated")
            messageIds.forEach { id ->
                client.from("messages").update(
                    mapOf("read_by" to listOf(userId)),
                ) {
                    filter {
                        eq("id", id)
                        eq("session_id", sessionId)
                    }
                }
            }
        }

    fun subscribe(sessionId: String): Flow<ChatMessage> {
        val channel = client.channel("messages-$sessionId")
        scope.launch {
            val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
                filter("session_id", FilterOperator.EQ, sessionId)
            }
            channel.subscribe()
            changes.collect { action ->
                val message = json.decodeFromJsonElement(
                    ChatMessage.serializer(),
                    action.record,
                )
                _messageEvents.emit(message)
            }
        }
        return messageEvents
    }

    suspend fun requestAiResponse(sessionId: String): Result<FunctionResult> = runCatchingResultSuspend {
        client.functions.invoke(
            function = "ai-respond",
            body = AiRespondRequest(sessionId = sessionId),
        ).body()
    }
}

@Serializable
private data class MessageInsert(
    @SerialName("session_id") val sessionId: String,
    val sender: MessageSender,
    @SerialName("sender_user_id") val senderUserId: String,
    val content: String,
)
