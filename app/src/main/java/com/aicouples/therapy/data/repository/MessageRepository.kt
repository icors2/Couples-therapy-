package com.aicouples.therapy.data.repository

import android.util.Log
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
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class MessageRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun listMessages(sessionId: String, limit: Long = 200): List<ChatMessage> =
        client.from("messages")
            .select {
                filter { eq("session_id", sessionId) }
                order("created_at", Order.ASCENDING)
                limit(limit)
            }
            .decodeList()

    /**
     * Inserts a partner message, then awaits ai-respond so callers can clear the typing indicator
     * when the AI is waiting or the invoke fails.
     */
    suspend fun sendMessage(
        sessionId: String,
        content: String,
        senderRole: MessageSender,
    ): Result<SendMessageResult> = runCatchingResultSuspend {
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

        var aiOutcome: FunctionResult? = null
        var aiFailed = false
        if (senderRole == MessageSender.PARTNER_A || senderRole == MessageSender.PARTNER_B) {
            runCatching {
                client.functions.invoke(
                    function = "ai-respond",
                    body = AiRespondRequest(
                        sessionId = sessionId,
                        triggerMessageId = inserted.id,
                    ),
                ).body<FunctionResult>()
            }.onSuccess { aiOutcome = it }
                .onFailure { error ->
                    aiFailed = true
                    Log.e(TAG, "ai-respond invoke failed", error)
                }
        }

        SendMessageResult(
            message = inserted,
            aiRespond = aiOutcome,
            aiInvokeFailed = aiFailed,
        )
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

    suspend fun setPinned(messageId: String, pinned: Boolean): Result<ChatMessage> =
        runCatchingResultSuspend {
            client.from("messages")
                .update(mapOf("pinned" to pinned)) {
                    filter { eq("id", messageId) }
                    select()
                }
                .decodeSingle()
        }

    fun subscribe(sessionId: String): Flow<ChatMessage> = callbackFlow {
        val channel = client.channel("messages-$sessionId-${System.currentTimeMillis()}")
        val job = launch {
            try {
                val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    filter("session_id", FilterOperator.EQ, sessionId)
                }
                channel.subscribe()
                changes.collect { action ->
                    runCatching { action.decodeRecord<ChatMessage>() }
                        .onSuccess { trySend(it) }
                        .onFailure { error ->
                            Log.e(TAG, "Failed to decode message realtime payload", error)
                        }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Message realtime subscription failed", error)
            }
        }

        awaitClose {
            job.cancel()
            launch {
                runCatching { client.realtime.removeChannel(channel) }
            }
        }
    }

    suspend fun requestAiResponse(sessionId: String): Result<FunctionResult> = runCatchingResultSuspend {
        client.functions.invoke(
            function = "ai-respond",
            body = AiRespondRequest(sessionId = sessionId),
        ).body()
    }

    private companion object {
        const val TAG = "MessageRepo"
    }
}

data class SendMessageResult(
    val message: ChatMessage,
    val aiRespond: FunctionResult? = null,
    val aiInvokeFailed: Boolean = false,
) {
    /** True when the server indicated the AI will not reply immediately. */
    val aiWaiting: Boolean
        get() {
            val msg = aiRespond?.message?.lowercase().orEmpty()
            return msg.contains("waiting") ||
                msg.contains("debounced") ||
                msg.contains("already responded")
        }
}

@Serializable
private data class MessageInsert(
    @SerialName("session_id") val sessionId: String,
    val sender: MessageSender,
    @SerialName("sender_user_id") val senderUserId: String,
    val content: String,
)
