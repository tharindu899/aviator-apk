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

class DriveRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().create()
    private val client = OkHttpClient()

    private val SIGNALS_FILE = "aviator_signals.json"
    private val PROFILE_FILE = "aviator_profile.json"
    private val SETTINGS_FILE = "aviator_settings.json"

    // ── File ID cache (in-memory, reset on fresh sign-in) ─────────────────

    private var signalsFileId: String? = null
    private var profileFileId: String? = null
    private var settingsFileId: String? = null

    // ── Signals ───────────────────────────────────────────────────────────

    suspend fun loadSignals(token: String): List<Signal> = withContext(Dispatchers.IO) {
        try {
            val fileId = findOrCreateFileId(token, SIGNALS_FILE)
            val content = readFile(token, fileId)
            if (content.isBlank() || content == "null") return@withContext emptyList()
            gson.fromJson(content, Array<Signal>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveSignals(token: String, signals: List<Signal>): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileId = findOrCreateFileId(token, SIGNALS_FILE)
            writeFile(token, fileId, gson.toJson(signals))
        } catch (e: Exception) {
            false
        }
    }

    suspend fun addSignal(token: String, signal: Signal): Boolean = withContext(Dispatchers.IO) {
        val current = loadSignals(token).toMutableList()
        current.add(0, signal)
        saveSignals(token, current)
    }

    suspend fun updateSignalStatus(token: String, signalId: String, status: SignalStatus): Boolean =
        withContext(Dispatchers.IO) {
            val current = loadSignals(token).toMutableList()
            val index = current.indexOfFirst { it.id == signalId }
            if (index == -1) return@withContext false
            current[index] = current[index].copy(status = status, updatedAt = System.currentTimeMillis())
            saveSignals(token, current)
        }

    suspend fun deleteSignal(token: String, signalId: String): Boolean = withContext(Dispatchers.IO) {
        val current = loadSignals(token).filter { it.id != signalId }
        saveSignals(token, current)
    }

    suspend fun batchUpdateStatus(token: String, updates: Map<String, SignalStatus>): Boolean =
        withContext(Dispatchers.IO) {
            val current = loadSignals(token).toMutableList()
            val now = System.currentTimeMillis()
            for (i in current.indices) {
                val newStatus = updates[current[i].id]
                if (newStatus != null) {
                    current[i] = current[i].copy(status = newStatus, updatedAt = now)
                }
            }
            saveSignals(token, current)
        }

    // ── Profile ───────────────────────────────────────────────────────────

    suspend fun loadProfile(token: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val fileId = findOrCreateFileId(token, PROFILE_FILE)
            val content = readFile(token, fileId)
            if (content.isBlank() || content == "null") return@withContext null
            gson.fromJson(content, UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveProfile(token: String, profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileId = findOrCreateFileId(token, PROFILE_FILE)
            writeFile(token, fileId, gson.toJson(profile))
        } catch (e: Exception) {
            false
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────

    suspend fun loadSettings(token: String): AppSettings = withContext(Dispatchers.IO) {
        try {
            val fileId = findOrCreateFileId(token, SETTINGS_FILE)
            val content = readFile(token, fileId)
            if (content.isBlank() || content == "null") return@withContext AppSettings()
            gson.fromJson(content, AppSettings::class.java) ?: AppSettings()
        } catch (e: Exception) {
            AppSettings()
        }
    }

    suspend fun saveSettings(token: String, settings: AppSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileId = findOrCreateFileId(token, SETTINGS_FILE)
            writeFile(token, fileId, gson.toJson(settings))
        } catch (e: Exception) {
            false
        }
    }

    // ── Drive API helpers ─────────────────────────────────────────────────

    private suspend fun findOrCreateFileId(token: String, fileName: String): String =
        withContext(Dispatchers.IO) {
            val cached = when (fileName) {
                SIGNALS_FILE -> signalsFileId
                PROFILE_FILE -> profileFileId
                SETTINGS_FILE -> settingsFileId
                else -> null
            }
            if (cached != null) return@withContext cached

            // Search for existing file in appDataFolder
            val searchUrl = "https://www.googleapis.com/drive/v3/files" +
                "?spaces=appDataFolder" +
                "&q=name='$fileName'" +
                "&fields=files(id,name)"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val searchResp = client.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string() ?: "{}"

            // Parse fileId from response
            val idRegex = Regex(""""id"\s*:\s*"([^"]+)"""")
            val existingId = idRegex.find(searchBody)?.groupValues?.get(1)

            if (existingId != null) {
                cacheFileId(fileName, existingId)
                return@withContext existingId
            }

            // Create new file
            val createBody = """{"name":"$fileName","parents":["appDataFolder"]}"""
            val createReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(createBody.toRequestBody("application/json".toMediaType()))
                .build()

            val createResp = client.newCall(createReq).execute()
            val createBody2 = createResp.body?.string() ?: "{}"
            val newId = idRegex.find(createBody2)?.groupValues?.get(1)
                ?: throw Exception("Failed to create Drive file: $fileName")

            // Write empty content
            writeFile(token, newId, "[]")
            cacheFileId(fileName, newId)
            newId
        }

    private fun cacheFileId(fileName: String, id: String) {
        when (fileName) {
            SIGNALS_FILE -> signalsFileId = id
            PROFILE_FILE -> profileFileId = id
            SETTINGS_FILE -> settingsFileId = id
        }
    }

    private fun readFile(token: String, fileId: String): String {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = client.newCall(req).execute()
        return resp.body?.string() ?: ""
    }

    private fun writeFile(token: String, fileId: String, content: String): Boolean {
        val body = content.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .addHeader("Authorization", "Bearer $token")
            .patch(body)
            .build()
        val resp = client.newCall(req).execute()
        return resp.isSuccessful
    }

    fun clearCache() {
        signalsFileId = null
        profileFileId = null
        settingsFileId = null
    }

    // ── New signal ID generator ───────────────────────────────────────────
    fun newSignalId(): String = UUID.randomUUID().toString()
}
