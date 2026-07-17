package com.aicouples.therapy.data.repository

import android.util.Log
import com.aicouples.therapy.data.model.AppNotification
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

@Singleton
class NotificationRepository @Inject constructor(
    private val client: SupabaseClient,
) {
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

    fun subscribe(): Flow<AppNotification> = callbackFlow {
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            awaitClose { }
            return@callbackFlow
        }

        val channel = client.channel("notifications-$userId-${System.currentTimeMillis()}")
        val job = launch {
            try {
                val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "notifications"
                    filter("user_id", FilterOperator.EQ, userId)
                }
                channel.subscribe()
                changes.collect { action ->
                    runCatching { action.decodeRecord<AppNotification>() }
                        .onSuccess { trySend(it) }
                        .onFailure { error ->
                            Log.e(TAG, "Failed to decode notification realtime payload", error)
                        }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Notification realtime subscription failed", error)
            }
        }

        awaitClose {
            job.cancel()
            launch {
                runCatching {
                    client.realtime.removeChannel(channel)
                }
            }
        }
    }

    private companion object {
        const val TAG = "NotificationRepo"
    }
}
