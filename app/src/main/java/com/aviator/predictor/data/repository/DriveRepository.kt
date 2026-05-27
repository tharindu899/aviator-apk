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

    // ── SharedPreferences (local cache + persisted file IDs) ──────────────

    private val PREFS_NAME            = "aviator_local_cache"
    private val CACHE_SIGNALS_KEY     = "signals_json"
    private val CACHE_SIGNALS_FILE_ID = "drive_file_id_signals"
    private val CACHE_PROFILE_FILE_ID = "drive_file_id_profile"
    private val CACHE_SETTINGS_FILE_ID= "drive_file_id_settings"

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── In-memory file ID cache (cleared on sign-out / token refresh) ─────
    //   Populated first from disk (instant, no network), then confirmed by Drive.

    private var signalsFileId:  String? = prefs.getString(CACHE_SIGNALS_FILE_ID,  null)
    private var profileFileId:  String? = prefs.getString(CACHE_PROFILE_FILE_ID,  null)
    private var settingsFileId: String? = prefs.getString(CACHE_SETTINGS_FILE_ID, null)

    // ── Local signal cache ────────────────────────────────────────────────

    /** Read signals from disk — instant, no network. */
    fun loadCachedSignals(): List<Signal> = try {
        val json = prefs.getString(CACHE_SIGNALS_KEY, null) ?: return emptyList()
        gson.fromJson(json, Array<Signal>::class.java)?.toList() ?: emptyList()
    } catch (_: Exception) { emptyList() }

    /** Persist signals locally; called every time signals change. */
    fun cacheSignals(signals: List<Signal>) {
        try { prefs.edit().putString(CACHE_SIGNALS_KEY, gson.toJson(signals)).apply() }
        catch (_: Exception) { /* best-effort */ }
    }

    fun clearLocalCache() {
        prefs.edit().clear().apply()
        signalsFileId  = null
        profileFileId  = null
        settingsFileId = null
    }

    // ── Signals ───────────────────────────────────────────────────────────

    /** Load from Drive and update local cache. */
    suspend fun loadSignals(token: String): List<Signal> = withContext(Dispatchers.IO) {
        val fileId  = findOrCreateFileId(token, SIGNALS_FILE)
        val content = readFile(token, fileId)
        if (content.isBlank() || content == "null") return@withContext emptyList()
        val signals = gson.fromJson(content, Array<Signal>::class.java)?.toList() ?: emptyList()
        cacheSignals(signals)
        signals
    }

    /**
     * Write [signals] directly to Drive — NO pre-read.
     * The caller (ViewModel) already owns the authoritative in-memory list.
     */
    suspend fun saveSignals(token: String, signals: List<Signal>): Boolean =
        withContext(Dispatchers.IO) {
            val fileId = findOrCreateFileId(token, SIGNALS_FILE)
            val ok = writeFile(token, fileId, gson.toJson(signals))
            if (ok) cacheSignals(signals)   // keep local cache in sync
            ok
        }

    /**
     * Add a new signal.
     * [currentSignals] — the ViewModel's in-memory list; no Drive read needed.
     */
    suspend fun addSignal(
        token: String,
        signal: Signal,
        currentSignals: List<Signal>
    ): Boolean = withContext(Dispatchers.IO) {
        val updated = listOf(signal) + currentSignals
        saveSignals(token, updated)
    }

    /**
     * Update a single signal's status.
     * [currentSignals] — the ViewModel's in-memory list; no Drive read needed.
     */
    suspend fun updateSignalStatus(
        token: String,
        signalId: String,
        status: SignalStatus,
        currentSignals: List<Signal>
    ): Boolean = withContext(Dispatchers.IO) {
        val now     = System.currentTimeMillis()
        val updated = currentSignals.map { s ->
            if (s.id == signalId) s.copy(status = status, updatedAt = now) else s
        }
        saveSignals(token, updated)
    }

    /**
     * Delete a signal.
     * [currentSignals] — the ViewModel's in-memory list; no Drive read needed.
     */
    suspend fun deleteSignal(
        token: String,
        signalId: String,
        currentSignals: List<Signal>
    ): Boolean = withContext(Dispatchers.IO) {
        saveSignals(token, currentSignals.filter { it.id != signalId })
    }

    /**
     * Batch-update statuses (auto-mark missed).
     * [currentSignals] — the ViewModel's in-memory list; no Drive read needed.
     */
    suspend fun batchUpdateStatus(
        token: String,
        updates: Map<String, SignalStatus>,
        currentSignals: List<Signal>
    ): Boolean = withContext(Dispatchers.IO) {
        val now     = System.currentTimeMillis()
        val updated = currentSignals.map { s ->
            val newStatus = updates[s.id]
            if (newStatus != null) s.copy(status = newStatus, updatedAt = now) else s
        }
        saveSignals(token, updated)
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
            // 1. In-memory hit — zero network calls
            val mem = when (fileName) {
                SIGNALS_FILE  -> signalsFileId
                PROFILE_FILE  -> profileFileId
                SETTINGS_FILE -> settingsFileId
                else          -> null
            }
            if (mem != null) return@withContext mem

            // 2. Search Drive
            val searchUrl = "https://www.googleapis.com/drive/v3/files" +
                "?spaces=appDataFolder&q=name='$fileName'&fields=files(id,name)"

            val searchResp = client.newCall(
                Request.Builder()
                    .url(searchUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            ).execute()

            if (searchResp.code == 401) throw DriveAuthException()

            val searchBody = searchResp.body?.string() ?: "{}"
            val idRegex    = Regex(""""id"\s*:\s*"([^"]+)"""")
            val existingId = idRegex.find(searchBody)?.groupValues?.get(1)

            if (existingId != null) {
                persistFileId(fileName, existingId)
                return@withContext existingId
            }

            // 3. Create new file
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
            persistFileId(fileName, newId)
            newId
        }

    /** Save file ID both in-memory and on disk so it survives app restarts. */
    private fun persistFileId(fileName: String, id: String) {
        when (fileName) {
            SIGNALS_FILE  -> { signalsFileId  = id; prefs.edit().putString(CACHE_SIGNALS_FILE_ID,  id).apply() }
            PROFILE_FILE  -> { profileFileId  = id; prefs.edit().putString(CACHE_PROFILE_FILE_ID,  id).apply() }
            SETTINGS_FILE -> { settingsFileId = id; prefs.edit().putString(CACHE_SETTINGS_FILE_ID, id).apply() }
        }
    }

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
     * Drop all caches.
     * Call on sign-out so the next user starts fresh.
     */
    fun clearCache() = clearLocalCache()

    fun newSignalId(): String = UUID.randomUUID().toString()
}
