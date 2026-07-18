package com.aicouples.therapy.data.repository

import com.aicouples.therapy.common.Result
import com.aicouples.therapy.common.runCatchingResultSuspend
import com.aicouples.therapy.data.model.IntakeAnswers
import com.aicouples.therapy.data.model.IntakeStatus
import com.aicouples.therapy.data.model.IntakeStatusRequest
import com.aicouples.therapy.data.model.FunctionResult
import com.aicouples.therapy.data.model.RelationshipIntake
import com.aicouples.therapy.data.model.SubmitIntakeRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntakeRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
) {
    suspend fun getMyIntake(relationshipId: String): RelationshipIntake? {
        val me = authRepository.getProfile() ?: return null
        return client.from("relationship_intakes")
            .select {
                filter {
                    eq("relationship_id", relationshipId)
                    eq("user_id", me.id)
                }
            }
            .decodeSingleOrNull()
    }

    suspend fun getStatus(relationshipId: String): Result<IntakeStatus> = runCatchingResultSuspend {
        client.functions.invoke(
            function = "intake-status",
            body = IntakeStatusRequest(relationshipId = relationshipId),
        ).body()
    }

    suspend fun submitIntake(
        relationshipId: String,
        answers: IntakeAnswers,
    ): Result<FunctionResult> = runCatchingResultSuspend {
        client.functions.invoke(
            function = "submit-intake",
            body = SubmitIntakeRequest(relationshipId = relationshipId, answers = answers),
        ).body()
    }
}
