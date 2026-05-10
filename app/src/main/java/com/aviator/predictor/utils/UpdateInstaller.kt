package com.aviator.predictor.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadProgress(
    val percent: Int        = 0,
    val isComplete: Boolean = false,
    val isFailed: Boolean   = false,
    val localFile: File?    = null
)

object UpdateInstaller {

    /**
     * Enqueues the APK download via [DownloadManager].
     * Saves to the PUBLIC Downloads folder so FileProvider can serve it.
     * Returns the download ID, or -1L on failure.
     */
    fun startDownload(context: Context, downloadUrl: String, fileName: String): Long {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Aviator Predictor Update")
                setDescription("Downloading $fileName…")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                // ✅ Use PUBLIC Downloads dir — matches <external-path> in file_provider_paths.xml
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            dm.enqueue(request)
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Polls [DownloadManager] and emits [DownloadProgress] until the download
     * finishes or fails. Call inside a coroutine / flow.
     */
    suspend fun pollProgress(
        context: Context,
        downloadId: Long,
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        while (true) {
            val query  = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)

            if (cursor == null || !cursor.moveToFirst()) {
                onProgress(DownloadProgress(isFailed = true))
                break
            }

            val statusCol     = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val totalCol      = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val localUriCol   = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            val status     = cursor.getInt(statusCol)
            val total      = cursor.getLong(totalCol)
            val downloaded = cursor.getLong(downloadedCol)
            val localUri   = cursor.getString(localUriCol)
            cursor.close()

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    // ✅ Resolve the file from the public Downloads directory
                    val file = resolveDownloadedFile(localUri, fileName)
                    onProgress(DownloadProgress(percent = 100, isComplete = true, localFile = file))
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    onProgress(DownloadProgress(isFailed = true))
                    break
                }
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_PENDING -> {
                    onProgress(DownloadProgress(percent = 0))
                }
                DownloadManager.STATUS_RUNNING -> {
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    onProgress(DownloadProgress(percent = percent))
                }
            }

            delay(600)
        }
    }

    /**
     * Resolves the downloaded APK [File] from a local URI string.
     * Falls back to the known public Downloads path if parsing fails.
     */
    private fun resolveDownloadedFile(localUri: String?, fileName: String): File? {
        if (localUri != null) {
            try {
                val path = Uri.parse(localUri).path
                if (path != null) {
                    val f = File(path)
                    if (f.exists()) return f
                }
            } catch (_: Exception) {}
        }
        // Fallback: reconstruct from known destination
        val fallback = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        return if (fallback.exists()) fallback else null
    }

    /**
     * Cancels an in-progress download.
     */
    fun cancelDownload(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
    }

    /**
     * Checks whether this app is allowed to install unknown packages (Android 8+).
     */
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens the system "Install unknown apps" settings page for this app.
     * Call this when [canInstall] returns false.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data  = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the system package installer for the downloaded APK file.
     * Uses [FileProvider] for Android 7+ compatibility.
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Finds the already-downloaded APK in the PUBLIC Downloads dir if it exists.
     */
    fun findDownloadedApk(context: Context, fileName: String): File? {
        // ✅ Match the public Downloads dir used in startDownload()
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        return if (file.exists()) file else null
    }

    /**
     * Deletes old update APKs from the public Downloads dir to free space.
     */
    fun cleanOldApks(context: Context) {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { f -> f.name.startsWith("AviatorPredictor") && f.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }
}
