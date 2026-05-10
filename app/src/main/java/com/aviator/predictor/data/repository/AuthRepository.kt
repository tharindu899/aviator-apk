package com.aviator.predictor.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.libraries.identity.googleid.*
import com.aviator.predictor.BuildConfig
import com.aviator.predictor.data.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

// ── DataStore instance (one per app) ─────────────────────────────────────────
private val Context.sessionDataStore by preferencesDataStore(name = "aviator_session")

data class AuthResult(
    val success: Boolean,
    val token: String = "",
    val idToken: String = "",
    val profile: UserProfile? = null,
    val error: String = ""
)

class AuthRepository(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    // ── DataStore keys ────────────────────────────────────────────────────
    private val KEY_EMAIL        = stringPreferencesKey("user_email")
    private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    private val KEY_PHOTO_URL    = stringPreferencesKey("photo_url")
    private val KEY_UID          = stringPreferencesKey("uid")

    // ── Restore session (called on every app start — no UI shown) ─────────
    //
    // Reads the saved email from DataStore, then calls GoogleAuthUtil.getToken()
    // to silently obtain a fresh OAuth token.  Returns success if the account
    // is still on the device and hasn't revoked permissions; otherwise the
    // caller should show the sign-in screen.
    suspend fun tryRestoreSession(): AuthResult = withContext(Dispatchers.IO) {
        try {
            val prefs = context.sessionDataStore.data.first()
            val email = prefs[KEY_EMAIL]
                ?: return@withContext AuthResult(false, error = "No saved session")

            // Get a fresh token without any UI
            val token = GoogleAuthUtil.getToken(
                context,
                email,
                "oauth2:https://www.googleapis.com/auth/drive.appdata"
            ) ?: return@withContext AuthResult(false, error = "Token refresh failed")

            val profile = UserProfile(
                uid         = prefs[KEY_UID]          ?: email,
                email       = email,
                displayName = prefs[KEY_DISPLAY_NAME] ?: "",
                photoUrl    = prefs[KEY_PHOTO_URL]    ?: "",
                lastLoginAt = System.currentTimeMillis()
            )

            AuthResult(success = true, token = token, profile = profile)
        } catch (e: Exception) {
            // Token fetch failed (revoked, account removed, no network on first cold boot)
            AuthResult(false, error = "Session restore failed: ${e.message}")
        }
    }

    // ── Full sign-in (shows Google account picker) ────────────────────────
    suspend fun signIn(activity: Activity): AuthResult = withContext(Dispatchers.Main) {
        try {
            val signInOption = GetSignInWithGoogleOption
                .Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken    = googleCred.idToken
                val email      = googleCred.id

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

                // ── Persist session so next launch skips the sign-in screen ──
                saveSession(profile)

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

    // ── Save / clear session ──────────────────────────────────────────────

    suspend fun saveSession(profile: UserProfile) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_UID]          = profile.uid
            prefs[KEY_EMAIL]        = profile.email
            prefs[KEY_DISPLAY_NAME] = profile.displayName
            prefs[KEY_PHOTO_URL]    = profile.photoUrl
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }

    // ── Drive OAuth token ─────────────────────────────────────────────────

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
        } catch (_: Exception) {}
        clearSession()   // ← wipe saved email so next launch shows sign-in
    }
}
