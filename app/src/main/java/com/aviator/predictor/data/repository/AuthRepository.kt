package com.aviator.predictor.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.*
import com.aviator.predictor.BuildConfig
import com.aviator.predictor.data.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// ── DataStore instance (one per app) ─────────────────────────────────────────
private val Context.sessionDataStore by preferencesDataStore(name = "aviator_session")

private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"

data class AuthResult(
    val success: Boolean,
    val token: String = "",
    val idToken: String = "",
    val profile: UserProfile? = null,
    val error: String = "",
    val needsConsentRetry: Boolean = false   // true when consent screen was launched
)

class AuthRepository(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    // ── DataStore keys ────────────────────────────────────────────────────
    private val KEY_EMAIL        = stringPreferencesKey("user_email")
    private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    private val KEY_PHOTO_URL    = stringPreferencesKey("photo_url")
    private val KEY_UID          = stringPreferencesKey("uid")

    // ── Restore session (called on every app start — no UI shown) ─────────

    /**
     * Fast path — reads only from DataStore (disk).
     * Returns the saved profile in milliseconds with zero network calls.
     * Used to dismiss the splash screen and show cached signals immediately.
     */
    suspend fun tryRestoreSessionFast(): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.sessionDataStore.data.first()
            val email = prefs[KEY_EMAIL] ?: return@withContext null
            UserProfile(
                uid         = prefs[KEY_UID]          ?: email,
                email       = email,
                displayName = prefs[KEY_DISPLAY_NAME] ?: "",
                photoUrl    = prefs[KEY_PHOTO_URL]    ?: "",
                lastLoginAt = System.currentTimeMillis()
            )
        } catch (_: Exception) { null }
    }

    /**
     * Slow path — fetches/refreshes the Google OAuth token (may hit the network).
     * Call this in a background coroutine AFTER the UI is already visible.
     */
    suspend fun fetchToken(email: String): String? = getTokenForEmail(email, activity = null)

    suspend fun tryRestoreSession(): AuthResult = withContext(Dispatchers.IO) {
        try {
            val prefs = context.sessionDataStore.data.first()
            val email = prefs[KEY_EMAIL]
                ?: return@withContext AuthResult(false, error = "No saved session")

            // No activity available during restore — if consent is needed,
            // this returns null and we fall back to the sign-in screen.
            val token = getTokenForEmail(email, activity = null)
                ?: return@withContext AuthResult(false, error = "Token refresh failed")

            val profile = UserProfile(
                uid         = prefs[KEY_UID]          ?: email,
                email       = email,
                displayName = prefs[KEY_DISPLAY_NAME] ?: "",
                photoUrl    = prefs[KEY_PHOTO_URL]    ?: "",
                lastLoginAt = System.currentTimeMillis()
            )

            AuthResult(success = true, token = token, profile = profile)
        } catch (e: Exception) {
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

            val result = credentialManager.getCredential(request = request, context = activity)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken    = googleCred.idToken
                val email      = googleCred.id

                // Pass the activity so UserRecoverableAuthException can be handled
                // (new accounts need to grant Drive consent on first sign-in).
                val accessToken = getTokenForEmail(email, activity)

                if (accessToken == null) {
                    // getTokenForEmail already launched the consent screen.
                    // Tell the user to tap "Sign In" again after granting access.
                    return@withContext AuthResult(
                        success          = false,
                        needsConsentRetry = true,
                        error            = "Google Drive access required.\n\nPlease grant access in the screen that just opened, then tap \"Continue with Google\" again."
                    )
                }

                val profile = UserProfile(
                    uid         = email,
                    email       = email,
                    displayName = googleCred.displayName ?: "",
                    photoUrl    = googleCred.profilePictureUri?.toString() ?: "",
                    createdAt   = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis()
                )

                saveSession(profile)

                AuthResult(success = true, token = accessToken, idToken = idToken, profile = profile)
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

    // ── Token helpers ─────────────────────────────────────────────────────

    /**
     * Returns a valid (possibly cached) Drive access token for [email].
     *
     * If [activity] is provided and the account has never granted Drive consent,
     * [UserRecoverableAuthException] is caught, its consent Intent is launched,
     * and null is returned so the caller can show a "try again" message.
     *
     * If [activity] is null (e.g. silent session restore), the exception is
     * swallowed and null is returned — the caller falls back to the sign-in screen.
     */
    private suspend fun getTokenForEmail(
        email: String,
        activity: Activity?
    ): String? = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.getToken(context, email, DRIVE_SCOPE)
        } catch (e: UserRecoverableAuthException) {
            // The account exists but Drive consent hasn't been granted yet.
            // Launch the consent screen if we have an Activity to attach to.
            if (activity != null) {
                withContext(Dispatchers.Main) {
                    try {
                        activity.startActivity(e.intent)
                    } catch (ignored: Exception) {
                        // Intent couldn't be launched (rare) — fall through to null
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Force-refreshes the access token by:
     *  1. Fetching the currently-cached token (so we have the string to invalidate).
     *  2. Calling clearToken(context, token) to evict it.
     *  3. Calling getToken() again to obtain a fresh one from the network.
     *
     * Called after receiving HTTP 401 from the Drive API.
     * No activity needed here — if the token is truly expired (not missing consent)
     * GoogleAuthUtil will refresh it silently.
     */
    suspend fun refreshAccessToken(email: String): String? =
        withContext(Dispatchers.IO) {
            try {
                // Step 1: get whatever is cached (may be expired)
                val cachedToken = try {
                    GoogleAuthUtil.getToken(context, email, DRIVE_SCOPE)
                } catch (_: Exception) { null }

                // Step 2: invalidate it
                if (!cachedToken.isNullOrBlank()) {
                    try { GoogleAuthUtil.clearToken(context, cachedToken) } catch (_: Exception) {}
                }

                // Step 3: fetch a fresh token from the network
                GoogleAuthUtil.getToken(context, email, DRIVE_SCOPE)
            } catch (e: UserRecoverableAuthException) {
                // Consent was revoked — caller should prompt the user to sign in again
                null
            } catch (e: Exception) {
                null
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

    // ── Retrieve the saved email (needed for token refresh from ViewModel) ─

    suspend fun getSavedEmail(): String? {
        return try {
            context.sessionDataStore.data.first()[KEY_EMAIL]
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}
        clearSession()
    }
}