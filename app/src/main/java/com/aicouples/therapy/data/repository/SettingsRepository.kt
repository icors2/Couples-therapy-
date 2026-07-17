package com.aicouples.therapy.data.repository

import com.aicouples.therapy.data.model.UserSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun getSettings(): UserSettings? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        return client.from("settings")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull()
            ?: UserSettings(userId = userId).also {
                client.from("settings").upsert(it)
            }
    }

    suspend fun updateSettings(settings: UserSettings) {
        client.from("settings").upsert(settings)
    }
}
