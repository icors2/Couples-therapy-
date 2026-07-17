package com.aicouples.therapy.data.repository

import com.aicouples.therapy.data.model.AppNotification
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Singleton
class NotificationRepository @Inject constructor(
    private val client: SupabaseClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<AppNotification>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    suspend fun listUnread(): List<AppNotification> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()
        return client.from("notifications")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("is_read", false)
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    suspend fun listRecent(limit: Long = 30): List<AppNotification> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()
        return client.from("notifications")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()
    }

    suspend fun markRead(id: String) {
        client.from("notifications").update(mapOf("is_read" to true)) {
            filter { eq("id", id) }
        }
    }

    fun subscribe(): Flow<AppNotification> {
        val userId = client.auth.currentUserOrNull()?.id ?: return events
        val channel = client.channel("notifications-$userId")
        scope.launch {
            val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "notifications"
                filter("user_id", FilterOperator.EQ, userId)
            }
            channel.subscribe()
            changes.collect { action ->
                val notification = json.decodeFromJsonElement(
                    AppNotification.serializer(),
                    action.record,
                )
                _events.emit(notification)
            }
        }
        return events
    }
}
