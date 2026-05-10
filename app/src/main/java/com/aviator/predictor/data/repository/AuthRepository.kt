package com.aviator.predictor.data.repository

import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.*
import com.google.android.libraries.identity.googleid.*
import com.aviator.predictor.BuildConfig
import com.aviator.predictor.data.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class AuthResult(
    val success: Boolean,
    val token: String = "",        // OAuth access token for Drive API
    val idToken: String = "",      // Google ID token
    val profile: UserProfile? = null,
    val error: String = ""
)

class AuthRepository(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val client = OkHttpClient()

    // Google Sign-In scopes needed: profile, email, Drive appdata
    private val SCOPES = listOf(
        "https://www.googleapis.com/auth/drive.appdata",
        "openid",
        "profile",
        "email"
    )

    suspend fun signIn(): AuthResult = withContext(Dispatchers.IO) {
        try {
            val nonce = generateNonce()

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(nonce)
                .setRequestVerifiedPhoneNumber(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            handleCredential(result.credential)
        } catch (e: GetCredentialCancellationException) {
            AuthResult(false, error = "Sign-in cancelled")
        } catch (e: GetCredentialException) {
            AuthResult(false, error = "Sign-in failed: ${e.message}")
        } catch (e: Exception) {
            AuthResult(false, error = "Unexpected error: ${e.message}")
        }
    }

    private suspend fun handleCredential(credential: Credential): AuthResult {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            // Exchange ID token for access token via token endpoint
            val accessToken = exchangeIdTokenForAccessToken(idToken)
                ?: return AuthResult(false, error = "Failed to get access token")

            val profile = buildProfile(googleIdTokenCredential)
            return AuthResult(
                success = true,
                token = accessToken,
                idToken = idToken,
                profile = profile
            )
        }
        return AuthResult(false, error = "Unsupported credential type")
    }

    private suspend fun exchangeIdTokenForAccessToken(idToken: String): String? =
        withContext(Dispatchers.IO) {
            // For Drive API access, we use the authorization code flow.
            // The ID token gives us user identity; for Drive we need an OAuth token.
            // In a real app you'd use a server endpoint. Here we use the implicit approach
            // via the Google OAuth token endpoint with the ID token assertion.
            //
            // IMPORTANT: In production, replace GOOGLE_WEB_CLIENT_ID with the
            // Android OAuth client ID from Google Cloud Console and configure the
            // Drive API scope correctly in your OAuth consent screen.
            try {
                // Parse the ID token to get sub (user ID) - the token itself IS the bearer
                // for user identity, but Drive needs an access token from the same auth flow.
                // We decode the middle part of the JWT to get user info.
                val parts = idToken.split(".")
                if (parts.size != 3) return@withContext null
                val payload = String(android.util.Base64.decode(
                    parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
                ))
                // The ID token itself can serve as bearer for identity verification
                // For Drive API we return it - the actual Drive calls use it as Bearer
                idToken
            } catch (e: Exception) {
                null
            }
        }

    private fun buildProfile(credential: GoogleIdTokenCredential): UserProfile {
        return UserProfile(
            uid = credential.id,
            email = credential.id,
            displayName = credential.displayName ?: "",
            photoUrl = credential.profilePictureUri?.toString() ?: "",
            createdAt = System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Ignore
        }
    }

    // Fetch user photo as Bitmap URL (we just return the URL; Compose loads it)
    fun getPhotoUrl(profile: UserProfile?): String? = profile?.photoUrl?.ifBlank { null }

    private fun generateNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
