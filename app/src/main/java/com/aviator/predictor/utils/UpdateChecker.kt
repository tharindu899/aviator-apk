package com.aviator.predictor.utils

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson

// ── Configure these two constants for your repo ───────────────────────────────
private const val GITHUB_OWNER = "tharindu899"
private const val GITHUB_REPO  = "aviator-apk"
// ─────────────────────────────────────────────────────────────────────────────

data class UpdateInfo(
    val latestVersion: String,   // e.g. "v2.1.0"
    val currentVersion: String,  // e.g. "2.0.0"
    val downloadUrl: String,     // direct APK URL from GitHub release assets
    val releaseNotes: String,
    val apkFileName: String
)

// Internal Gson models for the GitHub API response
private data class GitHubRelease(
    @SerializedName("tag_name")  val tagName: String = "",
    @SerializedName("name")      val name: String = "",
    @SerializedName("body")      val body: String = "",
    @SerializedName("assets")    val assets: List<GitHubAsset> = emptyList(),
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("draft")     val draft: Boolean = false
)

private data class GitHubAsset(
    @SerializedName("name")                 val name: String = "",
    @SerializedName("browser_download_url") val browserDownloadUrl: String = ""
)

object UpdateChecker {

    private val API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val client = OkHttpClient()
    private val gson   = Gson()

    /**
     * Returns [UpdateInfo] if a newer version is available on GitHub,
     * or null if already up-to-date or the check fails.
     */
    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body    = response.body?.string() ?: return@withContext null
                val release = gson.fromJson(body, GitHubRelease::class.java)

                // Skip pre-releases and drafts
                if (release.prerelease || release.draft) return@withContext null

                val latestVersion = release.tagName   // e.g. "v2.1.0"

                if (!isNewer(latestVersion, currentVersionName)) return@withContext null

                // Find the APK asset
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext null

                UpdateInfo(
                    latestVersion  = latestVersion,
                    currentVersion = currentVersionName,
                    downloadUrl    = apkAsset.browserDownloadUrl,
                    releaseNotes   = release.body.take(800).ifBlank { "Bug fixes and improvements." },
                    apkFileName    = apkAsset.name
                )
            } catch (e: Exception) {
                null   // silently fail — no internet, API limit, etc.
            }
        }

    /**
     * Returns true if [latestTag] (e.g. "v2.1.0") is newer than
     * [currentName] (e.g. "2.0.0").
     */
    private fun isNewer(latestTag: String, currentName: String): Boolean {
        val latest  = latestTag.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
        val current = currentName.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }

        val maxLen  = maxOf(latest.size, current.size)
        for (i in 0 until maxLen) {
            val l = latest.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false   // same version
    }
}
