package com.aviator.predictor.data.repository

import android.content.Context
import com.aviator.predictor.data.models.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Thrown when the Drive API responds with HTTP 401 (token expired / revoked).
 * The ViewModel catches this, force-refreshes the token, and retries once.
 */
class DriveAuthException : Exception("Drive access token expired — refresh required")

class DriveRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30,    java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30,   java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val SIGNALS_FILE  = "aviator_signals.json"
    private val PROFILE_FILE  = "aviator_profile.json"
    private val SETTINGS_FILE = "aviator_settings.json"

    // ── File ID cache (in-memory, reset on sign-out) ──────────────────────

    private var signalsFileId:  String? = null
    private var profileFileId:  String? = null
    private var settingsFileId: String? = null

    // ── Signals ───────────────────────────────────────────────────────────

    suspend fun loadSignals(token: String): List<Signal> = withContext(Dispatchers.IO) {
        val fileId  = findOrCreateFileId(token, SIGNALS_FILE)
        val content = readFile(token, fileId)           // throws DriveAuthException on 401
        if (content.isBlank() || content == "null") return@withContext emptyList()
        gson.fromJson(content, Array<Signal>::class.java)?.toList() ?: emptyList()
    }

    suspend fun saveSignals(token: String, signals: List<Signal>): Boolean =
        withContext(Dispatchers.IO) {
            val fileId = findOrCreateFileId(token, SIGNALS_FILE)
            writeFile(token, fileId, gson.toJson(signals))  // throws on 401
        }

    suspend fun addSignal(token: String, signal: Signal): Boolean = withContext(Dispatchers.IO) {
        val current = loadSignals(token).toMutableList()
        current.add(0, signal)
        saveSignals(token, current)
    }

    suspend fun updateSignalStatus(
        token: String,
        signalId: String,
        status: SignalStatus
    ): Boolean = withContext(Dispatchers.IO) {
        val current = loadSignals(token).toMutableList()
        val index   = current.indexOfFirst { it.id == signalId }
        if (index == -1) return@withContext false
        current[index] = current[index].copy(
            status    = status,
            updatedAt = System.currentTimeMillis()
        )
        saveSignals(token, current)
    }

    suspend fun deleteSignal(token: String, signalId: String): Boolean =
        withContext(Dispatchers.IO) {
            val current = loadSignals(token).filter { it.id != signalId }
            saveSignals(token, current)
        }

    suspend fun batchUpdateStatus(
        token: String,
        updates: Map<String, SignalStatus>
    ): Boolean = withContext(Dispatchers.IO) {
        val current = loadSignals(token).toMutableList()
        val now     = System.currentTimeMillis()
        for (i in current.indices) {
            val newStatus = updates[current[i].id]
            if (newStatus != null)
                current[i] = current[i].copy(status = newStatus, updatedAt = now)
        }
        saveSignals(token, current)
    }

    // ── Profile ───────────────────────────────────────────────────────────

    suspend fun loadProfile(token: String): UserProfile? = withContext(Dispatchers.IO) {
        val fileId  = findOrCreateFileId(token, PROFILE_FILE)
        val content = readFile(token, fileId)
        if (content.isBlank() || content == "null") return@withContext null
        gson.fromJson(content, UserProfile::class.java)
    }

    suspend fun saveProfile(token: String, profile: UserProfile): Boolean =
        withContext(Dispatchers.IO) {
            val fileId = findOrCreateFileId(token, PROFILE_FILE)
            writeFile(token, fileId, gson.toJson(profile))
        }

    // ── Settings ──────────────────────────────────────────────────────────

    suspend fun loadSettings(token: String): AppSettings = withContext(Dispatchers.IO) {
        val fileId  = findOrCreateFileId(token, SETTINGS_FILE)
        val content = readFile(token, fileId)
        if (content.isBlank() || content == "null") return@withContext AppSettings()
        gson.fromJson(content, AppSettings::class.java) ?: AppSettings()
    }

    suspend fun saveSettings(token: String, settings: AppSettings): Boolean =
        withContext(Dispatchers.IO) {
            val fileId = findOrCreateFileId(token, SETTINGS_FILE)
            writeFile(token, fileId, gson.toJson(settings))
        }

    // ── Drive API helpers ─────────────────────────────────────────────────

    private suspend fun findOrCreateFileId(token: String, fileName: String): String =
        withContext(Dispatchers.IO) {
            val cached = when (fileName) {
                SIGNALS_FILE  -> signalsFileId
                PROFILE_FILE  -> profileFileId
                SETTINGS_FILE -> settingsFileId
                else          -> null
            }
            if (cached != null) return@withContext cached

            // Search for existing file in appDataFolder
            val searchUrl = "https://www.googleapis.com/drive/v3/files" +
                "?spaces=appDataFolder&q=name='$fileName'&fields=files(id,name)"

            val searchResp = client.newCall(
                Request.Builder()
                    .url(searchUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            ).execute()

            if (searchResp.code == 401) throw DriveAuthException()

            val searchBody  = searchResp.body?.string() ?: "{}"
            val idRegex     = Regex(""""id"\s*:\s*"([^"]+)"""")
            val existingId  = idRegex.find(searchBody)?.groupValues?.get(1)

            if (existingId != null) {
                cacheFileId(fileName, existingId)
                return@withContext existingId
            }

            // Create new file
            val createBody = """{"name":"$fileName","parents":["appDataFolder"]}"""
            val createResp = client.newCall(
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(createBody.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()

            if (createResp.code == 401) throw DriveAuthException()

            val newId = idRegex.find(createResp.body?.string() ?: "{}")?.groupValues?.get(1)
                ?: throw Exception("Failed to create Drive file: $fileName")

            writeFile(token, newId, "[]")
            cacheFileId(fileName, newId)
            newId
        }

    private fun cacheFileId(fileName: String, id: String) {
        when (fileName) {
            SIGNALS_FILE  -> signalsFileId  = id
            PROFILE_FILE  -> profileFileId  = id
            SETTINGS_FILE -> settingsFileId = id
        }
    }

    /**
     * Read a Drive file's content.
     * @throws DriveAuthException on HTTP 401 so the caller can refresh the token.
     * @throws Exception on other HTTP errors or network failures.
     */
    private fun readFile(token: String, fileId: String): String {
        val resp = client.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .addHeader("Authorization", "Bearer $token")
                .build()
        ).execute()

        if (resp.code == 401) throw DriveAuthException()
        if (!resp.isSuccessful) throw Exception("Drive read failed: HTTP ${resp.code}")
        return resp.body?.string() ?: ""
    }

    /**
     * Write content to a Drive file.
     * @throws DriveAuthException on HTTP 401.
     * @throws Exception on other failures.
     */
    private fun writeFile(token: String, fileId: String, content: String): Boolean {
        val resp = client.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .addHeader("Authorization", "Bearer $token")
                .patch(content.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        if (resp.code == 401) throw DriveAuthException()
        if (!resp.isSuccessful) throw Exception("Drive write failed: HTTP ${resp.code}")
        return true
    }

    /**
     * Drop the in-memory file-ID cache.
     * Call on sign-out or after a token refresh so the next operation
     * re-validates file IDs with the new token.
     */
    fun clearCache() {
        signalsFileId  = null
        profileFileId  = null
        settingsFileId = null
    }

    fun newSignalId(): String = UUID.randomUUID().toString()
}
