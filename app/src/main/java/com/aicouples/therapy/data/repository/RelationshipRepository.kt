package com.aicouples.therapy.data.repository

import com.aicouples.therapy.data.model.PairCodeArgs
import com.aicouples.therapy.data.model.Profile
import com.aicouples.therapy.data.model.Relationship
import com.aicouples.therapy.util.PairCode
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) {
    suspend fun getMyProfile(): Profile? = authRepository.fetchProfile()

    suspend fun getPartnerProfile(): Profile? {
        val me = getMyProfile() ?: return null
        val relId = me.relationshipId ?: return null
        val relationship = getRelationship(relId) ?: return null
        val partnerId = if (relationship.partner1Id == me.id) {
            relationship.partner2Id
        } else {
            relationship.partner1Id
        }
        return supabase.from("profiles")
            .select { filter { eq("id", partnerId) } }
            .decodeSingleOrNull<Profile>()
    }

    suspend fun getRelationship(id: String): Relationship? =
        supabase.from("relationships")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun pairWithCode(rawCode: String): Result<String> = runCatching {
        val code = PairCode.normalize(rawCode)
        check(PairCode.isValid(code)) { "Pair code must be 6 characters" }
        supabase.postgrest.rpc(
            function = "pair_with_code",
            parameters = PairCodeArgs(partnerCode = code),
        ).decodeAs<String>()
    }

    fun partnerRoleFor(me: Profile, relationship: Relationship): PartnerSlot =
        if (relationship.partner1Id == me.id) PartnerSlot.A else PartnerSlot.B
}

enum class PartnerSlot { A, B }
