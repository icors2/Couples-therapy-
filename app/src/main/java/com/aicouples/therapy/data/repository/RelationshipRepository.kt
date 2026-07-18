package com.aicouples.therapy.data.repository

import com.aicouples.therapy.common.PairCode
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.common.runCatchingResultSuspend
import com.aicouples.therapy.data.model.AttestAgeRequest
import com.aicouples.therapy.data.model.ConnectionItem
import com.aicouples.therapy.data.model.ConsentParentalRequest
import com.aicouples.therapy.data.model.FunctionResult
import com.aicouples.therapy.data.model.MemberRole
import com.aicouples.therapy.data.model.PairRequest
import com.aicouples.therapy.data.model.ParentalConsent
import com.aicouples.therapy.data.model.Relationship
import com.aicouples.therapy.data.model.RelationshipType
import com.aicouples.therapy.data.model.UnpairRequest
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
    private val intakeRepository: IntakeRepository,
) {
    suspend fun listRelationships(): List<Relationship> {
        val me = authRepository.getProfile() ?: return emptyList()
        val asP1 = client.from("relationships")
            .select { filter { eq("partner1_id", me.id) } }
            .decodeList<Relationship>()
        val asP2 = client.from("relationships")
            .select { filter { eq("partner2_id", me.id) } }
            .decodeList<Relationship>()
        return (asP1 + asP2).distinctBy { it.id }
    }

    suspend fun getRelationship(relationshipId: String): Relationship? =
        client.from("relationships")
            .select { filter { eq("id", relationshipId) } }
            .decodeSingleOrNull()

    suspend fun getCurrentRelationship(): Relationship? {
        val profile = authRepository.getProfile() ?: return null
        val activeId = profile.activeRelationshipId ?: profile.relationshipId
        if (activeId != null) {
            return getRelationship(activeId) ?: listRelationships().firstOrNull()
        }
        return listRelationships().firstOrNull()
    }

    suspend fun getPartnerProfile(relationship: Relationship? = null): UserProfile? {
        val me = authRepository.getProfile() ?: return null
        val rel = relationship ?: getCurrentRelationship() ?: return null
        val partnerId = when (me.id) {
            rel.partner1Id -> rel.partner2Id
            else -> rel.partner1Id
        } ?: return null
        return client.from("profiles")
            .select { filter { eq("id", partnerId) } }
            .decodeSingleOrNull()
    }

    suspend fun listConnections(): List<ConnectionItem> {
        val me = authRepository.getProfile() ?: return emptyList()
        return listRelationships().map { rel ->
            val partner = getPartnerProfile(rel)
            val myRole = when (me.id) {
                rel.partner1Id -> rel.partner1Role
                else -> rel.partner2Role
            }
            val consent = getConsent(rel.id)
            val hasMinor = me.isMinor || (partner?.isMinor == true)
            val needsConsent = rel.relationshipType == RelationshipType.PARENT_CHILD &&
                hasMinor &&
                consent == null
            val intake = when (val status = intakeRepository.getStatus(rel.id)) {
                is Result.Success -> status.data
                else -> null
            }
            val intakeRequired = intake?.required == true
            val intakeMeDone = intake?.meDone == true
            val intakePartnerDone = intake?.partnerDone == true
            val intakeReady = !intakeRequired || (intakeMeDone && intakePartnerDone)
            val canStart = when {
                me.isMinor && consent == null -> false
                needsConsent -> false
                rel.relationshipType == RelationshipType.COUPLES && (me.isMinor || partner?.isMinor == true) -> false
                !intakeReady -> false
                else -> true
            }
            ConnectionItem(
                relationship = rel,
                partner = partner,
                myRole = myRole,
                needsConsent = needsConsent,
                canStartTherapy = canStart,
                intakeRequired = intakeRequired,
                intakeMeDone = intakeMeDone,
                intakePartnerDone = intakePartnerDone,
            )
        }
    }

    suspend fun getConsent(relationshipId: String): ParentalConsent? =
        client.from("parental_consents")
            .select { filter { eq("relationship_id", relationshipId) } }
            .decodeSingleOrNull()

    suspend fun setActiveRelationship(relationshipId: String): Result<Unit> = runCatchingResultSuspend {
        val me = authRepository.getProfile() ?: throw IllegalStateException("Not authenticated")
        client.from("profiles").update(
            mapOf("active_relationship_id" to relationshipId),
        ) {
            filter { eq("id", me.id) }
        }
    }

    suspend fun attestAge(dateOfBirth: String): Result<FunctionResult> = runCatchingResultSuspend {
        client.functions.invoke(
            function = "attest-age",
            body = AttestAgeRequest(dateOfBirth = dateOfBirth),
        ).body()
    }

    suspend fun consentParental(relationshipId: String): Result<FunctionResult> = runCatchingResultSuspend {
        client.functions.invoke(
            function = "consent-parental",
            body = ConsentParentalRequest(relationshipId = relationshipId, accept = true),
        ).body()
    }

    suspend fun pairWithCode(
        rawCode: String,
        relationshipType: RelationshipType = RelationshipType.COUPLES,
        myRole: MemberRole? = null,
    ): Result<FunctionResult> = runCatchingResultSuspend {
        val code = PairCode.normalize(rawCode)
        require(PairCode.isValid(code)) { "Pair code must be 6 characters" }

        val response = client.functions.invoke(
            function = "pair-partner",
            body = PairRequest(
                partnerCode = code,
                relationshipType = relationshipType,
                myRole = when (relationshipType) {
                    RelationshipType.COUPLES -> MemberRole.PARTNER
                    RelationshipType.PARENT_CHILD -> myRole
                },
            ),
        )
        response.body<FunctionResult>()
    }

    suspend fun unpair(relationshipId: String): Result<FunctionResult> = runCatchingResultSuspend {
        val response = client.functions.invoke(
            function = "unpair-partner",
            body = UnpairRequest(relationshipId = relationshipId),
        )
        response.body<FunctionResult>()
    }

    suspend fun refreshProfile(): UserProfile? = authRepository.getProfile()
}
