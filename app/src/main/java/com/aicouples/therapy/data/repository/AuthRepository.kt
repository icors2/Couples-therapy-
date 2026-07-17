package com.aicouples.therapy.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.aicouples.therapy.BuildConfig
import com.aicouples.therapy.data.model.Profile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    @ApplicationContext private val context: Context,
) {
    val sessionStatus: Flow<SessionStatus> = supabase.auth.sessionStatus

    val isSignedIn: Flow<Boolean> = sessionStatus.map { it is SessionStatus.Authenticated }

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
            BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signInWithGoogle(): Result<Unit> = runCatching {
        check(isConfigured()) {
            "Configure SUPABASE_URL, SUPABASE_ANON_KEY, and GOOGLE_WEB_CLIENT_ID (see user_setup.md)"
        }
        val nonce = UUID.randomUUID().toString()
        val hashedNonce = sha256(nonce)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialException) {
            throw IllegalStateException(e.message ?: "Google Sign-In failed", e)
        }

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            supabase.auth.signInWith(IDToken) {
                idToken = googleIdTokenCredential.idToken
                provider = Google
                this.nonce = nonce
            }
        } else {
            error("Unexpected credential type")
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    suspend fun fetchProfile(): Profile? {
        val uid = currentUserId() ?: return null
        return supabase.from("profiles")
            .select {
                filter { eq("id", uid) }
            }
            .decodeSingleOrNull<Profile>()
    }

    suspend fun ensureSessionRestored() {
        runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true) }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
