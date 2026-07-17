package com.aicouples.therapy.data.repository

import com.aicouples.therapy.data.model.AiArchiveRow
import com.aicouples.therapy.data.model.AiMemoryRow
import com.aicouples.therapy.data.model.TherapeuticMemory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun loadCurrentMemory(relationshipId: String): AiMemoryRow? =
        supabase.from("ai_memory")
            .select {
                filter {
                    eq("relationship_id", relationshipId)
                    eq("is_current", true)
                }
            }
            .decodeList<AiMemoryRow>()
            .maxByOrNull { it.version }

    suspend fun loadLatestArchive(relationshipId: String): AiArchiveRow? =
        supabase.from("ai_archives")
            .select {
                filter { eq("relationship_id", relationshipId) }
            }
            .decodeList<AiArchiveRow>()
            .maxByOrNull { it.archiveNumber }

    suspend fun saveNewMemoryVersion(
        relationshipId: String,
        memory: TherapeuticMemory,
        sessionsIncluded: Int,
    ): AiMemoryRow {
        val current = loadCurrentMemory(relationshipId)
        val nextVersion = (current?.version ?: 0) + 1

        if (current?.id != null) {
            supabase.from("ai_memory").update(
                mapOf("is_current" to false)
            ) {
                filter { eq("id", current.id) }
            }
        }

        val row = AiMemoryRow(
            relationshipId = relationshipId,
            version = nextVersion,
            memoryJson = memory.copy(sessionsIncluded = sessionsIncluded),
            sessionsIncluded = sessionsIncluded,
            isCurrent = true,
        )
        return supabase.from("ai_memory")
            .insert(row) { select() }
            .decodeSingle()
    }

    suspend fun archiveMemory(
        relationshipId: String,
        summary: TherapeuticMemory,
        sessionsFrom: Int,
        sessionsTo: Int,
    ): AiArchiveRow {
        val latest = loadLatestArchive(relationshipId)
        val number = (latest?.archiveNumber ?: 0) + 1
        val row = AiArchiveRow(
            relationshipId = relationshipId,
            archiveNumber = number,
            summaryJson = summary,
            sessionsFrom = sessionsFrom,
            sessionsTo = sessionsTo,
        )
        return supabase.from("ai_archives")
            .insert(row) { select() }
            .decodeSingle()
    }
}
