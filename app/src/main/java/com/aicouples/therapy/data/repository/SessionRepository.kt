package com.aicouples.therapy.data.repository

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
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Singleton
class SessionRepository @Inject constructor(
    private val client: SupabaseClient,
    private val json: Json,
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

    fun observeSession(sessionId: String): Flow<TherapySession?> = kotlinx.coroutines.flow.flow {
        val channel = client.channel("session-$sessionId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "sessions"
            filter("id", FilterOperator.EQ, sessionId)
        }
        channel.subscribe()
        emit(getSession(sessionId))
        changes.collect { action ->
            when (action) {
                is PostgresAction.Insert ->
                    emit(json.decodeFromJsonElement(TherapySession.serializer(), action.record))
                is PostgresAction.Update ->
                    emit(json.decodeFromJsonElement(TherapySession.serializer(), action.record))
                is PostgresAction.Delete -> emit(null)
                else -> emit(getSession(sessionId))
            }
        }
    }
}
