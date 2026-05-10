package com.aviator.predictor.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.*
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.libraries.identity.googleid.*
import com.aviator.predictor.BuildConfig
import com.aviator.predictor.data.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class AuthResult(
    val success: Boolean,
    val token: String = "",
    val idToken: String = "",
    val profile: UserProfile? = null,
    val error: String = ""
)

class AuthRepository(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    // Activity is required so Credential Manager can show the sign-in bottom sheet
    suspend fun signIn(activity: Activity): AuthResult = withContext(Dispatchers.Main) {
        try {
            // GetSignInWithGoogleOption always shows the account picker —
            // fixes "No credentials available" from GetGoogleIdOption
            val signInOption = GetSignInWithGoogleOption
                .Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity   // Must be Activity, not Application
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken    = googleCred.idToken
                val email      = googleCred.id   // email address

                // Get a real OAuth access token scoped to Drive appdata
                val accessToken = getDriveAccessToken(email)
                    ?: return@withContext AuthResult(false, error = "Failed to get Drive access token")

                val profile = UserProfile(
                    uid          = email,
                    email        = email,
                    displayName  = googleCred.displayName ?: "",
                    photoUrl     = googleCred.profilePictureUri?.toString() ?: "",
                    createdAt    = System.currentTimeMillis(),
                    lastLoginAt  = System.currentTimeMillis()
                )

                AuthResult(
                    success = true,
                    token   = accessToken,
                    idToken = idToken,
                    profile = profile
                )
            } else {
                AuthResult(false, error = "Unsupported credential type")
            }

        } catch (e: GetCredentialCancellationException) {
            AuthResult(false, error = "Sign-in cancelled")
        } catch (e: NoCredentialException) {
            AuthResult(false, error = "No Google account found on this device")
        } catch (e: GetCredentialException) {
            AuthResult(false, error = "Sign-in failed: ${e.message}")
        } catch (e: Exception) {
            AuthResult(false, error = "Unexpected error: ${e.message}")
        }
    }

    // Uses GoogleAuthUtil to exchange the signed-in account for a real
    // OAuth 2.0 access token with drive.appdata scope
    private suspend fun getDriveAccessToken(email: String): String? =
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(
                    context,
                    email,
                    "oauth2:https://www.googleapis.com/auth/drive.appdata"
                )
            } catch (e: Exception) {
                null
            }
        }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Ignore
        }
    }
}
