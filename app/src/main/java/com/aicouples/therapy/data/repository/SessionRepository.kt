package com.aicouples.therapy.data.repository

import android.util.Log
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.common.runCatchingResultSuspend
import com.aicouples.therapy.data.model.FunctionResult
import com.aicouples.therapy.data.model.SessionActionRequest
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.StartSessionRequest
import com.aicouples.therapy.data.model.TherapySession
import io.github.jan.supabase.SupabaseClient
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

@Singleton
class SessionRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun startTherapy(relationshipId: String): Result<FunctionResult> = runCatchingResultSuspend {
        val response = client.functions.invoke(
            function = "start-session",
            body = StartSessionRequest(relationshipId = relationshipId),
        )
        response.body()
    }

    suspend fun joinSession(sessionId: String): Result<FunctionResult> = runCatchingResultSuspend {
        val response = client.functions.invoke(
            function = "join-session",
            body = SessionActionRequest(sessionId = sessionId),
        )
        response.body()
    }

    suspend fun declineSession(sessionId: String): Result<FunctionResult> = runCatchingResultSuspend {
        val response = client.functions.invoke(
            function = "decline-session",
            body = SessionActionRequest(sessionId = sessionId),
        )
        response.body()
    }

    suspend fun endSession(sessionId: String): Result<FunctionResult> = runCatchingResultSuspend {
        val response = client.functions.invoke(
            function = "end-session",
            body = SessionActionRequest(sessionId = sessionId),
        )
        response.body()
    }

    suspend fun getSession(sessionId: String): TherapySession? =
        client.from("sessions")
            .select { filter { eq("id", sessionId) } }
            .decodeSingleOrNull()

    suspend fun getActiveOrPendingSession(relationshipId: String): TherapySession? {
        val sessions = client.from("sessions")
            .select {
                filter { eq("relationship_id", relationshipId) }
                order("started_at", Order.DESCENDING)
                limit(10)
            }
            .decodeList<TherapySession>()
        return sessions.firstOrNull {
            it.status == SessionStatus.PENDING || it.status == SessionStatus.ACTIVE
        }
    }

    suspend fun listSessions(relationshipId: String, limit: Long = 50): List<TherapySession> =
        client.from("sessions")
            .select {
                filter { eq("relationship_id", relationshipId) }
                order("started_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    fun observeSession(sessionId: String): Flow<TherapySession?> = callbackFlow {
        trySend(runCatching { getSession(sessionId) }.getOrNull())

        val channel = client.channel("session-$sessionId-${System.currentTimeMillis()}")
        val job = launch {
            try {
                val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "sessions"
                    filter("id", FilterOperator.EQ, sessionId)
                }
                channel.subscribe()
                changes.collect { action ->
                    when (action) {
                        is PostgresAction.Insert ->
                            runCatching { action.decodeRecord<TherapySession>() }
                                .onSuccess { trySend(it) }
                                .onFailure {
                                    Log.e(TAG, "Failed to decode session insert", it)
                                    trySend(runCatching { getSession(sessionId) }.getOrNull())
                                }
                        is PostgresAction.Update ->
                            runCatching { action.decodeRecord<TherapySession>() }
                                .onSuccess { trySend(it) }
                                .onFailure {
                                    Log.e(TAG, "Failed to decode session update", it)
                                    trySend(runCatching { getSession(sessionId) }.getOrNull())
                                }
                        is PostgresAction.Delete -> trySend(null)
                        else -> trySend(runCatching { getSession(sessionId) }.getOrNull())
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Session realtime subscription failed", error)
            }
        }

        awaitClose {
            job.cancel()
            launch { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    private companion object {
        const val TAG = "SessionRepo"
    }
}
