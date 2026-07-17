package com.aicouples.therapy.data.repository

import com.aicouples.therapy.data.model.MemoryArchive
import com.aicouples.therapy.data.model.TherapeuticMemory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun getLatestMemory(relationshipId: String): TherapeuticMemory? =
        client.from("ai_memory")
            .select {
                filter { eq("relationship_id", relationshipId) }
                order("version", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull()

    suspend fun listMemoryVersions(relationshipId: String): List<TherapeuticMemory> =
        client.from("ai_memory")
            .select {
                filter { eq("relationship_id", relationshipId) }
                order("version", Order.DESCENDING)
            }
            .decodeList()

    suspend fun listArchives(relationshipId: String): List<MemoryArchive> =
        client.from("ai_archives")
            .select {
                filter { eq("relationship_id", relationshipId) }
                order("archive_number", Order.DESCENDING)
            }
            .decodeList()
}
