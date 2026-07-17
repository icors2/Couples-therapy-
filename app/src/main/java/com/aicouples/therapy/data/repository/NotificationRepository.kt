package com.aicouples.therapy.data.repository

import com.aicouples.therapy.data.model.AppNotification
import com.aicouples.therapy.data.model.NotificationReadUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Singleton
class NotificationRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _incoming = MutableSharedFlow<AppNotification>(extraBufferCapacity = 32)
    val incoming = _incoming.asSharedFlow()

    suspend fun listUnread(): List<AppNotification> {
        val uid = authRepository.currentUserId() ?: return emptyList()
        return supabase.from("notifications")
            .select {
                filter {
                    eq("user_id", uid)
                }
            }
            .decodeList<AppNotification>()
            .filter { it.readAt == null }
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    suspend fun markRead(id: String) {
        val now = Clock.System.now().toString()
        supabase.from("notifications").update(
            NotificationReadUpdate(readAt = now)
        ) {
            filter { eq("id", id) }
        }
    }

    fun startListening() {
        val uid = authRepository.currentUserId() ?: return
        scope.launch {
            val channel = supabase.channel("notifications-$uid")
            val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "notifications"
            }
            channel.subscribe()
            flow.collect { action ->
                runCatching { action.decodeRecord<AppNotification>() }
                    .onSuccess { notification ->
                        if (notification.userId == uid) {
                            _incoming.emit(notification)
                        }
                    }
            }
        }
    }
}
