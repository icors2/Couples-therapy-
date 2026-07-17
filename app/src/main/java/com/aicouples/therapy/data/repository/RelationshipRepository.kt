package com.aicouples.therapy.data.repository

import com.aicouples.therapy.common.PairCode
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.common.runCatchingResultSuspend
import com.aicouples.therapy.data.model.FunctionResult
import com.aicouples.therapy.data.model.PairRequest
import com.aicouples.therapy.data.model.Relationship
import com.aicouples.therapy.data.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
) {
    suspend fun getCurrentRelationship(): Relationship? {
        val profile = authRepository.getProfile() ?: return null
        val relationshipId = profile.relationshipId ?: return null
        return client.from("relationships")
            .select { filter { eq("id", relationshipId) } }
            .decodeSingleOrNull()
    }

    suspend fun getPartnerProfile(): UserProfile? {
        val me = authRepository.getProfile() ?: return null
        val relationship = getCurrentRelationship() ?: return null
        val partnerId = when (me.id) {
            relationship.partner1Id -> relationship.partner2Id
            else -> relationship.partner1Id
        } ?: return null

        return client.from("profiles")
            .select { filter { eq("id", partnerId) } }
            .decodeSingleOrNull()
    }

    suspend fun pairWithCode(rawCode: String): Result<FunctionResult> = runCatchingResultSuspend {
        val code = PairCode.normalize(rawCode)
        require(PairCode.isValid(code)) { "Pair code must be 6 characters" }

        val response = client.functions.invoke(
            function = "pair-partner",
            body = PairRequest(partnerCode = code),
        )
        response.body<FunctionResult>()
    }

    suspend fun refreshProfile(): UserProfile? = authRepository.getProfile()
}
